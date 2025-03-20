package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.jboss.set.payload.jira.RetryingJiraIssueClient;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public class DetailedReportConsumer extends AbstractReportConsumer {

    private final URI jiraUri;

    public DetailedReportConsumer(RetryingJiraIssueClient issueClient, File file, URI jiraUri) throws IOException {
        super(issueClient, file);
        this.jiraUri = jiraUri;
    }

    @Override
    protected String componentUpgradeIssueLine(Issue issue) {
        String line = line(issue);
        System.out.print(line);
        return line;
    }

    @Override
    protected String incorporatedIssueLine(Issue issue) {
        String line = "  " + line(issue);
        System.out.print(line);
        return line;
    }

    private String line(Issue issue) {
        String line = String.format("%s [%s / %s / %s]",
                issueLink(jiraUri, issue.getKey()), issue.getIssueType().getName(), extractTargetRelease(issue),
                issue.getStatus().getName());
        return line + System.lineSeparator();
    }

    private static String extractTargetRelease(Issue issue) {
        try {
            IssueField field = issue.getFieldByName("Target Release");
            if (field != null) {
                JSONObject value = (JSONObject) field.getValue();
                return value != null ? value.getString("name") : null;
            }
            return "";
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static String issueLink(URI jiraUri, String issueKey) {
        return jiraUri.resolve("browse/").resolve(issueKey).toString();
    }

}
