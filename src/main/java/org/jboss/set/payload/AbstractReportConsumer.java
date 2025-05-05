package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;
import org.jboss.set.payload.jira.FaultTolerantIssueClient;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.function.Predicate;

public abstract class AbstractReportConsumer extends AbstractIssueConsumer {

    protected final BufferedWriter writer;

    public AbstractReportConsumer(FaultTolerantIssueClient issueClient, File file, String manifestReference) throws IOException {
        this(issueClient, file, INCLUDE_ALL, INCLUDE_ALL, manifestReference);
    }

    public AbstractReportConsumer(FaultTolerantIssueClient issueClient, File file, Predicate<Issue> componentUpgradeInclusionPredicate,
                                 Predicate<Issue> incorporatedInclusionPredicate, String manifestReference) throws IOException {
        super(issueClient, componentUpgradeInclusionPredicate, incorporatedInclusionPredicate, manifestReference);
        this.writer = new BufferedWriter(new FileWriter(file));
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
