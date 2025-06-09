package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;
import org.jboss.set.payload.dependencygroups.StaticDependencyGroupsResolutionStrategy;
import org.jboss.set.payload.jira.FaultTolerantIssueClient;
import org.jboss.set.payload.manifest.ManifestChecker;
import org.jboss.set.payload.pnc.PncResolutionStrategy;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Main implements Closeable, Runnable {

    private static final Logger logger = Logger.getLogger(Main.class);

    private final JiraRestClient jiraClient;
    private final FaultTolerantIssueClient issueClient;
    private final Config config;

    private final List<ComponentUpgradeResolutionStrategy> resolutionStrategies = new ArrayList<>();
    private final List<IssueConsumer> verifiedIssuesConsumers = new ArrayList<>();
    private final IssueConsumer toCheckManually;
    private final URI jiraUri;

    public static void main(String[] args) throws Exception {
        if (args.length != 4 && args.length != 5) {
            logger.error("Incorrect number of parameters");
            usage();
            System.exit(1);
        }

        try (Main main = new Main(Path.of(args[0]), args[1], args[2], args[3], args[4])) {
            main.run();
        }
    }

    private static void usage() {
        System.err.println("Usage:");
        System.err.println("  java -jar <path-to-jar-file> <path-to-manifest-file> <manifest-reference> <targetRelease> <fix-versions> [<layered-fix-versions>]");
        System.err.println();
        System.err.println("  <manifest-reference>\tcan be a Maven GAV, git hash, etc.");
        System.err.println("  <target-release>\ttarget release of the component upgrades.");
        System.err.println("  <fix-versions>\tcomma-delimited list of Jira fix versions to set to issues.");
        System.err.println("  <layered-fix-versions>\tcomma-delimited list of Jira fix versions for layered product (e.g. XP).");
    }

    public Main(Path manifestPath, String manifestReference, String targetRelease, String fixVersionsString,
                String layeredFixVersionsString)
            throws IOException {
        config = new SmallRyeConfigBuilder()
                .addDefaultSources()
                .build();

        ManifestChecker manifestChecker = new ManifestChecker(manifestPath);

        // Initialize Jira client
        jiraUri = config.getValue(ConfigKeys.JIRA_URL, URI.class);
        String jiraToken = config.getValue(ConfigKeys.JIRA_TOKEN, String.class);
        Long spacing = config.getOptionalValue(ConfigKeys.JIRA_REQUEST_FREQUENCY, Long.class).orElse(0L);
        Boolean disableStaticStrategy = config.getOptionalValue("static_resolution_strategy.disable", Boolean.class).orElse(false);
        Boolean dryMode = config.getOptionalValue(ConfigKeys.JIRA_DRY_MODE, Boolean.class).orElse(true);

        jiraClient = new AsynchronousJiraRestClientFactory()
                .createWithAuthenticationHandler(jiraUri,
                        builder -> builder.setHeader("Authorization", "Bearer " + jiraToken));
        issueClient = new FaultTolerantIssueClient(jiraClient.getIssueClient(), spacing, dryMode);

        resolutionStrategies.add(new DenyUnwantedStatesStrategy());
        resolutionStrategies.add(new PncResolutionStrategy(config, manifestChecker));
        if (!disableStaticStrategy) {
            resolutionStrategies.add(new StaticDependencyGroupsResolutionStrategy(config, manifestChecker));
        }

        Collection<String> fixVersions = parseCommaSeparatedStringList(fixVersionsString);
        Collection<String> layeredFixVersions = parseCommaSeparatedStringList(layeredFixVersionsString);

        verifiedIssuesConsumers.add(new IssueLinksReportConsumer(issueClient, new File("issue-links.txt"), jiraUri, manifestReference));
        verifiedIssuesConsumers.add(new IssueCodesReportConsumer(issueClient, new File("issue-codes.txt"), manifestReference));
        verifiedIssuesConsumers.add(new DetailedReportConsumer(issueClient, new File("detailed-report.txt"), jiraUri, manifestReference));
        verifiedIssuesConsumers.add(new IssueTransitionConsumer(issueClient, manifestReference, targetRelease, fixVersions, layeredFixVersions));

        toCheckManually = new IssueLinksReportConsumer(issueClient, new File("check-manually.txt"),
                AbstractIssueConsumer.INCLUDE_ALL, AbstractIssueConsumer.INCLUDE_NONE, jiraUri, manifestReference);
    }

    @Override
    public void close() throws IOException {
        jiraClient.close();
        for (IssueConsumer consumer: verifiedIssuesConsumers) {
            consumer.close();
        }
        toCheckManually.close();
    }

    @Override
    public void run() {
        List<String> issueKeys = loadIssueKeys();
        for (String issueKey : issueKeys) {
            logger.debugf("Retrieving issue %s", issueKey);
            Issue issue = issueClient.getIssue(issueKey);
            URI issueUri = jiraUri.resolve("browse/").resolve(issueKey);
            logger.infof("Processing issue %s [%s]: %s", issueUri.toString(), issue.getStatus().getName(),
                    issue.getSummary());

            Boolean result = null;
            for (ComponentUpgradeResolutionStrategy strategy: resolutionStrategies) {
                result = strategy.apply(issue);
                if (result != null) break;
            }

            if (result == null) {
                logger.warnf("%s: Unable to determine if issue is covered by the manifest.", issueKey);
                toCheckManually.accept(issue);
            } else if (result) {
                logger.infof("%s: Issue is covered by the manifest.", issueKey);
                processReportConsumers(issue);
            } else {
                logger.infof("%s: Issue is not covered by the manifest.", issueKey);
            }
        }
    }

    /**
     * Discovers available component upgrade Jira issues.
     */
    private List<String> loadIssueKeys() {
        int maxResults = 30;
        int startIndex = 0;

        List<String> keys = new ArrayList<>();
        SearchResult searchResult;

        String jiraQuery = config.getValue(ConfigKeys.JIRA_QUERY, String.class);

        do {
            searchResult = jiraClient.getSearchClient()
                    .searchJql(jiraQuery, maxResults, startIndex, Collections.emptySet()).claim();
            searchResult.getIssues().iterator().forEachRemaining(issue -> keys.add(issue.getKey()));
            startIndex += maxResults;
        } while (searchResult.getTotal() > startIndex);

        logger.infof("Found %s component upgrade issues.", searchResult.getTotal());

        return keys;
    }

    private void processReportConsumers(Issue issue) {
        for (IssueConsumer consumer: verifiedIssuesConsumers) {
            consumer.accept(issue);
        }
    }

    protected static Collection<String> parseCommaSeparatedStringList(String input) {
        if (StringUtils.isBlank(input)) {
            return Collections.emptyList();
        }
        return Arrays.stream(input.trim().split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
    }

}