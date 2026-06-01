package com.felix.miraagent.api.controller;

import com.felix.miraagent.style.StyleConstraint;
import com.felix.miraagent.style.StyleConstraintStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 全局风格约束 API：读取 / 保存。一份全局配置对所有角色生效，
 * 规定世界设定与回复语气/风格规则。保存后立即生效，无需重启。
 */
@RestController
@RequestMapping("/api/style")
public class StyleConstraintController {

    private final StyleConstraintStore store;

    public StyleConstraintController(StyleConstraintStore store) {
        this.store = store;
    }

    /** 当前生效的风格约束；未配置时返回一个启用的空壳供前端编辑。 */
    @GetMapping
    public ResponseEntity<StyleConstraint> get() {
        return ResponseEntity.ok(store.current()
                .orElseGet(() -> StyleConstraint.builder().enabled(true).build()));
    }

    /** 保存风格约束（整体覆盖），立即生效。 */
    @PutMapping
    public ResponseEntity<StyleConstraint> save(@RequestBody StyleConstraint constraint) {
        return ResponseEntity.ok(store.save(constraint));
    }
}
