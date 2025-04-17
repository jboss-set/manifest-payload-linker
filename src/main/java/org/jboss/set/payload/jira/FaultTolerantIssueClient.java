package org.jboss.set.payload.jira;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.Issue;
import org.jboss.logging.Logger;

/**
 * Jira client wrapper that implements retry & rate limiting logic.
 */
public class FaultTolerantIssueClient {

    private static final Logger logger = Logger.getLogger(FaultTolerantIssueClient.class);

    private final IssueRestClient issueRestClient;
    private final long spacingInMillis;

    private long lastRequest = 0;

    public FaultTolerantIssueClient(IssueRestClient issueRestClient, long spacingInMillis) {
        this.issueRestClient = issueRestClient;
        this.spacingInMillis = spacingInMillis;
    }

    public Issue getIssue(String issueKey) {
        try {
            // If rate limit is set, make sure Jira client call is delayed according to configuration.
            if (lastRequest > 0 && spacingInMillis > 0) {
                long elapsed = System.currentTimeMillis() - lastRequest;
                if (elapsed < spacingInMillis) {
                    long delay = spacingInMillis - elapsed;
                    logger.debugf("Delaying Jira client call for %d ms.", delay);
                    Thread.sleep(delay);
                }
            }

            return issueRestClient.getIssue(issueKey).claim();
        } catch (RestClientException e) {
            if (e.getStatusCode().or(0).equals(401)) {
                return issueRestClient.getIssue(issueKey).claim();
            }
            throw e;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lastRequest = System.currentTimeMillis();
        }
    }
}
