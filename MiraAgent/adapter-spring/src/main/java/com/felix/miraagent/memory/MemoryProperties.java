package com.felix.miraagent.memory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {
    private String baseDir = System.getProperty("user.home") + "/.miraagent/memory";

    private Dedup dedup = new Dedup();

    /** 写入前去重阈值（pg_trgm similarity，0-1）。 */
    @Data
    public static class Dedup {
        /** 关闭时所有写入直通，不做去重。 */
        private boolean enabled = true;
        /** [near, exact) 视为近似：更新既有卡片。 */
        private double nearThreshold = 0.6;
        /** >= exact 视为完全重复：仅强化既有、不新增卡片。 */
        private double exactThreshold = 0.82;
    }
}
