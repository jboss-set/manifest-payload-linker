package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;
import org.jboss.set.payload.jira.FaultTolerantIssueClient;

import java.io.File;
import java.io.IOException;

public class IssueCodesReportConsumer extends AbstractReportConsumer {

    private boolean firstIssue = false;

    public IssueCodesReportConsumer(FaultTolerantIssueClient issueClient, File file, String manifestReference) throws IOException {
        super(issueClient, file, INCLUDE_RESOLVED, INCLUDE_RESOLVED, manifestReference);
    }

    @Override
    public void componentUpgradeIssue(Issue issue, String manifestReference) throws IOException {
        writer.write(line(issue));
    }

    @Override
    public void incorporatedIssue(Issue issue, Issue componentUpgrade, String manifestReference) throws IOException {
        writer.write(line(issue));
    }

    private String line(Issue issue) {
        String delimiter = firstIssue ? ", " : "";
        firstIssue = true;
        return delimiter + issue.getKey();
    }

}
