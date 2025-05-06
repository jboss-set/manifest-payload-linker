package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import com.atlassian.jira.rest.client.api.domain.IssueLinkType;
import org.jboss.set.payload.jira.FaultTolerantIssueClient;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

public abstract class AbstractIssueConsumer implements IssueConsumer {

    public static final Predicate<Issue> INCLUDE_ALL = i -> true;
    public static final Predicate<Issue> INCLUDE_NONE = i -> false;
    public static final Predicate<Issue> INCLUDE_NON_VERIFIED = i -> !"Verified".equals(i.getStatus().getName());
    public static final Predicate<Issue> INCLUDE_RESOLVED = i -> "Resolved".equals(i.getStatus().getName());


    private static final String INCORPORATES = "Incorporates";

    protected final FaultTolerantIssueClient issueClient;
    private final Predicate<Issue> componentUpgradeInclusionPredicate;
    private final Predicate<Issue> incorporatedInclusionPredicate;
    private final String manifestReference;

    public AbstractIssueConsumer(FaultTolerantIssueClient issueClient, Predicate<Issue> componentUpgradeInclusionPredicate,
                                 Predicate<Issue> incorporatedInclusionPredicate, String manifestReference) {
        this.issueClient = issueClient;
        this.componentUpgradeInclusionPredicate = componentUpgradeInclusionPredicate;
        this.incorporatedInclusionPredicate = incorporatedInclusionPredicate;
        this.manifestReference = manifestReference;
    }

    @Override
    public void accept(Issue issue) {
        try {
            if (componentUpgradeInclusionPredicate.test(issue)) {
                componentUpgradeIssue(issue, manifestReference);
            }
            for (String key: filterIncorporatedIssueCodes(issue)) {
                Issue incorporatedIssue = issueClient.getIssue(key);
                if (incorporatedInclusionPredicate.test(incorporatedIssue)) {
                    incorporatedIssue(incorporatedIssue, issue, manifestReference);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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

}
