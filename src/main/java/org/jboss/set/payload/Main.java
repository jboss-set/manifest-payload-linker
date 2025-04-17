package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;
import org.jboss.set.payload.dependencygroups.StaticDependencyGroupsResolutionStrategy;
import org.jboss.set.payload.jira.FaultTolerantIssueClient;
import org.jboss.set.payload.pnc.PncResolutionStrategy;
import org.jboss.set.payload.manifest.ManifestChecker;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class Main implements Closeable, Runnable {

    private static final Logger logger = Logger.getLogger(Main.class);

    private final JiraRestClient jiraClient;
    private final FaultTolerantIssueClient issueClient;
    private final Config config;

    private final List<ComponentUpgradeResolutionStrategy> resolutionStrategies = new ArrayList<>();
    private final List<AbstractReportConsumer> reportConsumers = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            logger.error("Expected single argument: path to a manifest file");
            System.exit(1);
        }

        try (Main main = new Main(Path.of(args[0]))) {
            main.run();
        }
    }

    public Main(Path manifestPath) {
        config = new SmallRyeConfigBuilder()
                .addDefaultSources()
                .build();

        ManifestChecker manifestChecker = new ManifestChecker(manifestPath);

        // Initialize Jira client
        URI jiraUri = config.getValue(ConfigKeys.JIRA_URL, URI.class);
        String jiraToken = config.getValue(ConfigKeys.JIRA_TOKEN, String.class);
        Long spacing = config.getOptionalValue(ConfigKeys.JIRA_REQUEST_FREQUENCY, Long.class).orElse(0L);
        Boolean disableStaticStrategy = config.getOptionalValue("static_resolution_strategy.disable", Boolean.class).orElse(false);

        jiraClient = new AsynchronousJiraRestClientFactory()
                .createWithAuthenticationHandler(jiraUri,
                        builder -> builder.setHeader("Authorization", "Bearer " + jiraToken));
        issueClient = new FaultTolerantIssueClient(jiraClient.getIssueClient(), spacing);

        resolutionStrategies.add(new PncResolutionStrategy(config, manifestChecker));
        if (!disableStaticStrategy) {
            resolutionStrategies.add(new StaticDependencyGroupsResolutionStrategy(config, manifestChecker));
        }

        try {
            reportConsumers.add(new IssueLinksReportConsumer(issueClient, new File("issue-links.txt"), jiraUri));
            reportConsumers.add(new IssueCodesReportConsumer(issueClient, new File("issue-codes.txt")));
            reportConsumers.add(new DetailedReportConsumer(issueClient, new File("detailed-report.txt"), jiraUri));
        } catch (IOException e) {
            logger.errorf(e, "Can't create report file");
            System.exit(1);
        }
    }

    @Override
    public void close() throws IOException {
        jiraClient.close();
        for (AbstractReportConsumer consumer: reportConsumers) {
            consumer.close();
        }
    }

    @Override
    public void run() {
        List<String> issueKeys = loadIssueKeys();
        for (String issueKey : issueKeys) {
            logger.debugf("Processing issue %s", issueKey);
            Issue issue = issueClient.getIssue(issueKey);

            Boolean result = null;
            for (ComponentUpgradeResolutionStrategy strategy: resolutionStrategies) {
                result = strategy.apply(issue);
                if (result != null) break;
            }

            if (result == null) {
                logger.infof("Unable to determine if issue %s is covered by the manifest.", issueKey);
            } else if (result) {
                logger.debugf("Issue %s is covered by the manifest.", issueKey);
                processReportConsumers(issue);
            } else {
                logger.debugf("Issue %s is not covered by the manifest.", issueKey);
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
        for (Consumer<Issue> consumer: reportConsumers) {
            consumer.accept(issue);
        }
    }

}