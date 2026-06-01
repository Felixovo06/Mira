package com.felix.miraagent.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileCharacterRepositoryTest {

    private FileCharacterRepository repo;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        repo = new FileCharacterRepository(tmp.toString(), new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void loadsBundledExampleCards() {
        assertTrue(repo.findById("study-buddy").isPresent());
        assertEquals("小研", repo.findById("study-buddy").get().getName());
        assertTrue(repo.findById("mira").isPresent());
        assertTrue(repo.listAll().size() >= 2);
    }

    @Test
    void unknownCardIsEmpty() {
        assertTrue(repo.findById("nope").isEmpty());
    }

    @Test
    void saveThenFindRoundTrip() {
        CharacterProfile c = CharacterProfile.builder()
                .id("coach").name("教练").description("健身搭子").build();
        repo.save(c);
        assertTrue(repo.findById("coach").isPresent());
        assertEquals("教练", repo.findById("coach").get().getName());
    }

    @Test
    void externalCardOverridesBundledById() {
        CharacterProfile override = CharacterProfile.builder()
                .id("mira").name("Mira-Custom").description("自定义").build();
        repo.save(override);
        assertEquals("Mira-Custom", repo.findById("mira").get().getName());
    }

    @Test
    void saveRequiresId() {
        assertThrows(IllegalArgumentException.class,
                () -> repo.save(CharacterProfile.builder().name("无id").build()));
    }
}
