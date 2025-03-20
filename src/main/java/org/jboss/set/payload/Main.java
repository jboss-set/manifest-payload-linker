package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.codehaus.mojo.versions.ordering.MavenVersionComparator;
import org.codehaus.mojo.versions.ordering.VersionComparator;
import org.commonjava.atlas.maven.ident.ref.SimpleArtifactRef;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;
import org.jboss.pnc.client.BuildClient;
import org.jboss.pnc.client.Configuration;
import org.jboss.pnc.client.RemoteCollection;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.dto.Artifact;
import org.jboss.set.payload.jira.RetryingJiraIssueClient;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main implements Closeable, Runnable {

    /**
     * Pattern to extract PNC build ID from a URL posted in a text. If you change this, update the group number bellow.
     */
    private static final Pattern BUILD_URL_PATTERN = Pattern.compile(".*(https://orch\\.psi\\.redhat\\.com/pnc-web/(.*/)?builds/([a-zA-Z0-9]+)).*");
    /**
     * Regexp group number that contains the PNC build ID.
     */
    private static final int BUILD_ID_PATTERN_GROUP = 3;

    private static final VersionComparator VERSION_COMPARATOR = new MavenVersionComparator();

    private interface CONFIG {
        String JIRA_TOKEN = "jira.token";
        String JIRA_URL = "jira.url";
        String JIRA_QUERY = "jira.query";
        String PNC_BUILDS_API_URL = "pnc.builds_api_url";
    }

    private static final Logger logger = Logger.getLogger(Main.class);

    private Map<String, String> manifestStreams;
    private final BuildClient buildClient;
    private final JiraRestClient jiraClient;
    private final RetryingJiraIssueClient issueClient;
    private final Config config;

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

        try {
            this.manifestStreams = loadManifestStreams(manifestPath);
        } catch (IOException e) {
            logger.errorf(e, "Can't load the manifest file");
            System.exit(1);
        }

        // Initialize PNC client
        URI pncBuildsApiUrl = config.getValue(CONFIG.PNC_BUILDS_API_URL, URI.class);
        Configuration configuration = Configuration.builder()
                .host(pncBuildsApiUrl.getHost())
                .port(pncBuildsApiUrl.getPort())
                .protocol(pncBuildsApiUrl.getScheme())
                .build();
        buildClient = new BuildClient(configuration);

        // Initialize Jira client
        URI jiraUri = config.getValue(CONFIG.JIRA_URL, URI.class);
        String jiraToken = config.getValue(CONFIG.JIRA_TOKEN, String.class);
        jiraClient = new AsynchronousJiraRestClientFactory()
                .createWithAuthenticationHandler(jiraUri,
                        builder -> builder.setHeader("Authorization", "Bearer " + jiraToken));
        issueClient = new RetryingJiraIssueClient(jiraClient.getIssueClient());

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
        buildClient.close();
        jiraClient.close();
        for (AbstractReportConsumer consumer: reportConsumers) {
            consumer.close();
        }
    }

    @Override
    public void run() {
        try {
            List<String> issueKeys = loadIssueKeys();
            for (String issueKey : issueKeys) {
                logger.debugf("Processing issue %s", issueKey);
                Issue issue = issueClient.getIssue(issueKey);
                String buildId = findBuildId(issue);
                if (buildId != null) {
                    if (isBuildCoveredByManifest(buildId)) {
                        logger.debugf("Issue %s is covered by given manifest.", issueKey);
                        processReportConsumers(issue);
                    }
                }
            }
        } catch (RemoteResourceException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Determines if the build artifacts from given build are covered by given manifest (the manifest has to contain
     * equal or higher artifact versions).
     *
     * @param buildId PNC build ID
     * @return is given build covered by the manifest?
     */
    private boolean isBuildCoveredByManifest(String buildId) throws RemoteResourceException {
        RemoteCollection<Artifact> artifacts = buildClient.getBuiltArtifacts(buildId);

        boolean artifactsCovered = true;
        boolean anyMatch = false; // At least one build artifact has to be present in the manifest.
        for (Artifact artifact : artifacts) {
            SimpleArtifactRef artifactRef = SimpleArtifactRef.parse(artifact.getIdentifier());
            String key = artifactRef.getGroupId() + ":" + artifactRef.getArtifactId();
            if (manifestStreams.containsKey(key)) {
                anyMatch = true;
                String versionInManifest = manifestStreams.get(key);

                // Here were compare version introduced by the component upgrade against the version present in the
                // manifest. If the version from the manifest is lower, the component upgrade is not covered by this
                // manifest.
                if (compareVersions(artifactRef.getVersionString(), versionInManifest) > 0) {
                    logger.infof("Build artifact %s is not satisfied by manifest stream %s:%s",
                            artifact.getIdentifier(), key, versionInManifest);
                    artifactsCovered = false;
                    break;
                } else {
                    logger.debugf("Build artifact %s is satisfied by manifest stream %s:%s",
                            artifact.getIdentifier(), key, versionInManifest);
                }
            }
        }

        if (!anyMatch) {
            logger.infof("No artifacts for build %s are present in the manifest.", buildId);
        }

        return anyMatch && artifactsCovered;
    }

    /**
     * Discovers available component upgrade Jira issues.
     */
    private List<String> loadIssueKeys() {
        int maxResults = 30;
        int startIndex = 0;

        List<String> keys = new ArrayList<>();
        SearchResult searchResult;

        String jiraQuery = config.getValue(CONFIG.JIRA_QUERY, String.class);

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

    /**
     * Scans through issue comments and tries to find a PNC build ID.
     *
     * @param issue Jira issue
     * @return PNC build ID
     */
    private static String findBuildId(Issue issue) {
        String buildId = null;
        for (Comment comment : issue.getComments()) {
            List<String> lines = comment.getBody().lines().toList();
            for (String line : lines) {
                if (line.toLowerCase().contains("pnc build:")) {
                    Matcher matcher = BUILD_URL_PATTERN.matcher(line);
                    if (matcher.find()) {
                        buildId = matcher.group(BUILD_ID_PATTERN_GROUP);
                        logger.infof("Found PNC build ID %s in %s %s", buildId, issue.getKey(), issue.getSummary());
                    }
                }
            }
        }
        return buildId;
    }

    /**
     * Loads manifest streams into a map.
     */
    private static Map<String, String> loadManifestStreams(Path manifestPath) throws IOException {
        Map<String, String> manifestStreams = new HashMap<>();
        ChannelManifest manifest = ChannelManifestMapper.fromString(Files.readString(manifestPath));
        manifest.getStreams().forEach(s -> {
            if (s.getVersion() != null) {
                manifestStreams.put(s.getGroupId() + ":" + s.getArtifactId(), s.getVersion());
            } else {
                logger.warnf("Manifest stream %s:%s doesn't have a fixed version, can't be used for comparison.",
                        s.getGroupId(), s.getArtifactId());
            }
        });
        return manifestStreams;
    }

    /**
     * Compares two maven version strings.
     *
     * @return negative number, zero, or positive number if the first version is lower, equal or higher than the second version respectively.
     */
    private static int compareVersions(String first, String second) {
        return VERSION_COMPARATOR.compare(new DefaultArtifactVersion(first.trim()), new DefaultArtifactVersion(second.trim()));
    }

}