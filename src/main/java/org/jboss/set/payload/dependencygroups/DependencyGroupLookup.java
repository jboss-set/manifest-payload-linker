package org.jboss.set.payload.dependencygroups;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.IOException;
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
        DependencyGroup dependencyGroup = aliasToGroup.get(componentAlias.toLowerCase());
        if (dependencyGroup != null) {
            return dependencyGroup.getDependencies();
        }
        return null;
    }
}
