package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;

public interface IssueConsumer extends Consumer<Issue>, Closeable {

    void componentUpgradeIssue(Issue issue, String manifestReference) throws Exception;

    void incorporatedIssue(Issue issue, Issue componentUpgrade, String manifestReference) throws Exception;

    default void close() throws IOException {
    }
}
