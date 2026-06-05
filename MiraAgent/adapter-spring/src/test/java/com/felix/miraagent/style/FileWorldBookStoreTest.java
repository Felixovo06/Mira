package com.felix.miraagent.style;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileWorldBookStoreTest {

    private final ObjectMapper om = new ObjectMapper();

    private StyleConstraintProperties props(Path dir, boolean enabled, String legacy) {
        var p = new StyleConstraintProperties();
        p.setEnabled(enabled);
        p.setFile(dir.resolve("worldbook.json").toString());
        p.setLegacyFile(legacy);
        return p;
    }

    @Test
    void upsertAssignsIdAndPersists(@TempDir Path dir) {
        var store = new FileWorldBookStore(props(dir, true, ""), om);
        var saved = store.upsert(StyleConstraint.builder().name("毒舌语气").tone("阴阳怪气").build());

        assertNotNull(saved.getId());
        assertTrue(Files.exists(dir.resolve("worldbook.json")));

        // 新 store 从盘加载应能读回
        var reloaded = new FileWorldBookStore(props(dir, true, ""), om);
        assertTrue(reloaded.get(saved.getId()).isPresent());
    }

    @Test
    void toggleGatesActiveEntries(@TempDir Path dir) {
        var store = new FileWorldBookStore(props(dir, true, ""), om);
        var a = store.upsert(StyleConstraint.builder().name("A").worldSetting("WA").build());
        assertTrue(store.activeEntries().stream().anyMatch(e -> e.getId().equals(a.getId())));

        store.setEnabled(a.getId(), false);
        assertTrue(store.activeEntries().stream().noneMatch(e -> e.getId().equals(a.getId())),
                "关闭的条目不应出现在 activeEntries");
        // list() 仍包含禁用条目
        assertTrue(store.list().stream().anyMatch(e -> e.getId().equals(a.getId())));
    }

    @Test
    void activeEntriesSortedByOrder(@TempDir Path dir) {
        var store = new FileWorldBookStore(props(dir, true, ""), om);
        store.upsert(StyleConstraint.builder().name("late").order(50).worldSetting("L").build());
        store.upsert(StyleConstraint.builder().name("early").order(1).worldSetting("E").build());

        var active = store.activeEntries();
        int iEarly = indexOfName(active, "early");
        int iLate = indexOfName(active, "late");
        assertTrue(iEarly >= 0 && iLate >= 0 && iEarly < iLate, "应按 order 升序");
    }

    @Test
    void deleteRemoves(@TempDir Path dir) {
        var store = new FileWorldBookStore(props(dir, true, ""), om);
        var a = store.upsert(StyleConstraint.builder().name("X").build());
        assertTrue(store.delete(a.getId()));
        assertFalse(store.delete(a.getId()), "重复删除返回 false");
        assertTrue(store.get(a.getId()).isEmpty());
    }

    @Test
    void subsystemDisabledYieldsNoActiveEntries(@TempDir Path dir) {
        // 先用启用的 store 写入一条
        new FileWorldBookStore(props(dir, true, ""), om)
                .upsert(StyleConstraint.builder().name("A").worldSetting("W").build());
        // 总开关关闭
        var off = new FileWorldBookStore(props(dir, false, ""), om);
        assertTrue(off.activeEntries().isEmpty(), "总开关关闭时不注入任何条目");
        assertFalse(off.list().isEmpty(), "list 不受总开关影响");
    }

    @Test
    void migratesLegacySingleFile(@TempDir Path dir) throws Exception {
        Path legacy = dir.resolve("style.json");
        Files.writeString(legacy, """
                {"enabled":true,"worldSetting":"LEGACY_WORLD","tone":"旧语气","styleRules":["r1"]}
                """);
        var store = new FileWorldBookStore(props(dir, true, legacy.toString()), om);

        var all = store.list();
        assertEquals(1, all.size());
        assertEquals("LEGACY_WORLD", all.get(0).getWorldSetting());
        assertEquals("默认风格", all.get(0).getName());
        assertNotNull(all.get(0).getId());
        assertTrue(Files.exists(dir.resolve("worldbook.json")), "迁移后应落盘新文件");
    }

    private int indexOfName(List<StyleConstraint> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (name.equals(list.get(i).getName())) return i;
        }
        return -1;
    }
}
