package org.jboss.set.payload.llm;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

interface ComponentUpgradeService {

    @SystemMessage("You are a data extraction bot. Be brief and provide easily parseable responses. " +
            "Only include the data in the response that are being requested. " +
            "If the user provided summary doesn't look like a summary of a component upgrade ticket, return 'null' values.")
    @UserMessage("Given following summary of a component upgrade ticket, what component is being upgraded and to what version? " +
            "Summary: {{it}}")
    ComponentUpgrade extractComponentUpgrade(String summary);
}
