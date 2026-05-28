# ForgeAgent API 设计文档

## 1. 设计目标

ForgeAgent 的 API 要满足三个要求：

1. 统一入口
2. 统一状态
3. 统一可回放

Web、CLI、REST 三个入口都应共享同一套任务对象、会话对象和轨迹对象。

---

## 2. 通用约定

### 2.1 统一响应结构

所有接口统一返回以下结构：

```json
{
  "success": true,
  "message": "ok",
  "data": {},
  "traceId": "trace_001",
  "taskId": "task_001"
}
```

失败时：

```json
{
  "success": false,
  "message": "测试失败",
  "error": {
    "code": "TEST_FAILED",
    "type": "COMPILATION_ERROR",
    "detail": "..."
  },
  "traceId": "trace_001",
  "taskId": "task_001"
}
```

### 2.2 状态枚举

任务状态建议统一为：

- `PENDING`
- `ANALYZING`
- `PLANNING`
- `EXECUTING`
- `TESTING`
- `RETRYING`
- `SUCCEEDED`
- `FAILED`
- `STOPPED`

### 2.3 轨迹事件类型

- `TASK_CREATED`
- `PROJECT_IMPORTED`
- `PROJECT_SUMMARY_READY`
- `SUBAGENT_STARTED`
- `SUBAGENT_FINISHED`
- `PLAN_CREATED`
- `TOOL_CALLED`
- `TOOL_FINISHED`
- `PATCH_APPLIED`
- `TEST_STARTED`
- `TEST_FINISHED`
- `RETRY_DECIDED`
- `SKILL_GENERATED`
- `TASK_COMPLETED`

---

## 3. 任务接口

### 3.1 导入项目

`POST /api/projects/import`

用途：

- 导入本地 Java 项目
- 生成项目摘要
- 初始化 ProjectContext

请求：

```json
{
  "projectPath": "/path/to/project",
  "name": "demo-spring-project"
}
```

返回：

```json
{
  "projectId": "proj_001",
  "summary": {
    "buildTool": "maven",
    "rootPackage": "com.example.demo",
    "testCommand": "mvn test"
  }
}
```

---

### 3.2 创建任务

`POST /api/tasks`

用途：

- 创建新的修复任务
- 启动主 Agent 流程

请求：

```json
{
  "projectId": "proj_001",
  "goal": "修复注册接口 email 为空时报 500",
  "mode": "AUTO"
}
```

返回：

```json
{
  "taskId": "task_001",
  "status": "PENDING"
}
```

---

### 3.3 查询任务列表

`GET /api/tasks`

用途：

- 查看历史任务
- 支持按状态、项目、关键字过滤

建议查询参数：

- `projectId`
- `status`
- `keyword`
- `page`
- `pageSize`

---

### 3.4 查询任务详情

`GET /api/tasks/{id}`

用途：

- 查看任务基础信息
- 查看当前状态
- 查看重试次数
- 查看结果摘要

---

### 3.5 重新执行任务

`POST /api/tasks/{id}/retry`

用途：

- 手动触发重试

---

### 3.6 停止任务

`POST /api/tasks/{id}/stop`

用途：

- 中断当前运行中的 Agent

---

## 4. 轨迹接口

### 4.1 获取轨迹

`GET /api/tasks/{id}/trajectory`

用途：

- 获取完整时间线
- 用于 Web 回放

返回内容建议包括：

- 事件类型
- 事件时间
- 事件主体
- 事件摘要
- 关联工具调用

---

### 4.2 获取 diff

`GET /api/tasks/{id}/diff`

用途：

- 查看最终补丁
- 查看每轮变更

---

### 4.3 获取日志

`GET /api/tasks/{id}/logs`

用途：

- 查看测试输出
- 查看模型错误
- 查看工具错误

---

## 5. 技能接口

### 5.1 查询技能列表

`GET /api/skills`

用途：

- 查看所有 Skill
- 支持按标签、触发词、项目类型筛选

---

### 5.2 查询技能详情

`GET /api/skills/{id}`

用途：

- 查看技能内容
- 查看生成来源
- 查看最近命中记录

---

### 5.3 生成技能

`POST /api/skills/generate`

用途：

- 从任务结果生成 Skill 草稿

请求：

```json
{
  "taskId": "task_001",
  "force": false
}
```

---

## 6. 项目接口

### 6.1 查询项目摘要

`GET /api/projects/{id}`

用途：

- 查看项目结构
- 查看构建命令
- 查看模块信息
- 查看测试入口

---

### 6.2 查询项目文件索引

`GET /api/projects/{id}/files`

用途：

- 返回项目文件树
- 支持前端搜索

---

## 7. 子代理接口

### 7.1 查询子代理状态

`GET /api/tasks/{id}/subagents`

用途：

- 查看当前任务下所有子代理
- 查看运行状态、开始时间、摘要结果

---

### 7.2 中断子代理

`POST /api/tasks/{id}/subagents/{subagentId}/stop`

用途：

- 单独中断某个子代理

---

## 8. 事件流接口

### 8.1 实时任务流

`GET /api/tasks/{id}/events`

建议使用 SSE 或 WebSocket。

用途：

- 实时推送任务状态
- 实时推送工具调用
- 实时推送测试输出
- 实时推送子代理状态

---

## 9. CLI 映射

CLI 命令建议直接映射到 REST 能力：

```bash
forge init /path/to/project
forge run "修复注册接口 email 为空时报 500"
forge tasks
forge show task-001
```

映射关系：

- `forge init` -> 项目导入接口
- `forge run` -> 创建任务接口
- `forge tasks` -> 任务列表接口
- `forge show` -> 任务详情 + 轨迹接口

---

## 10. 设计原则

1. 所有接口都必须可追踪
2. 所有失败都必须结构化
3. 所有任务状态都必须持久化
4. 所有关键动作都必须进入轨迹
5. 所有入口必须共享同一运行时

