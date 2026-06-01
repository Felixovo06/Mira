package com.felix.miraagent.memory.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.miraagent.memory.MemoryCategory;
import com.felix.miraagent.memory.MemoryIndex;
import com.felix.miraagent.memory.MemoryRetrieveRequest;
import com.felix.miraagent.memory.MemoryRetrieveResult;
import com.felix.miraagent.memory.MemoryScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcHybridMemoryRetrieverTest {

    @Test
    void rerankBoostsCurrentCharacterAndCategoryAfterRrf() {
        MemoryIndex genericEvent = MemoryIndex.builder()
                .id("event")
                .userId("u1")
                .scope(MemoryScope.GLOBAL)
                .category(MemoryCategory.EVENT)
                .contentPreview("generic")
                .createdAt(Instant.parse("2024-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2024-01-01T00:00:00Z"))
                .build();
        MemoryIndex characterPreference = MemoryIndex.builder()
                .id("pref")
                .userId("u1")
                .characterId("mira")
                .scope(MemoryScope.CHARACTER)
                .category(MemoryCategory.PREFERENCE)
                .contentPreview("character preference")
                .createdAt(Instant.parse("2024-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2024-01-01T00:00:00Z"))
                .build();

        var lexical = new StubLexicalRetriever(List.of(genericEvent, characterPreference));
        var hybrid = new JdbcHybridMemoryRetriever(lexical, null, null, new ObjectMapper());

        MemoryRetrieveResult result = hybrid.retrieve(MemoryRetrieveRequest.builder()
                .userId("u1")
                .characterId("mira")
                .query("tea")
                .limit(2)
                .build());

        assertEquals("pref", result.getHits().get(0).getId());
        assertEquals("event", result.getHits().get(1).getId());
    }

    private static class StubLexicalRetriever extends JdbcMemoryRetriever {
        private final List<MemoryIndex> hits;

        StubLexicalRetriever(List<MemoryIndex> hits) {
            super(null, new ObjectMapper());
            this.hits = hits;
        }

        @Override
        public MemoryRetrieveResult retrieve(MemoryRetrieveRequest request) {
            return MemoryRetrieveResult.builder().hits(hits).queryUsed(request.getQuery()).build();
        }
    }
}
