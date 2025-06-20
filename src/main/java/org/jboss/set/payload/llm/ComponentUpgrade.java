package org.jboss.set.payload.llm;

import org.apache.commons.lang3.StringUtils;

public record ComponentUpgrade(String component, String targetVersion) {
    @Override
    public String toString() {
        return "ComponentUpgrade{" +
                "component='" + component + '\'' +
                ", targetVersion='" + targetVersion + '\'' +
                '}';
    }

    public boolean isValid() {
        // This is dependent on the LLM behavior, and different models get give different indication of null values.
        return !StringUtils.isBlank(component) && !"null".equals(component)
                && !StringUtils.isBlank(targetVersion) && !"null".equals(targetVersion)
                && targetVersion.split("\\.").length > 1;
    }
}
