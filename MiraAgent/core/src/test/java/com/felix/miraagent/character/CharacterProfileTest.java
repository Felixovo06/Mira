package com.felix.miraagent.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CharacterProfileTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTripPreservesFieldsAndLists() throws Exception {
        CharacterProfile profile = CharacterProfile.builder()
                .id("study-buddy").name("小研").description("学习搭子")
                .personality("踏实").scenario("备考").firstMessage("嗨")
                .exampleDialogue("d1").exampleDialogue("d2")
                .speakingStyle("简洁").relationshipToUser("搭子")
                .systemNotes("note").tag("学习").tag("规划")
                .build();

        String json = mapper.writeValueAsString(profile);
        CharacterProfile back = mapper.readValue(json, CharacterProfile.class);

        assertEquals("study-buddy", back.getId());
        assertEquals("小研", back.getName());
        assertEquals(2, back.getExampleDialogues().size());
        assertEquals(2, back.getTags().size());
        assertEquals("简洁", back.getSpeakingStyle());
    }

    @Test
    void deserializesPartialCard() throws Exception {
        String json = "{\"id\":\"mira\",\"name\":\"Mira\",\"description\":\"陪伴\"}";
        CharacterProfile back = mapper.readValue(json, CharacterProfile.class);
        assertEquals("mira", back.getId());
        assertEquals("Mira", back.getName());
        assertNull(back.getPersonality());
    }
}
