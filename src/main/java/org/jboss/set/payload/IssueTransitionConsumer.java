package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.jboss.logging.Logger;
import org.jboss.set.payload.jira.FaultTolerantIssueClient;
import org.jboss.set.payload.jira.JiraConstants;
import org.jboss.util.Strings;

import java.util.Collection;

public class IssueTransitionConsumer extends AbstractIssueConsumer {

    private static final Logger logger = Logger.getLogger(IssueTransitionConsumer.class);

    private static final String COMPONENT_UPGRADE_MESSAGE = "This component upgrade has been incorporated in manifest marked as %s [*\\].\n\n[*\\] %s";
    private static final String INCORPORATED_ISSUE_MESSAGE = "This issue has been incorporated in manifest [*\\] marked as %s via component upgrade %s.\n\n[*\\] %s";
    private static final String LABEL = "on-payload";

    private final String targetRelese;
    private final Collection<String> fixVersions;
    private final Collection<String> layeredFixVersions;


    public IssueTransitionConsumer(FaultTolerantIssueClient issueClient, String manifestReference, String targetRelease,
                                   Collection<String> fixVersions, Collection<String> layeredFixVersions) {
        super(issueClient, INCLUDE_NON_VERIFIED, INCLUDE_RESOLVED, manifestReference);
        this.targetRelese = targetRelease;
        this.fixVersions = fixVersions;
        this.layeredFixVersions = layeredFixVersions;
    }

    @Override
    public void componentUpgradeIssue(Issue issue, String manifestReference) {
        String comment = String.format(COMPONENT_UPGRADE_MESSAGE, fixVersions, manifestReference);
        if (!hasLabel(issue, LABEL)) {
            issueClient.addComment(issue, comment);
            issueClient.updateIssue(issue, fixVersions, LABEL);
            issueClient.transitionToResolved(issue);
        } else {
            logger.infof("%s: Issue is already on payload.", issue.getKey());
        }
    }

    @Override
    public void incorporatedIssue(Issue issue, Issue componentUpgrade, String manifestReference) {
        String comment = String.format(INCORPORATED_ISSUE_MESSAGE, fixVersions, componentUpgrade.getKey(), manifestReference);
        if (!hasLabel(issue, LABEL)) {
            issueClient.addComment(issue, comment);
            if (StringUtils.isBlank(targetRelese) || targetRelese.equals(getTargetRelease(issue))) {
                issueClient.updateIssue(issue, fixVersions, LABEL);
            } else {
                // This should handle a situation when we have XP issue incorporated in an EAP component upgrade
                // -> use the layeredFixVersions (that should be the XP fix versions in equivalent CR)
                issueClient.updateIssue(issue, layeredFixVersions, LABEL);
            }
        } else {
            logger.infof("%s: Issue is already on payload.", issue.getKey());
        }
    }

    private String getTargetRelease(Issue issue) {
        for (IssueField field : issue.getFields()) {
            if (JiraConstants.TARGET_RELEASE.equals(field.getName())) {
                try {
                    return ((JSONObject) field.getValue()).get("name").toString();
                } catch (JSONException e) {
                    throw new RuntimeException("Unable to extract target release name from its JSON definition", e);
                }
            }
        }
        return null;
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "SameParameterValue"})
    private static boolean hasLabel(Issue issue, String label) {
        return issue.getLabels().contains(label);
    }

}
