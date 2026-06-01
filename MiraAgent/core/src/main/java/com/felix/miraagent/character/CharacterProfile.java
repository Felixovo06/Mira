package com.felix.miraagent.character;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Value
@Builder
@Jacksonized
public class CharacterProfile {
    String id;
    String name;
    String description;
    String personality;
    String scenario;
    String firstMessage;
    @Singular
    List<String> exampleDialogues;
    String speakingStyle;
    String relationshipToUser;
    String systemNotes;
    @Singular
    List<String> tags;

    public static CharacterProfile defaultProfile() {
        return CharacterProfile.builder()
                .id("default")
                .name("Mira")
                .description("A helpful and warm AI companion.")
                .personality("Warm, thoughtful, capable.")
                .build();
    }
}
