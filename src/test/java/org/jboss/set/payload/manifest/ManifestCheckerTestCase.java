package org.jboss.set.payload.manifest;

import io.smallrye.common.constraint.Assert;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.Test;

public class ManifestCheckerTestCase {

    @Test
    public void testComparator() {
        Assert.assertTrue(compareVersions("2.2.1.Final", "2.2.2.Final") < 0);
        Assert.assertTrue(compareVersions("2.2.2.Final-redhat-00002", "2.2.2.SP01") < 0);
    }

    private int compareVersions(String v1, String v2) {
        return ManifestChecker.VERSION_COMPARATOR.compare(v1, v2);
    }
}
