package com.felix.miraagent.skill;

import lombok.Builder;
import lombok.Value;

/**
 * 对已有 skill 的修补。修补后仍是 Active（不单设 Improved 态），version+1。
 */
@Value
@Builder
public class SkillPatch {
    String skillId;
    String newDescription;   // nullable，不改则保留
    String newBody;          // nullable，整段替换正文
    String appendBody;       // nullable，追加到正文末尾（与 newBody 二选一）
    String note;             // 修补说明，进 history
    String sourceTraceId;    // nullable
    String sourceSessionId;  // nullable
}
