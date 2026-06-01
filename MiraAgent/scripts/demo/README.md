# MiraAgent Demo

5 分钟跑完一条完整链路：**学习搭子「小研」陪用户规划复习**，逐步展示角色卡 → 长期记忆写入 → 工具调用 → 跨轮召回 → 自我改善（Background Review 沉淀 skill）。

## 跑法

```bash
# 1) 起服务（需真实模型 + DB + embedding，profile=local）
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw install -DskipTests
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw spring-boot:run -pl adapter-spring -Dspring-boot.run.profiles=local

# 2) 另开一个终端跑 Demo（需 curl + jq）
./scripts/demo/run-demo.sh
```

可用环境变量：`MIRA_BASE`（默认 http://localhost:8080）、`MIRA_USER`、`MIRA_CHARACTER`（默认 study-buddy）。

## Demo 步骤与对应数据变化

| 步骤 | 动作 | 可观察的数据/trace |
|---|---|---|
| 0 | 健康检查 | `/api/health` |
| 1 | 列出角色卡 | 出现内置 `study-buddy` / `mira` |
| 2 | 告知课程与目标 | `write_memory` → `/api/memory` 出现新条目 |
| 3 | 请求规划复习 | 调用 `todo`/`calculator` → `/api/tool-executions/runs/{runId}` |
| 4 | "继续上次那个计划" | session + 记忆召回（`recall_memory`） |
| 5 | 查看 trace | `/api/traces/{runId}` 事件序列 |
| 6 | 自我改善 | `/api/skills` 出现/更新规划 skill，`/api/skills/curator-report` |

## sample-data/

可直接落盘的示例数据（匹配真实磁盘格式）：

- `memory/demo-user/USER.md`、`PREFERENCES.md` — 示例长期记忆（含 `<!-- memory-id ... -->` 头）
- `skills/study-planning/SKILL.md` — 示例 skill（YAML frontmatter + 步骤/工具建议/检查清单）

把它们拷到记忆/skill 的 base-dir（默认 `~/.miraagent/memory`、`~/.miraagent/skills`）即可作为预置数据；配置了 DB 时索引会自动重建。

内置示例角色卡见 `adapter-spring/src/main/resources/characters/*.json`，随应用加载，也可经 `POST /api/characters` 导入自定义卡。
