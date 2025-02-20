package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public class DetailedReportConsumer extends AbstractReportConsumer {

    private final URI jiraUri;
    private final JiraRestClient jiraClient;

    public DetailedReportConsumer(File file, URI jiraUri, JiraRestClient jiraClient) throws IOException {
        super(file);
        this.jiraUri = jiraUri;
        this.jiraClient = jiraClient;
    }

    @Override
    protected String componentUpgradeIssueLine(Issue issue) {
        String line = String.format("%s [%s]",
                issueLink(jiraUri, issue.getKey()), issue.getStatus().getName());
        System.out.println(line);
        return line + System.lineSeparator();
    }

    @Override
    protected String incorporatedIssueLine(String key) {
        Issue issue = jiraClient.getIssueClient().getIssue(key).claim();
        String line = String.format("  %s [%s]",
                issueLink(jiraUri, issue.getKey()), issue.getStatus().getName());
        System.out.println(line);
        return line + System.lineSeparator();
    }

    public static String issueLink(URI jiraUri, String issueKey) {
        return jiraUri.resolve("browse/").resolve(issueKey).toString();
    }

}
