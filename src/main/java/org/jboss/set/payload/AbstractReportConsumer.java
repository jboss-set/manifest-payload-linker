package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import com.atlassian.jira.rest.client.api.domain.IssueLinkType;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

public abstract class AbstractReportConsumer implements Consumer<Issue>, Closeable {

    private static final String INCORPORATES = "Incorporates";

    private final BufferedWriter writer;

    public AbstractReportConsumer(File file) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(file));
    }

    @Override
    public void accept(Issue issue) {
        try {
            writer.write(componentUpgradeIssueLine(issue));
            for (String key: filterIncorporatedIssueCodes(issue)) {
                writer.write(incorporatedIssueLine(key));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract String componentUpgradeIssueLine(Issue issue);

    protected abstract String incorporatedIssueLine(String key);

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
