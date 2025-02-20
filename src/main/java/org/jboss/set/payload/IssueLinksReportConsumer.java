package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public class IssueLinksReportConsumer extends AbstractReportConsumer {

    private final URI jiraUri;

    public IssueLinksReportConsumer(File file, URI jiraUri) throws IOException {
        super(file);
        this.jiraUri = jiraUri;
    }

    @Override
    protected String componentUpgradeIssueLine(Issue issue) {
//        System.out.println(issueLink(jiraUri, issue.getKey()));
        return issueLink(jiraUri, issue.getKey()) + System.lineSeparator();
    }

    @Override
    protected String incorporatedIssueLine(String key) {
//        System.out.println("  " + issueLink(jiraUri, key));
        return "  " + issueLink(jiraUri, key) + System.lineSeparator();
    }

    public static String issueLink(URI jiraUri, String issueKey) {
        return jiraUri.resolve("browse/").resolve(issueKey).toString();
    }

}
