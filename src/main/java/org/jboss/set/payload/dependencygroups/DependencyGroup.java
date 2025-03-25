package org.jboss.set.payload.dependencygroups;

import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.commonjava.atlas.maven.ident.ref.SimpleArtifactRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectRef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

class DependencyGroup {

    private String component;
    private List<String> aliases = new ArrayList<>();
    private Set<String> dependencies = new TreeSet<>();

    public DependencyGroup() {
    }

    public DependencyGroup(String component) {
        this.component = component;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public Set<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(Set<String> dependencies) {
        this.dependencies = dependencies.stream()
                .map(SimpleProjectRef::parse)
                .map(r -> r.getGroupId() + ":" + r.getArtifactId())
                .collect(Collectors.toSet());
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases;
    }
}
