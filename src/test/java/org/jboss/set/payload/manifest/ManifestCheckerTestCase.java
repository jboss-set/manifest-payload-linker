package org.jboss.set.payload.manifest;

import io.smallrye.common.constraint.Assert;
import org.junit.Test;

import java.util.HashMap;

public class ManifestCheckerTestCase {

    @Test
    public void testComparator() {
        Assert.assertTrue(ManifestChecker.compareVersions("2.2.1.Final", "2.2.2.Final") < 0);
        Assert.assertTrue(ManifestChecker.compareVersions("2.2.2.Final-redhat-00002", "2.2.2.SP01") < 0);
    }

    @Test
    public void testIsSameMinor() {
        Assert.assertTrue(ManifestChecker.isSameMinor("1.2.3.redhat-00001", "1.2.3.redhat-00001"));
        Assert.assertTrue(ManifestChecker.isSameMinor("1.2.3.redhat-00001", "1.2.4"));
        Assert.assertTrue(ManifestChecker.isSameMinor("1.2.3.redhat-00001", "1.2"));
        Assert.assertFalse(ManifestChecker.isSameMinor("1.2.3.redhat-00001", "1.3"));
        Assert.assertFalse(ManifestChecker.isSameMinor("1.2.3.redhat-00001", "1.3.1"));
    }

    @Test
    public void testManifestChecker() {
        Assert.assertFalse(testManifestChecker("1.2.3", "1.2.4"));
        Assert.assertTrue(testManifestChecker("1.2.4", "1.2.4"));
        Assert.assertTrue(testManifestChecker("1.2.5", "1.2.4"));
        Assert.assertFalse(testManifestChecker("1.3.0", "1.2.4"));
        Assert.assertFalse(testManifestChecker("2.1.0", "1.2.4"));
    }

    private boolean testManifestChecker(String manifestVersion, String ticketVersion) {
        HashMap<String, String> streams = new HashMap<>();
        streams.put("io.undertow:undertow-core", manifestVersion);
        ManifestChecker manifestChecker = new ManifestChecker(streams, null);

        HashMap<String, String> upgradedArtifacts = new HashMap<>();
        upgradedArtifacts.put("io.undertow:undertow-core", ticketVersion);
        upgradedArtifacts.put("io.undertow:undertow-servlet", ticketVersion);

        return manifestChecker.test(new ManifestChecker.ComponentQuery("JBEAP-123", upgradedArtifacts));
    }
}
