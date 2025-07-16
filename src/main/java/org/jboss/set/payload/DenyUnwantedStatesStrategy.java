package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;
import org.jboss.logging.Logger;

import java.util.Arrays;

import static org.jboss.set.payload.jira.JiraConstants.CLOSED;
import static org.jboss.set.payload.jira.JiraConstants.DONE;
import static org.jboss.set.payload.jira.JiraConstants.READY_FOR_QA;
import static org.jboss.set.payload.jira.JiraConstants.VERIFIED;

/**
 * This strategy should make sure we don't process issues in certain states, like issues that were already handed over,
 * or issues resolved as duplicates.
 */
public class DenyUnwantedStatesStrategy implements ComponentUpgradeResolutionStrategy {

    private static final Logger logger = Logger.getLogger(DenyUnwantedStatesStrategy.class);

    private static final String[] COMPLETED_STATES = new String[] {READY_FOR_QA, VERIFIED, CLOSED};

    @Override
    public Boolean apply(Issue issue) {
        if (Arrays.stream(COMPLETED_STATES).anyMatch(status -> issue.getStatus().getName().equals(status))) {
            logger.infof("%s: Denying issue in a completed state: %s", issue.getKey(), issue.getStatus().getName());
            return false;
        }
        if (issue.getResolution() != null && !DONE.equals(issue.getResolution().getName())) {
            logger.infof("%s: Denying because of resolution %s", issue.getKey(), issue.getResolution().getName());
            return false;
        }
        return null; // Unknown
    }
}
