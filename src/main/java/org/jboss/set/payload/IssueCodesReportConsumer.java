package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;

import java.io.File;
import java.io.IOException;

public class IssueCodesReportConsumer extends AbstractReportConsumer {

    private boolean firstIssue = false;

    public IssueCodesReportConsumer(File file) throws IOException {
        super(file);
    }

    @Override
    protected String componentUpgradeIssueLine(Issue issue) {
        return incorporatedIssueLine(issue.getKey());
    }

    @Override
    protected String incorporatedIssueLine(String key) {
        String delimiter = firstIssue ? ", " : "";
        firstIssue = true;
        return delimiter + key;
    }

}
