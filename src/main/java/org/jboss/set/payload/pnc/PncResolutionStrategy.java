package org.jboss.set.payload.pnc;

import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import org.commonjava.atlas.maven.ident.ref.SimpleArtifactRef;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;
import org.jboss.pnc.client.BuildClient;
import org.jboss.pnc.client.Configuration;
import org.jboss.pnc.client.RemoteCollection;
import org.jboss.pnc.client.RemoteResourceException;
import org.jboss.pnc.dto.Artifact;
import org.jboss.set.payload.ComponentUpgradeResolutionStrategy;
import org.jboss.set.payload.ConfigKeys;
import org.jboss.set.payload.manifest.ManifestChecker;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PncResolutionStrategy implements ComponentUpgradeResolutionStrategy {

    /**
     * Pattern to extract PNC build ID from a URL posted in a text. If you change this, update the group number bellow.
     */
    private static final Pattern BUILD_URL_PATTERN = Pattern.compile(".*(https://orch\\.psi\\.redhat\\.com/pnc-web/(.*/)?builds/([a-zA-Z0-9]+)).*");
    /**
     * Regexp group number that contains the PNC build ID.
     */
    private static final int BUILD_ID_PATTERN_GROUP = 3;

    private static final Logger logger = Logger.getLogger(PncResolutionStrategy.class);

    private final BuildClient buildClient;
    private final ManifestChecker manifestChecker;

    public PncResolutionStrategy(Config config, ManifestChecker manifestChecker) {
        this.manifestChecker = manifestChecker;

        // Initialize PNC client
        URI pncBuildsApiUrl = config.getValue(ConfigKeys.PNC_BUILDS_API_URL, URI.class);
        Configuration configuration = Configuration.builder()
                .host(pncBuildsApiUrl.getHost())
                .port(pncBuildsApiUrl.getPort())
                .protocol(pncBuildsApiUrl.getScheme())
                .build();
        buildClient = new BuildClient(configuration);

    }

    @Override
    public Boolean apply(Issue issue) {
        String buildId = findBuildId(issue);
        if (buildId != null) {
            try {
                RemoteCollection<Artifact> artifacts = buildClient.getBuiltArtifacts(buildId);
                Map<String, String> gaToVersionMap = new HashMap<>();
                artifacts.forEach(artifact -> {
                    SimpleArtifactRef artifactRef = SimpleArtifactRef.parse(artifact.getIdentifier());
                    gaToVersionMap.put(artifactRef.getGroupId() + ":" + artifactRef.getArtifactId(), artifactRef.getVersionString());
                });
                return manifestChecker.test(new ManifestChecker.ComponentQuery(issue.getKey(), gaToVersionMap));
            } catch (RemoteResourceException e) {
                logger.error("Can't retrieve build artifacts from PNC API.", e);
            }
        }
        return null; // Represents Unknown
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
                if (line.toLowerCase().contains("https://orch.psi.redhat.com/pnc-web/")) {
                    Matcher matcher = BUILD_URL_PATTERN.matcher(line);
                    if (matcher.find()) {
                        buildId = matcher.group(BUILD_ID_PATTERN_GROUP);
                        logger.infof("%s: Found PNC build ID %s", issue.getKey(), buildId);
                    }
                }
            }
        }
        return buildId;
    }

}
