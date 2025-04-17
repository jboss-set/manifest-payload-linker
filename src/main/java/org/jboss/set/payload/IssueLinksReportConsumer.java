package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;
import org.jboss.set.payload.jira.FaultTolerantIssueClient;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public class IssueLinksReportConsumer extends AbstractReportConsumer {

    private final URI jiraUri;

    public IssueLinksReportConsumer(FaultTolerantIssueClient issueClient, File file, URI jiraUri) throws IOException {
        super(issueClient, file, INCLUDE_RESOLVED, INCLUDE_RESOLVED);
        this.jiraUri = jiraUri;
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
        return issueLink(jiraUri, issue.getKey()) + System.lineSeparator();
    }

    public static String issueLink(URI jiraUri, String issueKey) {
        return jiraUri.resolve("browse/").resolve(issueKey).toString();
    }

}
