# MiraAgent 评测体系（eval）

把运行中的 Agent 当**黑盒**，跑一组 golden 用例，输出**可量化、可重复**的报告。
评测只依赖系统的公开 API（`/api/chat/stream`），不侵入内部实现——所以它评的是"用户真实拿到的东西"。

## 四层模型

| 层 | 内容 | 客观性/速度 | 现状 |
|----|------|------------|------|
| L1 单元级 | 工具选择准确率 / 参数准确率 / no-tool 率 / 记忆 P·R / MCP 成功率 | 高 | 工具三项✅，记忆 P/R 与 MCP 计划中 |
| L2 链路级 | E2E 成功率 / 工具链完成率 / TTFT / 延迟 / token·turn | 高 | ✅ |
| L3 质量级 | LLM-as-Judge：相关性/角色一致/记忆使用（3× 取中位数 + Kappa 校验） | 中 | 计划中 |
| L4 回归级 | golden set + baseline diff，精确到"哪条 case 变了多少" | — | 计划中 |

当前已落地 **L1（工具三项）+ L2**，对真实 Agent 出报告。

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
