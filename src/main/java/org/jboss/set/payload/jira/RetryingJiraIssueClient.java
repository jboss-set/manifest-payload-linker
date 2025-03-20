package org.jboss.set.payload.jira;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.Issue;

/**
 * Very stupid retrying implementation of the issue client to overcome occasional 401 errors.
 */
public class RetryingJiraIssueClient {
    private final IssueRestClient issueRestClient;

    public RetryingJiraIssueClient(IssueRestClient issueRestClient) {
        this.issueRestClient = issueRestClient;
    }

    public Issue getIssue(String issueKey) {
        try {
            return issueRestClient.getIssue(issueKey).claim();
        } catch (RestClientException e) {
            if (e.getStatusCode().or(0).equals(401)) {
                return issueRestClient.getIssue(issueKey).claim();
            }
            throw e;
        }
    }
}
