package org.jboss.set.payload.llm;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.eclipse.microprofile.config.Config;
import org.jboss.set.payload.ConfigKeys;

import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class LlmSummaryExtractor {

    private final OpenAiChatModel model;
    private final ComponentUpgradeService service;

    public LlmSummaryExtractor(Config config) {
        String llmBaseUrl = config.getValue(ConfigKeys.LLM_BASE_URL, String.class);
        Integer llmTimeout = config.getOptionalValue(ConfigKeys.LLM_TIMEOUT, Integer.class).orElse(60);

        model = OpenAiChatModel.builder()
                .baseUrl(llmBaseUrl)
                .timeout(Duration.of(llmTimeout, ChronoUnit.SECONDS))
                .logRequests(true)
                .logResponses(true)
                .responseFormat("json_object")
//                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                .strictJsonSchema(true)
                .temperature(0d)
                .build();
        service = AiServices.create(ComponentUpgradeService.class, model);
    }

    public ComponentUpgrade extractInfo(String summary) {
        return service.extractComponentUpgrade(summary);
    }
}
