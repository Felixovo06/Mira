package com.felix.miraagent.api;

import com.felix.miraagent.api.controller.CharacterController;
import com.felix.miraagent.character.CharacterProfile;
import com.felix.miraagent.character.CharacterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CharacterControllerTest {

    /** 极简内存仓库。 */
    static class InMemoryRepo implements CharacterRepository {
        final List<CharacterProfile> store = new ArrayList<>();
        @Override public Optional<CharacterProfile> findById(String id) {
            return store.stream().filter(c -> c.getId().equals(id)).findFirst();
        }
        @Override public List<CharacterProfile> listAll() { return store; }
        @Override public CharacterProfile save(CharacterProfile p) { store.add(p); return p; }
    }

    @Test
    void listAndGet() {
        var repo = new InMemoryRepo();
        repo.save(CharacterProfile.builder().id("mira").name("Mira").build());
        var c = new CharacterController(repo);

        assertEquals(1, c.list().getBody().size());
        assertEquals("Mira", c.get("mira").getBody().getName());
        assertEquals(HttpStatus.NOT_FOUND, c.get("nope").getStatusCode());
    }

    @Test
    void importCardCreated() {
        var c = new CharacterController(new InMemoryRepo());
        var resp = c.importCard(CharacterProfile.builder().id("coach").name("教练").build());
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    void importWithoutIdIsBadRequest() {
        var c = new CharacterController(new InMemoryRepo());
        var resp = c.importCard(CharacterProfile.builder().name("无id").build());
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }
}
