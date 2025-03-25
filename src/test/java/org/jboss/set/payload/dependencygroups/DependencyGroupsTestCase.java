package org.jboss.set.payload.dependencygroups;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class DependencyGroupsTestCase {

    @Test
    public void testGroupsCoverEntireManifest() throws Exception {
        URL manifestResource = getClass().getClassLoader().getResource("manifest.yaml");
        Assert.assertNotNull(manifestResource);
        ChannelManifest manifest = ChannelManifestMapper.from(manifestResource);

        URL groupsResource = getClass().getClassLoader().getResource("dependency-groups.yaml");
        Assert.assertNotNull(groupsResource);
        YAMLMapper mapper = YAMLMapper.builder().build();
        JavaType type = mapper.getTypeFactory().constructParametricType(List.class, DependencyGroup.class);
        List<DependencyGroup> groups = mapper.readValue(groupsResource, type);

        ArrayList<String> notMatching = new ArrayList<>();
        manifest.getStreams().forEach(stream -> {
            String ga = stream.getGroupId() + ":" + stream.getArtifactId();
            boolean match = groups.stream()
                    .flatMap(g -> g.getDependencies().stream())
                    .anyMatch(dep -> dep.startsWith(ga));
            if (!match) {
                notMatching.add(ga);
                System.out.println(ga);
            }
        });

        Assert.assertTrue(notMatching.isEmpty());
    }

}
