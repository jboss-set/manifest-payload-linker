package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import com.atlassian.jira.rest.client.api.domain.IssueLinkType;
import org.jboss.set.payload.jira.RetryingJiraIssueClient;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

public abstract class AbstractReportConsumer implements Consumer<Issue>, Closeable {

    protected static final Predicate<Issue> INCLUDE_ALL = i -> true;
    protected static final Predicate<Issue> INCLUDE_NON_VERIFIED = i -> !"Verified".equals(i.getStatus().getName());
    protected static final Predicate<Issue> INCLUDE_RESOLVED = i -> "Resolved".equals(i.getStatus().getName());


    private static final String INCORPORATES = "Incorporates";

    private final BufferedWriter writer;
    protected final RetryingJiraIssueClient issueClient;
    private final Predicate<Issue> componentUpgradeInclusionPredicate;
    private final Predicate<Issue> incorporatedInclusionPredicate;

    public AbstractReportConsumer(RetryingJiraIssueClient issueClient, File file) throws IOException {
        this(issueClient, file, INCLUDE_ALL, INCLUDE_ALL);
    }

    public AbstractReportConsumer(RetryingJiraIssueClient issueClient, File file, Predicate<Issue> componentUpgradeInclusionPredicate,
                                  Predicate<Issue> incorporatedInclusionPredicate) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(file));
        this.issueClient = issueClient;
        this.componentUpgradeInclusionPredicate = componentUpgradeInclusionPredicate;
        this.incorporatedInclusionPredicate = incorporatedInclusionPredicate;
    }

    @Override
    public void accept(Issue issue) {
        try {
            if (componentUpgradeInclusionPredicate.test(issue)) {
                writer.write(componentUpgradeIssueLine(issue));
            }
            for (String key: filterIncorporatedIssueCodes(issue)) {
                Issue incorporatedIssue = issueClient.getIssue(key);
                if (incorporatedInclusionPredicate.test(incorporatedIssue)) {
                    writer.write(incorporatedIssueLine(incorporatedIssue));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract String componentUpgradeIssueLine(Issue issue);

    protected abstract String incorporatedIssueLine(Issue issue);

    protected List<String> filterIncorporatedIssueCodes(Issue issue) {
        if (issue.getIssueLinks() != null) {
            return StreamSupport.stream(issue.getIssueLinks().spliterator(), false)
                    .filter(l -> INCORPORATES.equals(l.getIssueLinkType().getName())
                            && IssueLinkType.Direction.OUTBOUND.equals(l.getIssueLinkType().getDirection()))
                    .map(IssueLink::getTargetIssueKey)
                    .toList();
        }
        return Collections.emptyList();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
