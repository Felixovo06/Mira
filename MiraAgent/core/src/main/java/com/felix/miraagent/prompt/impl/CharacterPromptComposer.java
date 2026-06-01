package com.felix.miraagent.prompt.impl;

import com.felix.miraagent.character.CharacterProfile;

public class CharacterPromptComposer {

    public String compose(CharacterProfile profile) {
        if (profile == null) {
            return "";
        }
        var sb = new StringBuilder();
        sb.append("# Character: ").append(profile.getName()).append("\n\n");

        if (hasText(profile.getDescription())) {
            sb.append("## Description\n").append(profile.getDescription()).append("\n\n");
        }
        if (hasText(profile.getPersonality())) {
            sb.append("## Personality\n").append(profile.getPersonality()).append("\n\n");
        }
        if (hasText(profile.getSpeakingStyle())) {
            sb.append("## Speaking Style\n").append(profile.getSpeakingStyle()).append("\n\n");
        }
        if (hasText(profile.getScenario())) {
            sb.append("## Scenario\n").append(profile.getScenario()).append("\n\n");
        }
        if (hasText(profile.getRelationshipToUser())) {
            sb.append("## Relationship to User\n").append(profile.getRelationshipToUser()).append("\n\n");
        }
        if (hasText(profile.getSystemNotes())) {
            sb.append("## System Notes\n").append(profile.getSystemNotes()).append("\n\n");
        }
        if (!profile.getExampleDialogues().isEmpty()) {
            sb.append("## Example Dialogues\n");
            profile.getExampleDialogues().forEach(d -> sb.append(d).append("\n"));
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
