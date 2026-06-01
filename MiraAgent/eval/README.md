# MiraAgent 评测体系（eval）

把运行中的 Agent 当**黑盒**，跑一组 golden 用例，输出**可量化、可重复**的报告。
评测只依赖系统的公开 API（`/api/chat/stream`），不侵入内部实现——所以它评的是"用户真实拿到的东西"。

## 四层模型

| 层 | 内容 | 现状 |
|----|------|------|
| L1 单元级 | 工具选择/参数/no-tool 准确率、工具&MCP 执行成功率 | ✅（黑盒 eval 模块）|
| L1 记忆 | 检索 Precision / Recall / Top-1 | ✅（`MemoryRetrievalEvalTest`，组件级，按需）|
| L2 链路级 | E2E 成功率 / 工具链完成率 / TTFT / 延迟 / token·turn | ✅ |
| L3 质量级 | LLM-as-Judge：相关性/角色一致/工具使用（3× 取中位数；可加 Kappa）| ✅（可选）|
| L4 回归级 | baseline diff，精确到"哪条指标变了多少"（容差）| ✅ |
| 自我改善 | 后台复盘触发准确率（黑盒经 trace 观测异步复盘）| ✅ |

四层全落地。记忆 P·R 因依赖真实 embedding+PG，作为组件级 gated 测试单独跑（见下）。

## 跑法

```bash
# 1) 先启动后端（:8080）。要评联网搜索就开 Exa MCP：
#    SPRING_APPLICATION_JSON='{"mira":{"mcp":{"enabled":true,"servers":[...exa...]}}}'
# 2) 跑评测
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -q -pl eval -am compile \
  exec:java -Dexec.args="--base-url=http://localhost:8080" -Deval.out=eval-report.json
```

输出：控制台 summary + `eval-report.json`（每条 case 明细 + 各层汇总）。
用例集在 `src/main/resources/eval/cases.json`（`-Deval.cases=` 可换）。

### 开 L3（LLM-as-Judge，可选）

判官走独立的 OpenAI 兼容端点，**不配凭据则自动跳过**（保持 eval 与主链路解耦，判官也不复用被测模型客户端）：

```bash
-Deval.judge.baseUrl=https://api.xiaomimimo.com/v1 \
-Deval.judge.apiKey=$KEY -Deval.judge.model=mimo-v2.5 -Deval.judge.samples=3
```

### 开 L4（回归对比，可选）

```bash
-Deval.baseline=上次的eval-report.json -Deval.tolerance=0.05
```

报告里多出 `diff`：超容差的指标分到 `regressions`/`improvements`（延迟/token 越低越好，其余越高越好）。

> CI 建议：L1 工具准确率/L3/L4 因依赖真实模型→**按需/每日**跑；`EvalLogicTest`（diff/median 纯逻辑）可进每次 push。

### 记忆检索 P·R（组件级，按需）

依赖真实 embedding(DashScope) + 云 PG，故为 gated 测试，不在黑盒 eval 模块内（避免耦合）：

```bash
JAVA_HOME=... ./mvnw -pl adapter-spring test -Dmira.it.postgres=true \
  -Dtest=MemoryRetrievalEvalTest -Dsurefire.failIfNoSpecifiedTests=false
```

播种带标注的记忆池→跑混合检索→算 P/R/Top-1（目标 P≥0.80 / R≥0.75 / Top-1≥0.85）。

> 实测发现：在"短句 + 共同前缀（都以'用户…'开头）"的难集上 Top-1 仅 0.33——
> 根因一是短文本 embedding 区分度低，二是 `rerank()` 阶段只按 category/recency/character 排序、
> **丢弃了查询相关性信号**，无法挽救弱排序。这是评测暴露的真实可优化点（非崩溃，是质量短板）。

## 指标定义（关键设计取舍）

- **工具选择准确率**：给定 user message，模型是否调用了期望工具。客观，但**依赖真实模型**→ 非确定性、有 API 成本，故归"按需跑"，不进每次 push 的 CI。
- **参数准确率**：仅在选对工具后评；期望参数值对实参 JSON 做**子串匹配**（容忍格式差异）。
- **no-tool 率**：工具可用但不该用时（如闲聊）模型是否克制——防"工具滥用"。
- **TTFT**：从 SSE 第一个 `text_delta` 计时，反映"用户多久看到第一个字"。
- **token·turn**：跨所有模型轮次（含工具轮）累加，用于成本监控。

## 非确定性怎么处理（面试常问）

真实 LLM 每次输出不同，评测必须正视：
1. 用**容差/子串匹配**而非精确文本对比；
2. 质量层用 **3× Judge 取中位数 + 与人工标注做 Kappa 校验**；
3. 钉死模型版本、低温采样、必要时多次采样取聚合；
4. CI 只跑**确定性**部分（失败场景用 FakeModelClient、记忆 P/R 用缓存向量），模型相关指标按需/每日跑小固定集。

## 面试话术速答

| 问题 | 回答方向 |
|------|---------|
| 怎么评工具调用质量？ | L1 工具选择/参数准确率，结构化 case 比对期望，黑盒跑真实 Agent |
| 怎么知道一次改动变好还是变坏？ | L4 golden + baseline diff（容差对比），精确到单条 case 的分数 delta |
| LLM-as-Judge 可信吗？ | 3× 取中位数 + 与人工标注 Kappa 校验 + Judge prompt 显式给评分标准 |
| 做了失败场景吗？ | L2 覆盖流式中断、工具异常转 error 不穿透、MCP 超时降级、长回复分块 |
| 评测发现过真问题吗？ | 有：首版 L2 跑出 `avg_tokens_per_turn=0`，定位到 `RunResult.usage` 未聚合 token，已修（见 ConversationLoop usage 累加） |

> 最后一行是这套评测最好的背书——它**真的抓到了一个 token 用量没透出的缺陷**。
