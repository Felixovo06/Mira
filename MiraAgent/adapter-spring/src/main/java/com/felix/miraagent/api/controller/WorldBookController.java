package com.felix.miraagent.api.controller;

import com.felix.miraagent.style.StyleConstraint;
import com.felix.miraagent.style.StyleConstraintStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 世界书 API：多条目、可单独开关。一本全局世界书对所有角色生效，每条条目规定
 * 世界设定 / 语气 / 规则，启用的条目按 order 拼接注入。所有写操作立即生效，无需重启。
 */
@RestController
@RequestMapping("/api/worldbook")
public class WorldBookController {

    private final StyleConstraintStore store;

    public WorldBookController(StyleConstraintStore store) {
        this.store = store;
    }

    /** 全部条目（含禁用，按 order）。 */
    @GetMapping
    public List<StyleConstraint> list() {
        return store.list();
    }

    /** 新建或更新一条条目（无 id 视为新建）。 */
    @PostMapping
    public ResponseEntity<StyleConstraint> upsert(@RequestBody StyleConstraint entry) {
        return ResponseEntity.ok(store.upsert(entry));
    }

    /** 删除一条。 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return store.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** 开关一条（插拔式核心）。 */
    @PostMapping("/{id}/toggle")
    public ResponseEntity<StyleConstraint> toggle(@PathVariable String id,
                                                  @RequestParam boolean enabled) {
        return store.setEnabled(id, enabled)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
