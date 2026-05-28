# ForgeAgent 数据表设计文档

## 1. 设计目标

ForgeAgent 使用 SQLite 作为本地核心存储，覆盖：

- 项目
- 任务
- 会话
- 轨迹
- 子代理
- 技能
- 记忆
- 文件索引

设计原则：

1. 结构清晰
2. 方便查询
3. 方便回放
4. 方便检索
5. 方便扩展

---

## 2. 表结构总览

```text
projects
tasks
task_sessions
task_events
subagents
skills
skill_runs
memories
project_files
task_diffs
task_logs
```

---

## 3. projects 表

### 3.1 用途

存储导入的 Java 项目基础信息。

### 3.2 字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | TEXT | 项目 ID |
| name | TEXT | 项目名称 |
| path | TEXT | 项目路径 |
| build_tool | TEXT | 构建工具，maven / gradle |
| root_package | TEXT | 根包名 |
| test_command | TEXT | 默认测试命令 |
| summary | TEXT | 项目摘要 JSON |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

## 4. tasks 表

### 4.1 用途

存储一次任务的主记录。

### 4.2 字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | TEXT | 任务 ID |
| project_id | TEXT | 关联项目 ID |
| goal | TEXT | 用户任务目标 |
| status | TEXT | 当前状态 |
| mode | TEXT | 执行模式 |
| retry_count | INTEGER | 重试次数 |
| current_round | INTEGER | 当前轮次 |
| summary | TEXT | 最终摘要 JSON |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| finished_at | DATETIME | 完成时间 |

索引建议：

- `project_id`
- `status`
- `created_at`

---

## 5. task_sessions 表

### 5.1 用途

存储任务过程中的会话状态。

### 5.2 字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | TEXT | 会话 ID |
| task_id | TEXT | 关联任务 ID |
| project_id | TEXT | 关联项目 ID |
| conversation_state | TEXT | 会话状态 JSON |
| current_plan | TEXT | 当前计划 JSON |
| model_name | TEXT | 当前模型 |
| token_usage | TEXT | Token 使用统计 JSON |
| status | TEXT | 会话状态 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

## 6. task_events 表

### 6.1 用途

轨迹事件主表，记录任务全过程。

### 6.2 字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | INTEGER | 自增主键 |
| task_id | TEXT | 任务 ID |
| session_id | TEXT | 会话 ID |
| event_type | TEXT | 事件类型 |
| event_time | DATETIME | 事件时间 |
| title | TEXT | 事件标题 |
| content | TEXT | 事件内容 |
| payload | TEXT | 事件 JSON |
| round_no | INTEGER | 所属轮次 |
| subagent_id | TEXT | 关联子代理 ID |
| tool_name | TEXT | 关联工具名 |

索引建议：

- `task_id`
- `session_id`
- `event_type`
- `event_time`

---

## 7. subagents 表

### 7.1 用途

存储子代理运行信息。

### 7.2 字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | TEXT | 子代理 ID |
| task_id | TEXT | 所属任务 ID |
| parent_task_id | TEXT | 父任务 ID |
| role | TEXT | 子代理角色 |
| status | TEXT | 状态 |
| goal | TEXT | 子代理目标 |
| summary | TEXT | 子代理摘要 |
| toolset | TEXT | 允许工具集 |
| started_at | DATETIME | 开始时间 |
| finished_at | DATETIME | 结束时间 |
| token_usage | TEXT | Token 统计 JSON |

---

## 8. skills 表

### 8.1 用途

存储成功任务沉淀出的技能。

### 8.2 字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | TEXT | 技能 ID |
| name | TEXT | 技能名称 |
| description | TEXT | 技能描述 |
| triggers | TEXT | 触发词 JSON |
| actions | TEXT | 动作 JSON |
| verification | TEXT | 验证方式 JSON |
| source_task_id | TEXT | 来源任务 ID |
| hit_count | INTEGER | 命中次数 |
| last_hit_at | DATETIME | 最近命中时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引建议：

- `name`
- `source_task_id`

---

## 9. skill_runs 表

### 9.1 用途

记录技能被调用或命中的情况。

### 9.2 字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | INTEGER | 自增主键 |
| skill_id | TEXT | 技能 ID |
| task_id | TEXT | 任务 ID |
| matched_text | TEXT | 命中的文本 |
| result | TEXT | 命中结果摘要 |
| created_at | DATETIME | 创建时间 |

---

## 10. memories 表

### 10.1 用途

存储项目级、任务级、技能级记忆。

### 10.2 字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | TEXT | 记忆 ID |
| scope | TEXT | 作用域，project / task / skill |
| scope_id | TEXT | 作用域对象 ID |
| memory_type | TEXT | 记忆类型 |
| content | TEXT | 记忆内容 |
| source | TEXT | 来源说明 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

## 11. project_files 表

### 11.1 用途

存储项目文件索引，方便搜索和定位。

### 11.2 字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | INTEGER | 自增主键 |
| project_id | TEXT | 项目 ID |
| file_path | TEXT | 文件路径 |
| file_type | TEXT | 文件类型 |
| symbol_summary | TEXT | 符号摘要 |
| updated_at | DATETIME | 更新时间 |

建议建立 FTS 索引，用于文件名和摘要搜索。

---

## 12. task_diffs 表

### 12.1 用途

存储任务过程中的 diff 快照。

### 12.2 字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | INTEGER | 自增主键 |
| task_id | TEXT | 任务 ID |
| round_no | INTEGER | 轮次 |
| diff_text | TEXT | diff 内容 |
| created_at | DATETIME | 创建时间 |

---

## 13. task_logs 表

### 13.1 用途

存储测试输出、工具输出和运行日志摘要。

### 13.2 字段

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | INTEGER | 自增主键 |
| task_id | TEXT | 任务 ID |
| session_id | TEXT | 会话 ID |
| log_type | TEXT | 日志类型 |
| content | TEXT | 日志内容 |
| created_at | DATETIME | 创建时间 |

---

## 14. 关键关系

```text
projects 1 -> N tasks
tasks 1 -> 1 task_sessions
tasks 1 -> N task_events
tasks 1 -> N subagents
tasks 1 -> N task_diffs
tasks 1 -> N task_logs
tasks 1 -> N skill_runs
projects 1 -> N project_files
projects 1 -> N memories
tasks 1 -> N memories
skills 1 -> N skill_runs
skills 1 -> N memories
```

---

## 15. 设计建议

1. 所有 JSON 内容统一存为 TEXT，业务层序列化和反序列化
2. 所有轨迹相关表都要有 task_id 索引
3. 所有时间字段统一用 DATETIME 或 Unix 时间戳，不混用
4. 技能和记忆都要支持检索
5. 后续如果要扩展到多项目、多用户，只需在 task / project 层追加作用域字段

