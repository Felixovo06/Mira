# ForgeAgent 产品需求文档（最终版）

## 1. 产品定位

ForgeAgent 是一个借鉴 Hermes Agent 底层架构的本地 AI Coding Agent，面向 Java / Spring Boot 工程提供自动修复、测试生成、工具调用、轨迹回放、技能沉淀、多入口会话和只读并行 Subagent 能力。

它不是聊天机器人，而是一个：

> Hermes 风格的 Java 工程 Agent 运行时

## 2. 产品目标

### 2.1 核心目标

在 2 周内完成一个高完成度、可演示、可复用的产品闭环：

1. 用户输入 Java 项目路径和自然语言任务
2. 系统分析项目结构
3. 并行子 Agent 进行只读搜索、测试分析、技能召回
4. 主 Agent 汇总计划
5. 调用工具修改代码
6. 运行测试
7. 失败后基于日志重试，最多 3 轮
8. 展示完整执行轨迹
9. 成功后自动生成可复用 Skill

### 2.2 产品价值

- 让 Java / Spring Boot 修复任务具备稳定可演示的自动化闭环
- 让用户能看见 Agent 的每一步决策、工具调用、失败与重试
- 让成功经验可沉淀为 Skill，形成可复用知识资产
- 让 Web、CLI、REST 共用同一套会话与运行时

## 3. 用户画像

### 3.1 目标用户

- Java / Spring Boot 开发者
- 实习生或初级工程师
- 需要快速修复接口、补测试、定位报错的人
- 想观察 Agent 如何工作、如何重试、如何沉淀经验的人

### 3.2 核心场景

- 修复 Controller / Service / Repository 中的空指针、校验失败、500 错误
- 根据失败日志补测试或修复测试
- 对一个本地项目做结构化分析和自动摘要
- 回放一次任务的完整执行过程
- 将一个成功修复模式转成 Skill

## 4. 设计原则

1. 本地优先，能离线就离线
2. 统一运行时，多入口共享状态
3. 子代理只读，主代理统一写入
4. 每次行动都可回放、可解释、可追踪
5. 成功经验必须沉淀，不只完成单次任务
6. 安全默认拒绝危险命令
7. 产品面向 Java 工程，不做通用聊天壳

## 5. 范围定义

### 5.1 P0 范围

#### 5.1.1 多入口网关

支持以下入口共享同一套运行时：

- Web Dashboard
- REST API
- CLI

统一共享：

- TaskSession
- ProjectContext
- ConversationState
- TrajectoryStream

CLI 最小命令：

```bash
forge init /path/to/project
forge run "修复注册接口 email 为空时报 500"
forge tasks
forge show task-001
```

#### 5.1.2 Java 项目分析

识别并抽取：

- `pom.xml` / `build.gradle`
- `src/main/java`
- `src/test/java`
- `application.yml` / `application.yaml`
- `Controller` / `Service` / `Repository` / `DTO` / `Test`

输出项目摘要，包括：

- 构建方式
- 模块结构
- 入口类
- 主要分层
- 关键测试位置
- 项目内可用的测试命令

#### 5.1.3 只读并行子代理

P0 子 Agent：

- CodeSearchAgent：定位相关文件
- TestAnalysisAgent：分析测试入口和失败日志
- SkillRecallAgent：检索历史 Skill

规则：

- 子 Agent 只读
- 子 Agent 不直接改文件
- 子 Agent 不可发起二次写入
- 主 Agent 统一生成最终 patch

#### 5.1.4 Agent 循环

固定循环：

`Plan -> Act -> Observe -> Reflect -> Retry -> Record`

约束：

- 最多重试 3 轮
- 每轮必须记录计划、工具调用、测试结果、重试原因
- 失败必须分类，不能只写“失败了”

#### 5.1.5 工具运行时

P0 工具：

- `read_file`
- `search_files`
- `list_files`
- `apply_patch`
- `git_diff`
- `maven_test`
- `gradle_test`
- `limited_shell`

要求：

- 必须通过 ToolRegistry 注册
- 必须归属 Toolset
- 必须支持可用性检查
- 必须支持危险命令拦截
- 工具失败要返回结构化错误，不允许直接把异常抛给模型

#### 5.1.6 测试驱动修复

支持：

- `mvn test`
- `./gradlew test`

可解析失败类型：

- 编译失败
- 单测失败
- 断言失败
- `NullPointerException`
- Spring Bean 注入失败
- 参数校验失败

#### 5.1.7 轨迹回放

记录并展示：

- 用户目标
- 项目摘要
- Agent 计划
- 子 Agent 结果
- 工具调用
- 文件 diff
- 测试命令
- 测试输出
- 重试原因
- 最终总结
- 生成的 Skill

Dashboard 必须能按时间线展示完整执行过程。

#### 5.1.8 技能沉淀

成功任务自动生成 Skill，内容至少包含：

- 名称
- 触发条件
- 适用场景
- 修复步骤
- 验证步骤
- 典型失败模式

#### 5.1.9 记忆系统

P0 三类记忆：

- ProjectMemory：项目结构、构建命令、模块信息
- TaskMemory：历史任务、结果、失败原因、最终 diff
- SkillMemory：可复用修复模式

不做完整用户画像，不做大而全长期人格记忆。

#### 5.1.10 安全策略

允许：

- `mvn test`
- `gradle test`
- `git diff`
- `rg`
- `ls`
- `pwd`

拒绝：

- `rm -rf`
- `sudo`
- `cat ~/.ssh/*`
- `cat .env`
- `printenv`

危险命令必须直接拒绝，并记录到轨迹。

### 5.2 P1 范围

- Markdown 报告导出
- 模型 token / cost 统计
- Skill 命中可视化
- JUnit 测试生成专项模式
- 任务历史搜索

### 5.3 不做范围

- VS Code 插件
- Telegram / Discord 网关
- 复杂云端部署
- 用户登录系统
- 完整 MCP 兼容
- 子 Agent 自动改文件
- 长期定时任务

## 6. 核心对象模型

### 6.1 ProjectContext

表示项目的静态上下文：

- 项目根目录
- 构建工具
- 模块列表
- 入口类
- 测试命令
- 目录索引

### 6.2 TaskSession

表示一次任务执行实例：

- sessionId
- projectId
- 用户输入
- 当前状态
- 重试次数
- 当前计划
- 轨迹引用

### 6.3 ConversationState

表示会话消息流：

- 用户消息
- Agent 消息
- 工具消息
- 子 Agent 摘要

### 6.4 Trajectory

表示可回放的完整执行记录：

- 计划
- 工具调用
- diff
- 测试输出
- 决策点
- 最终结果

### 6.5 Skill

表示从成功任务中抽象出的修复模板：

- name
- description
- triggers
- actions
- verification
- examples

## 7. 系统架构

### 7.1 总体架构

```text
用户
  -> Web / CLI / REST
  -> Gateway
  -> TaskSession
  -> Main Agent
  -> Subagents（只读并行）
  -> Tool Runtime
  -> 测试与补丁执行
  -> Trajectory / Memory / Skill
```

### 7.2 模块划分

```text
forge-agent
├── server
│   ├── gateway
│   ├── agent-core
│   ├── subagents
│   ├── tool-runtime
│   ├── java-plugin
│   ├── memory-engine
│   ├── skill-engine
│   ├── trajectory
│   ├── sandbox
│   └── model-adapter
├── web
├── cli
└── demo-spring-project
```

### 7.3 运行时原则

- 所有入口共用同一套会话与轨迹存储
- 主 Agent 负责决策
- 子 Agent 负责信息补全
- 工具层负责执行
- 轨迹层负责记录
- 记忆层负责沉淀

## 8. Agent 行为规范

### 8.1 主 Agent 职责

- 理解任务
- 汇总子 Agent 结论
- 制定修复计划
- 选择工具
- 生成 patch
- 发起测试
- 分析失败日志
- 决定是否重试
- 输出最终总结

### 8.2 子 Agent 职责

- 只读分析
- 局部搜索
- 测试日志归因
- Skill 召回
- 返回结构化摘要

### 8.3 子 Agent 限制

- 不改文件
- 不运行写操作
- 不修改记忆
- 不直接影响主流程状态
- 不允许无限递归

### 8.4 重试策略

最多 3 轮：

1. 第 1 轮定位问题并尝试修复
2. 第 2 轮根据测试失败日志收缩范围
3. 第 3 轮做最小修补并收尾

超过 3 轮后停止，输出失败总结与阻塞原因。

## 9. 工具运行时设计

### 9.1 工具注册

所有工具都必须显式注册到 ToolRegistry，并归属于 Toolset。

### 9.2 工具分层

- 文件工具：读、搜、列、补丁、diff
- 测试工具：Maven、Gradle
- 命令工具：受限 shell
- 安全工具：命令白名单、危险命令检测

### 9.3 工具执行约束

- 子 Agent 默认只暴露只读工具
- 写文件必须由主 Agent 执行
- 危险命令必须拒绝
- 工具返回必须结构化

## 10. 记忆系统

### 10.1 记忆类型

- ProjectMemory：项目级事实
- TaskMemory：任务级结论
- SkillMemory：模式级复用知识

### 10.2 记忆原则

- 只记可复用内容
- 只记稳定事实
- 只记对后续任务有帮助的结论
- 不记一次性噪音

### 10.3 记忆写入时机

- 项目导入完成后写 ProjectMemory
- 任务成功或失败后写 TaskMemory
- 成功模式稳定后写 SkillMemory

## 11. 技能系统

### 11.1 生成条件

只有当任务成功且模式具备复用价值时，才生成 Skill。

### 11.2 Skill 结构

```yaml
name: spring-validation-null-check
description: 修复 Spring Boot 中空输入导致的 500 错误
triggers:
  - NullPointerException
  - Controller
  - RequestBody
actions:
  - 检查 Controller 入参
  - 增加参数校验
  - 补充测试
  - 重新运行 mvn test
verification:
  - 测试通过
  - 不再出现 500
  - 失败日志消失
```

### 11.3 Skill 质量要求

- 可触发
- 可执行
- 可验证
- 有失败边界
- 不写空泛描述

## 12. 轨迹回放

### 12.1 必须记录的字段

- sessionId
- taskGoal
- projectSummary
- agentPlan
- subagentSummaries
- toolCalls
- diff
- testCommands
- testOutput
- retryReasons
- finalSummary
- generatedSkills
- tokenUsage
- costEstimate
- timestamps

### 12.2 展示要求

Web Dashboard 至少展示：

- 时间线
- 计划与执行对照
- diff 预览
- 测试输出
- 重试链路
- 最终结果

## 13. REST API 设计草案

### 13.1 任务接口

- `POST /api/projects/import`
- `POST /api/tasks`
- `GET /api/tasks`
- `GET /api/tasks/{id}`
- `POST /api/tasks/{id}/retry`
- `POST /api/tasks/{id}/stop`

### 13.2 轨迹接口

- `GET /api/tasks/{id}/trajectory`
- `GET /api/tasks/{id}/diff`
- `GET /api/tasks/{id}/logs`

### 13.3 技能接口

- `GET /api/skills`
- `GET /api/skills/{id}`
- `POST /api/skills/generate`

## 14. 数据存储

### 14.1 SQLite

用于存储：

- 项目
- 任务
- 会话
- 轨迹索引
- Skill 元数据
- 任务记忆

### 14.2 检索

优先使用：

- SQLite FTS
- 结构化查询

必要时可加 Lucene，但 P0 不依赖它。

## 15. 前端页面

### 15.1 Web 仪表盘首屏

首屏必须能直接执行任务，不做营销页。

页面元素：

- 项目路径输入
- 任务输入框
- 最近任务列表
- 当前任务状态
- 运行中轨迹流

### 15.2 任务详情页

展示：

- 项目摘要
- 当前计划
- 子 Agent 结果
- 工具调用
- diff
- 测试输出
- Skill 生成结果

## 16. 安全策略

### 16.1 默认拒绝

以下操作默认拒绝：

- 删除系统文件
- 读取敏感密钥
- 输出环境变量
- 提权命令
- 危险 shell 管道

### 16.2 审计要求

所有拒绝都要进入轨迹，并说明拒绝原因。

### 16.3 文件保护

默认保护：

- `.env`
- SSH 密钥目录
- 系统配置目录
- 用户主目录敏感文件

## 17. 验收标准

以下能力必须可演示：

1. 能通过 Web / CLI 创建任务
2. 能导入 demo Spring Boot 项目
3. 能完成至少 2 个自动修复 demo
4. 能展示 Subagent 并行分析结果
5. 能运行 `mvn test` 并基于失败日志重试
6. 能展示 diff、timeline、测试输出
7. 能生成至少 1 个 Skill
8. 能拦截危险命令
9. README 能讲清楚整体架构

## 18. 里程碑

### 第 1 阶段

- 统一对象模型
- 任务创建与会话流
- 项目分析
- 基础工具层

### 第 2 阶段

- 主 Agent Loop
- 子 Agent 并行分析
- 测试失败解析
- 自动 patch 与重试

### 第 3 阶段

- Trajectory 回放
- Skill 沉淀
- Memory 写入
- Web Dashboard 展示

## 19. 成功定义

ForgeAgent 成功的标准不是“能回答问题”，而是：

- 能稳定修复 Java / Spring Boot 问题
- 能清楚解释自己为什么这么修
- 能把成功模式沉淀成 Skill
- 能让下一次任务更快、更准、更可追踪

这才是它作为 Hermes-inspired Java Engineering Agent Runtime 的核心价值。
