package org.jboss.set.payload.jira;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Transition;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import io.atlassian.util.concurrent.Promise;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Jira client wrapper that implements retry & rate limiting logic.
 */
public class FaultTolerantIssueClient {

    private static final Logger logger = Logger.getLogger(FaultTolerantIssueClient.class);

    private final IssueRestClient issueRestClient;
    private final Invoker invoker;

    /**
     * If set to true, all issue modification operations will be no-ops.
     */
    private final boolean dryMode = false;

    public FaultTolerantIssueClient(IssueRestClient issueRestClient, long spacingInMillis) {
        this.issueRestClient = issueRestClient;

        TimeSpacingInvoker timeSpacingInvoker = new TimeSpacingInvoker(spacingInMillis);
        invoker = new RetryingInvoker(timeSpacingInvoker);
    }

    public Issue getIssue(String issueKey) {
        Callable<Promise<Issue>> callable = () -> issueRestClient.getIssue(issueKey);
        return invoker.invoke(callable);
    }

    public void addComment(final Issue issue, final String comment) {
        logger.infof("Commenting on issue %s: %s", issue.getKey(), comment);
        if (!dryMode) {
            Callable<Promise<Void>> callable = () -> issueRestClient.addComment(issue.getCommentsUri(),
                    Comment.createWithGroupLevel(comment, "Red Hat Employee"));
            invoker.invoke(callable);
        }
    }

    public void addLabel(final Issue issue, final String label) {
        logger.infof("Adding label %s to issue %s", label, issue.getKey());
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

    public void transitionToResolved(final Issue issue) {
        logger.infof("Transitioning issue %s to Resolved", issue.getKey());
        int transitionId = getResolveTransitionId(issue);
        TransitionInput transitionInput = new TransitionInput(transitionId, List.of(new FieldInput("resolution", "Done")));
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

        private final Invoker nestedInvoker;

        public RetryingInvoker(Invoker nestedInvoker) {
            this.nestedInvoker = nestedInvoker;
        }

        @Override
        public <T> T invoke(Callable<Promise<T>> callable) {
            return invoke(callable, 2);
        }

        private <T> T invoke(Callable<Promise<T>> callable, int attempts) {
            try {
                return nestedInvoker.invoke(callable);
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
