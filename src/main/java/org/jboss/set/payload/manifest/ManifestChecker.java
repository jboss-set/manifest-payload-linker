package org.jboss.set.payload.manifest;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.codehaus.mojo.versions.ordering.MavenVersionComparator;
import org.codehaus.mojo.versions.ordering.VersionComparator;
import org.jboss.logging.Logger;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ManifestChecker {

    public record ComponentQuery(String issueKey, Map<String, String> upgradedArtifacts) {
    }

    private static final Logger logger = Logger.getLogger(ManifestChecker.class);
    private static final VersionComparator VERSION_COMPARATOR = new MavenVersionComparator();

    private final Map<String, String> manifestStreams;

    /**
     * @param manifestPath path to manifest file
     */
    public ManifestChecker(Path manifestPath) {
        try {
            this.manifestStreams = loadManifestStreams(manifestPath);
        } catch (IOException e) {
            throw new RuntimeException("Can't load the manifest file", e);
        }
    }

    /**
     * Checks if given component upgrade is covered by the manifest.
     *
     * @param componentQuery component query to resolve
     * @return true of all upgrade artifacts are covered by the manifest (version in manifest is equal or higher)
     */
    public Boolean test(ComponentQuery componentQuery) {
        boolean presentInManifest = false; // At least one build artifact has to be present in the manifest.
        for (Map.Entry<String, String> entry : componentQuery.upgradedArtifacts.entrySet()) {
            String ga = entry.getKey();
            String upgradedVersion = entry.getValue();
            if (manifestStreams.containsKey(ga)) {
                presentInManifest = true;
                String versionInManifest = manifestStreams.get(ga);

                // Here were compare version introduced by the component upgrade against the version present in the
                // manifest. If the version from the manifest is lower, the component upgrade is not covered by this
                // manifest.
                if (compareVersions(upgradedVersion, versionInManifest) > 0) {
                    logger.infof("%s: Build artifact %s:%s is not satisfied by manifest stream %s:%s",
                            componentQuery.issueKey, ga, upgradedVersion, ga, versionInManifest);
                    return false;
                } else {
                    logger.debugf("%s: Build artifact %s:%s is satisfied by manifest stream %s:%s",
                            componentQuery.issueKey, ga, upgradedVersion, ga, versionInManifest);
                }
            }
        }

        if (presentInManifest) { // At least one artifact was present in manifest.
            return true;
        }

        logger.infof("%s: No artifacts from this component upgrade are present in the manifest.",
                componentQuery.issueKey);
        return null; // Represents Unknown
    }


    /**
     * Compares two maven version strings.
     *
     * @return negative number, zero, or positive number if the first version is lower, equal or higher than the second version respectively.
     */
    private static int compareVersions(String first, String second) {
        return VERSION_COMPARATOR.compare(new DefaultArtifactVersion(first.trim()), new DefaultArtifactVersion(second.trim()));
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

}
