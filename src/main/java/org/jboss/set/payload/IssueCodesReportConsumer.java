package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;
import org.jboss.set.payload.jira.RetryingJiraIssueClient;

import java.io.File;
import java.io.IOException;

public class IssueCodesReportConsumer extends AbstractReportConsumer {

    private boolean firstIssue = false;

    public IssueCodesReportConsumer(RetryingJiraIssueClient issueClient, File file) throws IOException {
        super(issueClient, file, INCLUDE_RESOLVED, INCLUDE_RESOLVED);
    }

    @Override
    protected String componentUpgradeIssueLine(Issue issue) {
        return line(issue);
    }

    @Override
    protected String incorporatedIssueLine(Issue issue) {
        return line(issue);
    }

    private String line(Issue issue) {
        String delimiter = firstIssue ? ", " : "";
        firstIssue = true;
        return delimiter + issue.getKey();
    }

}
