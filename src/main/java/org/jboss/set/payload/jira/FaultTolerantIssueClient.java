package org.jboss.set.payload.jira;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Transition;
import com.atlassian.jira.rest.client.api.domain.Version;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import io.atlassian.util.concurrent.Promise;
import org.apache.commons.lang3.stream.Streams;
import org.jboss.logging.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.jboss.set.payload.jira.JiraConstants.CLOSED;
import static org.jboss.set.payload.jira.JiraConstants.NEW;
import static org.jboss.set.payload.jira.JiraConstants.READY_FOR_QA;
import static org.jboss.set.payload.jira.JiraConstants.RESOLVED;
import static org.jboss.set.payload.jira.JiraConstants.VERIFIED;

/**
 * Jira client wrapper that implements retry & rate limiting logic.
 */
public class FaultTolerantIssueClient {

    private static final Logger logger = Logger.getLogger(FaultTolerantIssueClient.class);

    private static final String[] RESOLVED_STATES = new String[] {RESOLVED, READY_FOR_QA, VERIFIED, CLOSED};

    private final IssueRestClient issueRestClient;
    private final Invoker invoker;

    /**
     * If set to true, all issue modification operations will be no-ops.
     */
    private final boolean dryMode;

    public FaultTolerantIssueClient(IssueRestClient issueRestClient, long spacingInMillis, boolean dryMode) {
        this.issueRestClient = issueRestClient;
        this.dryMode = dryMode;

        TimeSpacingInvoker timeSpacingInvoker = new TimeSpacingInvoker(spacingInMillis);
        this.invoker = new RetryingInvoker(timeSpacingInvoker);
    }

    public Issue getIssue(String issueKey) {
        Callable<Promise<Issue>> callable = () -> issueRestClient.getIssue(issueKey);
        return invoker.invoke(callable);
    }

    public void addComment(final Issue issue, final String comment) {
        logger.infof("%s: Commenting on issue %s: %s", issue.getKey(), dryModeFlag(), comment);
        if (!dryMode) {
            Callable<Promise<Void>> callable = () -> issueRestClient.addComment(issue.getCommentsUri(),
                    Comment.createWithGroupLevel(comment, "Red Hat Employee"));
            invoker.invoke(callable);
        }
    }

    public void addLabel(final Issue issue, final String label) {
        logger.infof("%s: Adding label \"%s\" to issue %s %s", issue.getKey(), label, dryModeFlag());
        Set<String> labels = issue.getLabels();
        labels.add(label);

        final IssueInput issueInput = new IssueInputBuilder()
                .setFieldValue("labels", labels)
                .build();

        if (!dryMode) {
            Callable<Promise<Void>> callable = () -> issueRestClient.updateIssue(issue.getKey(), issueInput);
            invoker.invoke(callable);
        }
    }

    public void updateIssue(final Issue issue, final String fixVersion, final String label) {
        boolean change = false;

        Set<String> fixVersions = Streams.of(issue.getFixVersions())
                .map(Version::getName)
                .collect(Collectors.toSet());
        if (!fixVersions.contains(fixVersion)) {
            fixVersions.add(fixVersion);
            change = true;
        } else {
            logger.infof("%s: Fix version \"%s\" already set.", issue.getKey(), fixVersion);
        }

        Set<String> labels = issue.getLabels();
        if (!labels.contains(label)) {
            labels.add(label);
            change = true;
        } else {
            logger.infof("%s: Label \"%s\" already set.", issue.getKey(), label);
        }

        if (change) {
            logger.infof("%s: Updating issue with fix_version = %s, label = %s %s",
                    issue.getKey(), fixVersion, label, dryModeFlag());
            if (!dryMode) {
                final IssueInput issueInput = new IssueInputBuilder()
                        .setFieldValue("labels", labels)
                        .setFixVersionsNames(fixVersions)
                        .build();
                Callable<Promise<Void>> callable = () -> issueRestClient.updateIssue(issue.getKey(), issueInput);
                invoker.invoke(callable);
            }
        }
    }

    public void transitionToResolved(final Issue issue) {
        if (issue.getStatus().getName().equals(NEW)) {
            logger.warnf("%s: Transitioning from NEW state is not implemented currently.", issue.getKey());
            return;
        }
        if (Arrays.stream(RESOLVED_STATES).anyMatch(state -> state.equals(issue.getStatus().getName()))) {
            logger.infof("%s: Issue is already resolved (%s)", issue.getKey(), issue.getStatus().getName());
            return;
        }
        logger.infof("%s: Transitioning issue to Resolved %s", issue.getKey(), dryModeFlag());
        int transitionId = getResolveTransitionId(issue);
        TransitionInput transitionInput = new TransitionInput(transitionId, List.of(
                new FieldInput("resolution", ComplexIssueInputFieldValue.with("name", "Done"))));
        if (!dryMode) {
            Callable<Promise<Void>> callable = () -> issueRestClient.transition(issue, transitionInput);
            invoker.invoke(callable);
        }
    }

    private int getResolveTransitionId(Issue issue) {
        Iterable<Transition> transitions = issueRestClient.getTransitions(issue).claim();
        for (Transition transition : transitions) {
            if ("Resolve Issue".equals(transition.getName())) {
                return transition.getId();
            }
        }
        throw new RuntimeException("Transition to Resolved is not available.");
    }

    private String dryModeFlag() {
        return dryMode ? "(dry mode)" : "";
    }

    private interface Invoker {
        <T> T invoke(Callable<Promise<T>> callable);
    }

    private static class TimeSpacingInvoker implements Invoker {

        private final long spacingInMillis;
        private long lastRequest = 0;

        public TimeSpacingInvoker(long spacingInMillis) {
            this.spacingInMillis = spacingInMillis;
        }

        @Override
        public <T> T invoke(Callable<Promise<T>> callable) {
            try {
                // If rate limit is set, make sure Jira client call is delayed according to configuration.
                if (lastRequest > 0 && spacingInMillis > 0) {
                    long elapsed = System.currentTimeMillis() - lastRequest;
                    if (elapsed < spacingInMillis) {
                        long delay = spacingInMillis - elapsed;
                        logger.debugf("Delaying Jira client call for %d ms.", delay);
                        Thread.sleep(delay);
                    }
                }
                return callable.call().claim();
            } catch (RuntimeException e) {
                // Rethrow runtime exceptions as we need to handle them in RetryingInvoker.
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                lastRequest = System.currentTimeMillis();
            }
        }
    }

    private static class RetryingInvoker implements Invoker {

        private final Invoker delegate;

        public RetryingInvoker(Invoker delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> T invoke(Callable<Promise<T>> callable) {
            return invoke(callable, 2);
        }

        private <T> T invoke(Callable<Promise<T>> callable, int attempts) {
            try {
                return delegate.invoke(callable);
            } catch (RestClientException e) {
                // Here we are only retrying in case the previous request resulted in HTTP 401.
                if (e.getStatusCode().or(0).equals(401)) {
                    if (attempts - 1 > 0) {
                        logger.errorf(e, "Exception caught from Jira client invocation, retrying %d more attempts", attempts);
                        return invoke(callable, attempts - 1);
                    } else {
                        throw e;
                    }
                }
                throw e;
            }
        }
    }
}
