package org.jboss.set.payload.dependencygroups;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DependencyGroupLookup {

    private final List<DependencyGroup> groups;
    private final Map<String, DependencyGroup> aliasToGroup = new HashMap<>();

    public DependencyGroupLookup(File dependencyGroupFile) throws IOException {
        YAMLMapper mapper = YAMLMapper.builder().build();
        JavaType type = mapper.getTypeFactory().constructParametricType(List.class, DependencyGroup.class);
        groups = mapper.readValue(dependencyGroupFile, type);
        groups.forEach(group -> {
            aliasToGroup.put(group.getComponent().toLowerCase(), group);
            group.getAliases().forEach(alias -> aliasToGroup.put(alias.toLowerCase(), group));
        });
    }

    public Collection<String> findArtifacts(String componentAlias) {
        // This determines if there's a group with defined name or alias that matches the given componentAlias.
        DependencyGroup dependencyGroup = aliasToGroup.get(componentAlias.toLowerCase());
        if (dependencyGroup != null) {
            return dependencyGroup.getDependencies();
        }

        // As a backup strategy, check if we can identify a group based on artifactId.
        List<DependencyGroup> matchingGroups = new ArrayList<>();
        for (DependencyGroup group: groups) {
            for (String ga: group.getDependencies()) {
                String[] segments = ga.split(":");
                if (segments.length > 1) {
                    String artifactId = segments[1];
                    if (componentAlias.equals(artifactId)) {
                        matchingGroups.add(group);
                    }
                }
            }
        }
        if (matchingGroups.size() == 1) { // Only return if single candidate group was found.
            return matchingGroups.get(0).getDependencies();
        }

        // Null represents Unknown.
        return null;
    }
}
