# ForgeAgent 架构文档

## 1. 模块划分

ForgeAgent 采用“前后端分离 + 核心运行时独立”的结构，核心目标是让 Web、CLI、REST 共用同一套任务运行时与轨迹系统。

### 1.1 总体结构

```text
forge-agent
├── server
│   ├── gateway
│   ├── agent-core
│   ├── subagents
│   ├── tool-runtime
│   ├── java-analyzer
│   ├── memory-engine
│   ├── skill-engine
│   ├── trajectory
│   ├── sandbox
│   └── model-adapter
├── web
├── cli
└── demo-spring-project
```

### 1.2 server/gateway

职责：

- 对外提供 Web、REST、CLI 统一入口
- 创建和管理 TaskSession
- 路由任务到 Agent Core
- 统一输出轨迹流、任务状态、结果摘要

边界：

- 不直接执行业务推理
- 不直接做文件修复
- 不持有业务规则

### 1.3 server/agent-core

职责：

- 主 Agent 的核心循环
- 任务规划、执行、观察、反思、重试
- 汇总子 Agent 结果
- 决定是否继续重试
- 生成最终总结与 Skill 草稿

### 1.4 server/subagents

职责：

- 管理只读并行子代理
- 提供 CodeSearchAgent、TestAnalysisAgent、SkillRecallAgent
- 控制并发数、预算、超时、隔离范围

规则：

- 子代理只读
- 子代理不改文件
- 子代理不写记忆
- 子代理不允许递归生成子代理

### 1.5 server/tool-runtime

职责：

- 工具注册
- 工具分组
- 工具可用性检查
- 工具执行
- 危险命令拦截
- 工具结果标准化

工具分组建议：

- 文件工具：`read_file`、`search_files`、`list_files`、`apply_patch`、`git_diff`
- 构建工具：`maven_test`、`gradle_test`
- 命令工具：`limited_shell`
- 安全工具：命令白名单、敏感文件保护

### 1.6 server/java-analyzer

职责：

- 识别 Java / Spring Boot 项目结构
- 提取模块、入口、测试、配置文件
- 生成项目摘要
- 定位 Controller / Service / Repository / DTO / Test

### 1.7 server/memory-engine

职责：

- 管理 ProjectMemory、TaskMemory、SkillMemory
- 维护可复用事实与任务经验
- 控制写入时机与容量

### 1.8 server/skill-engine

职责：

- 从成功任务中生成 Skill
- 维护 Skill 元信息
- 支持技能检索、触发、复用

### 1.9 server/trajectory

职责：

- 记录任务全过程
- 记录计划、工具调用、diff、测试输出、重试原因
- 支持回放、检索、导出

### 1.10 server/sandbox

职责：

- 约束 shell 执行边界
- 管理危险命令拒绝
- 管理文件访问保护

### 1.11 server/model-adapter

职责：

- 适配 OpenAI-compatible 模型接口
- 统一工具调用格式
- 统一结构化输出格式
- 屏蔽不同模型供应商差异

### 1.12 web

职责：

- 任务创建
- 项目导入
- 任务详情
- 轨迹回放
- diff 查看
- 测试日志查看
- Skill 查看

### 1.13 cli

职责：

- 快速启动任务
- 查看任务列表
- 查看任务详情
- 适合本地开发与调试

### 1.14 demo-spring-project

职责：

- 提供标准演示项目
- 用于验证自动修复、测试生成、重试闭环

---

## 2. 代码规范

### 2.1 总体原则

- 以可读性优先
- 以模块边界优先
- 以稳定接口优先
- 以可回放和可测试优先
- 不做过度抽象

### 2.2 命名规范

- 类名使用大驼峰
- 方法名和变量名使用小驼峰
- 常量使用全大写加下划线
- 包名使用全小写、按职责分层
- 工具名、任务名、技能名保持稳定、可检索

示例：

- `TaskSession`
- `ProjectContext`
- `TrajectoryRecorder`
- `applyPatch`
- `MAX_RETRY_ROUNDS`

### 2.3 分层规范

推荐分层：

1. 接口层：Web、CLI、REST Controller
2. 应用层：任务编排、用例流程
3. 领域层：任务、项目、轨迹、技能、记忆
4. 基础设施层：数据库、文件系统、模型调用、外部工具

禁止：

- Controller 直接写核心业务逻辑
- 工具层直接依赖 UI 层
- 子代理直接修改主会话状态

### 2.4 接口与返回规范

- 所有外部接口返回统一结构
- 所有工具返回结构化结果
- 所有失败必须带错误类型、错误消息、上下文摘要
- 不允许只返回模糊字符串

推荐字段：

- `success`
- `data`
- `error`
- `message`
- `traceId`
- `taskId`

### 2.5 日志规范

- 关键流程必须打日志
- 日志要能串起任务全流程
- 每次重试必须有原因日志
- 危险命令拒绝必须记录
- 子代理结果必须可追溯

要求：

- 日志简洁
- 不泄露敏感文件内容
- 不打印密钥和环境变量

### 2.6 错误处理规范

- 所有失败统一转成可读错误对象
- 工具失败不直接抛到最上层
- 模型调用失败要区分限流、认证失败、服务错误、超时
- 测试失败要保留原始日志片段和归因结果

### 2.7 测试规范

- 每个核心模块都要有单测
- Agent 循环要有流程测试
- 工具层要有行为测试
- 子代理调度要有并发测试
- 轨迹格式要有回放测试
- 安全策略要有拒绝测试

### 2.8 文档规范

- 所有面向团队的文档统一使用中文
- 接口名、代码标识、命令行参数保留英文
- 文档中的术语首次出现时给出中文解释
- 设计文档要可直接转开发任务

### 2.9 提交规范

- 一次提交只解决一个明确问题
- 提交信息包含模块与结果
- 不混入无关格式化修改
- 影响核心流程时必须补测试

---

## 3. 技术选型

### 3.1 后端

**Java 21 + Spring Boot 3**

选择原因：

- 适合做长期运行的本地服务
- 生态成熟，适合工程化落地
- 对 Spring Boot 项目的分析和修复天然贴近
- 便于后续扩展 REST、WebSocket、任务调度、持久化

### 3.2 模型接入层

**OpenAI-compatible API 为主，模型适配层独立封装**

选择原因：

- 接入成本低
- 可兼容多家模型供应商
- 便于统一工具调用协议
- 便于后续替换模型而不改主流程

可选方案：

- 直接使用 Spring AI 作为模型接入与工具调用封装层
- ForgeAgent 自己保留 Agent Core、任务循环、轨迹、子代理和安全层

### 3.3 前端

**React + Vite + TypeScript + Tailwind**

选择原因：

- 适合快速搭建任务型仪表盘
- TypeScript 方便维护复杂状态
- Vite 启动快，适合本地开发
- Tailwind 适合快速构建密度较高的操作界面

### 3.4 存储

**SQLite**

选择原因：

- 本地部署简单
- 适合单机任务系统
- 支持事务、索引、FTS
- 足以支撑任务、会话、轨迹、技能、记忆

检索建议：

- 优先 SQLite FTS
- 规模上来后可引入 Lucene 作为补充

### 3.5 工程构建

**Maven**

选择原因：

- Java 生态默认适配度高
- 依赖管理和多模块组织成熟
- 适合 Spring Boot 项目

### 3.6 代码分析与文件工具

**JGit + ripgrep**

选择原因：

- JGit 便于读取、diff、版本操作
- ripgrep 适合快速搜索代码
- 二者都适合本地工程任务

### 3.7 测试执行

**Maven Test + Gradle Test**

选择原因：

- 覆盖主流 Java 项目
- 与实际 Spring Boot 项目贴合
- 便于把失败日志直接反馈给 Agent

### 3.8 沙箱与安全

**受限 Shell + 命令白名单 + 敏感文件保护**

选择原因：

- 降低误操作风险
- 适合本地修复场景
- 方便记录安全审计轨迹

### 3.9 轨迹与日志

**结构化 JSON + 时间线视图**

选择原因：

- 便于回放
- 便于前端渲染
- 便于后续做统计和检索

### 3.10 可选增强

- Spring AI：用于补充模型接入、工具调用、记忆封装
- Lucene：用于增强任务历史检索
- WebSocket / SSE：用于轨迹流式展示

---

## 4. 结论

ForgeAgent 的核心不是“堆技术”，而是把 Java 工程修复这件事做成一个闭环：

- 能看懂项目
- 能并行分析
- 能安全改动
- 能验证结果
- 能回放过程
- 能沉淀经验

技术选型上，优先保证本地可用、工程可控、状态统一、轨迹可追踪。

