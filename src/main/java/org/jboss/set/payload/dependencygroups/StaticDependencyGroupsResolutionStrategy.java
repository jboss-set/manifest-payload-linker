package org.jboss.set.payload.dependencygroups;

import com.atlassian.jira.rest.client.api.domain.Issue;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;
import org.jboss.set.payload.ComponentUpgradeResolutionStrategy;
import org.jboss.set.payload.Main;
import org.jboss.set.payload.llm.ComponentUpgrade;
import org.jboss.set.payload.llm.LlmSummaryExtractor;
import org.jboss.set.payload.manifest.ManifestChecker;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;

public class StaticDependencyGroupsResolutionStrategy implements ComponentUpgradeResolutionStrategy {

    private static final Logger logger = Logger.getLogger(Main.class);

    private final LlmSummaryExtractor summaryExtractor;
    private final DependencyGroupLookup dependencyGroupLookup;
    private final ManifestChecker manifestChecker;

    public StaticDependencyGroupsResolutionStrategy(Config config, ManifestChecker manifestChecker) {
        this.summaryExtractor = new LlmSummaryExtractor(config);
        this.manifestChecker = manifestChecker;
        try {
            this.dependencyGroupLookup = new DependencyGroupLookup(new File("dependency-groups.yaml"));
        } catch (IOException e) {
            throw new RuntimeException("Can't load the dependency group file", e);
        }
    }

    @Override
    public Boolean apply(Issue issue) {
        ComponentUpgrade info = summaryExtractor.extractInfo(issue.getSummary());
        if (info != null && info.isValid()) {
            logger.infof("%s: Component '%s' upgraded to version '%s'",
                    issue.getKey(), info.component(), info.targetVersion());
            Collection<String> componentArtifacts = dependencyGroupLookup.findArtifacts(info.component());
            if (componentArtifacts != null) {
                HashMap<String, String> upgradeArtifacts = new HashMap<>();
                componentArtifacts.forEach(ga -> upgradeArtifacts.put(ga, info.targetVersion()));
                return manifestChecker.test(new ManifestChecker.ComponentQuery(issue.getKey(), upgradeArtifacts));
            } else {
                logger.warnf("%s: Can't identify component %s", issue.getKey(), info.component());
            }
        } else {
            logger.warnf("%s: Can't parse issue summary: %s", issue.getKey(), issue.getSummary());
        }
        return null; // Represents Unknown
    }
}
