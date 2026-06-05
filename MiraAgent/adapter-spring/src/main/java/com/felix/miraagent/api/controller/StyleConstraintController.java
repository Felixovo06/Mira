package com.felix.miraagent.api.controller;

import com.felix.miraagent.style.StyleConstraint;
import com.felix.miraagent.style.StyleConstraintStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 旧版「单份风格约束」API 兼容垫片。底层已升级为多条目世界书，此处只读写第一条，
 * 供未迁移的前端继续工作。新前端请用 {@code /api/worldbook}。
 */
@RestController
@RequestMapping("/api/style")
public class StyleConstraintController {

    private final StyleConstraintStore store;

    public StyleConstraintController(StyleConstraintStore store) {
        this.store = store;
    }

    /** 世界书第一条；为空时返回一个启用的空壳供前端编辑。 */
    @GetMapping
    public ResponseEntity<StyleConstraint> get() {
        return ResponseEntity.ok(store.list().stream().findFirst()
                .orElseGet(() -> StyleConstraint.builder().enabled(true).build()));
    }

    /** 保存（upsert）该条目，立即生效。 */
    @PutMapping
    public ResponseEntity<StyleConstraint> save(@RequestBody StyleConstraint constraint) {
        return ResponseEntity.ok(store.upsert(constraint));
    }
}
