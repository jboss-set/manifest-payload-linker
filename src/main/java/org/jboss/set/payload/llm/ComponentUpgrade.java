package org.jboss.set.payload.llm;

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
        return !"null".equals(component) && !"null".equals(targetVersion);
    }
}
