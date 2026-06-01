package com.felix.miraagent.api.controller;

import com.felix.miraagent.character.CharacterProfile;
import com.felix.miraagent.character.CharacterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色卡管理 API：列表 / 查看 / 导入（创建或覆盖）。
 * 服务 Web UI 与微信入口，导入后的卡即可作为 chat 的 characterId 使用。
 */
@RestController
@RequestMapping("/api/characters")
public class CharacterController {

    private final CharacterRepository characterRepository;

    public CharacterController(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    @GetMapping
    public ResponseEntity<List<CharacterProfile>> list() {
        return ResponseEntity.ok(characterRepository.listAll());
    }

    @GetMapping("/{characterId}")
    public ResponseEntity<CharacterProfile> get(@PathVariable String characterId) {
        return characterRepository.findById(characterId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 导入角色卡（JSON 即 CharacterProfile，以 id 为主键，存在则覆盖）。 */
    @PostMapping
    public ResponseEntity<?> importCard(@RequestBody CharacterProfile profile) {
        if (profile.getId() == null || profile.getId().isBlank()) {
            return ResponseEntity.badRequest().body("character id is required");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(characterRepository.save(profile));
    }
}
