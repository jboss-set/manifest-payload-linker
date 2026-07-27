package org.jboss.set.payload.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;
import org.jboss.set.payload.ConfigKeys;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public class LlmSummaryExtractor {

    private static final Logger logger = Logger.getLogger(LlmSummaryExtractor.class);

    private ComponentUpgradeService service = null;

    public LlmSummaryExtractor(Config config) {
        Optional<String> llmBaseUrl = config.getOptionalValue(ConfigKeys.LLM_BASE_URL, String.class);
        Integer llmTimeout = config.getOptionalValue(ConfigKeys.LLM_TIMEOUT, Integer.class).orElse(60);

        if (llmBaseUrl.isPresent()) {
            //                .responseFormat("json_object")
            //                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
            //                .strictJsonSchema(true)
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .baseUrl(llmBaseUrl.get())
                    .timeout(Duration.of(llmTimeout, ChronoUnit.SECONDS))
                    .logRequests(true)
                    .logResponses(true)
//                .responseFormat("json_object")
//                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
//                .strictJsonSchema(true)
                    .temperature(0d)
                    .build();
            service = AiServices.create(ComponentUpgradeService.class, model);
        }
    }

    public ComponentUpgrade extractInfo(String summary) {
        if (service == null) {
            logger.warnf("LLM service not configured.");
            return null;
        }
        String responseContent = null;
        try {
            responseContent = service.extractComponentUpgrade(summary);
            return YAMLMapper.builder().build().readValue(responseContent, ComponentUpgrade.class);
        } /*catch (RuntimeException e) {
            logger.errorf(e, "Can't parse issue summary \"%s\"", summary);
            return null;
        }*/ catch (JsonProcessingException e) {
            logger.errorf(e, "Can't parse LLM response for summary \"%s\": %s", summary, responseContent);
            return null;
        }
    }
}
