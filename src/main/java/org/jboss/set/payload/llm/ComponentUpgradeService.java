package org.jboss.set.payload.llm;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

interface ComponentUpgradeService {

    @SystemMessage("You must answer strictly in the following JSON format: {\"component\": string, \"targetVersion\": string}"
            + "If the user provided summary doesn't look like a summary of a component upgrade ticket, return 'null' values.")
    @UserMessage("Given following summary of a component upgrade ticket, what component is being upgraded and to what version? " +
            "Summary: {{it}}")
    String extractComponentUpgrade(String summary);
}
