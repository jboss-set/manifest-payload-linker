package org.jboss.set.payload.manifest;

import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;
import org.jboss.set.payload.ConfigKeys;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.version.VersionMatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ManifestChecker {

    public record ComponentQuery(String issueKey, Map<String, String> upgradedArtifacts) {
    }

    private static final Logger logger = Logger.getLogger(ManifestChecker.class);
    static final Comparator<String> VERSION_COMPARATOR = VersionMatcher.COMPARATOR;

    private final Map<String, String> manifestStreams;
    private final List<String> ignoredGAs = new ArrayList<>();

    /**
     * @param manifestPath path to manifest file
     */
    public ManifestChecker(Path manifestPath, Config config) {
        this(loadManifestStreams(manifestPath), config);
    }

    /**
     * @param manifestStreams map of "groupId:artifactId" -> "artifact version"
     */
    public ManifestChecker(Map<String, String> manifestStreams, Config config) {
        this.manifestStreams = manifestStreams;
        if (config != null) {
            Optional<String> ignoreArtifactsOpt = config.getOptionalValue(ConfigKeys.IGNORE_ARTIFACTS, String.class);
            ignoreArtifactsOpt.ifPresent(str -> Collections.addAll(ignoredGAs, str.split(",")));
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
                // It is also being checked if the versions have the same major.minor segments. If the major.minor
                // segments are different, it likely means the components being compared do not belong to the same
                // EE product variant.
                if (ignoredGAs.contains(ga)) {
                    logger.debugf("%s: Build artifact %s:%s is ignored via global configuration",
                            componentQuery.issueKey, ga, upgradedVersion);
                } else if (!isSameMinor(upgradedVersion, versionInManifest) || compareVersions(upgradedVersion, versionInManifest) > 0) {
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
    static int compareVersions(String first, String second) {
        return VERSION_COMPARATOR.compare(first.trim(), second.trim());
    }

    /**
     * Checks that the two version strings have identical "major.minor" segments.
     */
    static boolean isSameMinor(String first, String second) {
        String[] firstSegments = first.split("\\.", 3);
        String[] secondSegments = second.split("\\.", 3);

        if (firstSegments.length < 2 || secondSegments.length < 2) {
            return false;
        }

        for (int i = 0; i < 2; i++) {
            if (!firstSegments[i].equals(secondSegments[i])) {
                return false;
            }
        }

        return true;
    }

    /**
     * Loads manifest streams into a map.
     */
    private static Map<String, String> loadManifestStreams(Path manifestPath) {
        try {
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
