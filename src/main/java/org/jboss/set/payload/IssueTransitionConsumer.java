package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;
import org.jboss.set.payload.jira.FaultTolerantIssueClient;

public class IssueTransitionConsumer extends AbstractIssueConsumer {

    private static final String COMPONENT_UPGRADE_MESSAGE = "This component upgrade has been incorporated in %s.";
    private static final String INCORPORATED_ISSUE_MESSAGE = "This issue has been incorporated in %s via component upgrade %s.";
    private static final String LABEL = "on-payload";

    private final String fixVersion;


    public IssueTransitionConsumer(FaultTolerantIssueClient issueClient, String manifestReference, String fixVersion) {
        super(issueClient, INCLUDE_NON_VERIFIED, INCLUDE_RESOLVED, manifestReference);
        this.fixVersion = fixVersion;
    }

    @Override
    public void componentUpgradeIssue(Issue issue, String manifestReference) {
        String comment = String.format(COMPONENT_UPGRADE_MESSAGE, manifestReference);
        if (!hasLabel(issue, LABEL)) {
            issueClient.addComment(issue, comment);
            issueClient.updateIssue(issue, fixVersion, LABEL);
            issueClient.transitionToResolved(issue);
        }
    }

    @Override
    public void incorporatedIssue(Issue issue, Issue componentUpgrade, String manifestReference) {
        String comment = String.format(INCORPORATED_ISSUE_MESSAGE, manifestReference, componentUpgrade.getKey());
        if (!hasLabel(issue, LABEL)) {
            issueClient.addComment(issue, comment);
            issueClient.updateIssue(issue, fixVersion, LABEL);
        }
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "SameParameterValue"})
    private static boolean hasLabel(Issue issue, String label) {
        return issue.getLabels().contains(label);
    }

}
