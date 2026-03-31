package com.example.agentruntime.catalog;

import java.util.List;

/**
 * 内置 Skill 模板。
 */
record CuratedSkillTemplate(
        String id,
        String name,
        String version,
        String description,
        String prompt,
        List<String> tags
) {

    String manifestYaml() {
        StringBuilder builder = new StringBuilder();
        builder.append("id: ").append(id).append('\n');
        builder.append("name: ").append(name).append('\n');
        builder.append("version: ").append(version).append('\n');
        builder.append("description: ").append(description).append('\n');
        builder.append("type: prompt\n");
        builder.append("prompt: |\n");
        for (String line : prompt.split("\\R")) {
            builder.append("  ").append(line).append('\n');
        }
        builder.append("riskLevel: LOW\n");
        builder.append("tags:\n");
        for (String tag : tags) {
            builder.append("  - ").append(tag).append('\n');
        }
        return builder.toString();
    }
}
