package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;
import org.jboss.set.payload.jira.FaultTolerantIssueClient;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.function.Predicate;

public class IssueLinksReportConsumer extends AbstractReportConsumer {

    private final URI jiraUri;

    public IssueLinksReportConsumer(FaultTolerantIssueClient issueClient, File file, URI jiraUri, String manifestReference)
            throws IOException {
        this(issueClient, file, INCLUDE_RESOLVED, INCLUDE_RESOLVED, jiraUri, manifestReference);
    }

    public IssueLinksReportConsumer(FaultTolerantIssueClient issueClient, File file,
                                    Predicate<Issue> componentUpgradeInclusionPredicate,
                                    Predicate<Issue> incorporatedInclusionPredicate,
                                    URI jiraUri, String manifestReference)
            throws IOException {
        super(issueClient, file, componentUpgradeInclusionPredicate, incorporatedInclusionPredicate, manifestReference);
        this.jiraUri = jiraUri;
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
        return issueLink(jiraUri, issue.getKey()) + System.lineSeparator();
    }

    public static String issueLink(URI jiraUri, String issueKey) {
        return jiraUri.resolve("browse/").resolve(issueKey).toString();
    }

}
