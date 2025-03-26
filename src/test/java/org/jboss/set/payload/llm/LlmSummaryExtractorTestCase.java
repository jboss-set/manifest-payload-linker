package org.jboss.set.payload.llm;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.jboss.set.payload.ConfigKeys;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.net.URI;

@Ignore
// TODO: This requires an LLM instance, which may be too expensive to be running at each build. Move this to a
//  non-default profile.
public class LlmSummaryExtractorTestCase {

    private final LlmSummaryExtractor extractor;

    public LlmSummaryExtractorTestCase() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withDefaultValue("llm.base_url", "")
                .build();
         extractor = new LlmSummaryExtractor(config);
    }

    @Ignore // This requires a valid Jira token in the `config/application.properties` file.
    @Test
    public void testExtractionOnJiraTickets() throws Exception {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .addDefaultSources()
                .build();

//        ManifestChecker manifestChecker = new ManifestChecker(Path.of("manifest.yaml"));
//        DependencyGroupResolutionStrategy strategy = new DependencyGroupResolutionStrategy(manifestChecker);

        URI jiraUri = config.getValue(ConfigKeys.JIRA_URL, URI.class);
        String jiraToken = config.getValue(ConfigKeys.JIRA_TOKEN, String.class);
        String jql = config.getValue(ConfigKeys.JIRA_QUERY, String.class);
        try (JiraRestClient jiraClient = new AsynchronousJiraRestClientFactory()
                .createWithAuthenticationHandler(jiraUri,
                        builder -> builder.setHeader("Authorization", "Bearer " + jiraToken))) {
            SearchResult result = jiraClient.getSearchClient().searchJql(jql).claim();

            for (Issue issue: result.getIssues()) {
                ComponentUpgrade info = extractor.extractInfo(issue.getSummary());
                System.out.printf("%s: Upgrade %s to %s%n", issue.getKey(), info.component(), info.targetVersion());
            }
        }
    }


    @Test
    public void testPositive() {
        ComponentUpgrade componentUpgrade = extractor.extractInfo("[8.1.0.GA] Upgrade IronJacamar to 3.0.11.Final-redhat-00001");
        Assert.assertEquals("IronJacamar", componentUpgrade.component());
        Assert.assertEquals("3.0.11.Final-redhat-00001", componentUpgrade.targetVersion());
    }

    @Test
    public void testNegativeResponse() {
        ComponentUpgrade componentUpgrade = extractor.extractInfo("once upon a time");
        Assert.assertEquals("null", componentUpgrade.component());
        Assert.assertEquals("null", componentUpgrade.targetVersion());
    }
}
