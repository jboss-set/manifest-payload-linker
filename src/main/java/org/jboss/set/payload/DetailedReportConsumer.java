package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.jboss.set.payload.jira.FaultTolerantIssueClient;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public class DetailedReportConsumer extends AbstractReportConsumer {

    private final URI jiraUri;

    public DetailedReportConsumer(FaultTolerantIssueClient issueClient, File file, URI jiraUri, String manifestReference) throws IOException {
        super(issueClient, file, manifestReference);
        this.jiraUri = jiraUri;
    }

    @Override
    public void componentUpgradeIssue(Issue issue, String manifestReference) throws IOException {
        String line = line(issue);
        System.out.print(line);
        writer.write(line);
    }

    @Override
    public void incorporatedIssue(Issue issue, Issue componentUpgrade, String manifestReference) throws IOException {
        String line = "  " + line(issue);
        System.out.print(line);
        writer.write(line);
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
