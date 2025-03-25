package org.jboss.set.payload;

import com.atlassian.jira.rest.client.api.domain.Issue;

import java.util.function.Function;
import java.util.function.Predicate;

public interface ComponentUpgradeResolutionStrategy extends Function<Issue, Boolean> {

}
