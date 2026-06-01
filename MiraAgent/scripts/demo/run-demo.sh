#!/usr/bin/env bash
#
# MiraAgent 端到端 Demo：学习搭子「小研」陪用户规划复习。
# 演示：角色卡 → 写入长期记忆 → 调用 todo 工具规划 → 跨轮 session/记忆召回 →
#       任务结束后 Background Review 沉淀/修补学习规划 skill。
#
# 前置：本地起服务（带真实模型 + DB + embedding），默认 8080。
#   JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw install -DskipTests
#   JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw spring-boot:run -pl adapter-spring -Dspring-boot.run.profiles=local
# 依赖：curl、jq
#
set -uo pipefail

BASE="${MIRA_BASE:-http://localhost:8080}"
USER_ID="${MIRA_USER:-demo-user}"
CHAR="${MIRA_CHARACTER:-study-buddy}"
SESSION="demo-$(date +%s)"

command -v jq >/dev/null || { echo "需要 jq，请先安装"; exit 1; }

hr() { printf '\n\033[36m──────── %s ────────\033[0m\n' "$1"; }
say() { printf '\033[33m👤 用户:\033[0m %s\n' "$1"; }

# chat <message> [enabledToolsJsonArray]
LAST_RUN=""
chat() {
  local msg="$1"; local tools="${2:-[]}"
  say "$msg"
  local resp
  resp=$(curl -s "$BASE/api/chat" -H 'Content-Type: application/json' -d "$(jq -n \
    --arg u "$USER_ID" --arg s "$SESSION" --arg c "$CHAR" --arg m "$msg" \
    --argjson t "$tools" \
    '{userId:$u, sessionId:$s, characterId:$c, content:$m, enabledTools:$t}')")
  LAST_RUN=$(echo "$resp" | jq -r '.runId // empty')
  printf '\033[32m🤖 %s:\033[0m %s\n' "$CHAR" "$(echo "$resp" | jq -r '.content // .error // "(无回复)"')"
  local tools_used
  tools_used=$(echo "$resp" | jq -r '[.toolExecutions[]?.toolName] | unique | join(", ")')
  [ -n "$tools_used" ] && printf '   \033[35m🔧 调用工具: %s\033[0m\n' "$tools_used"
}

hr "0. 健康检查"
curl -s "$BASE/api/health" | jq . || { echo "服务未启动？BASE=$BASE"; exit 1; }

hr "1. 可用角色卡（应包含 study-buddy / mira）"
curl -s "$BASE/api/characters" | jq -r '.[] | "  - \(.id): \(.name) — \(.description)"'

hr "2. 用户介绍课程与目标 → Agent 应写入长期记忆"
chat "我在学线性代数，期末 7 月初，目标 90 分以上，最弱的是特征值。"

hr "   当前长期记忆"
curl -s "$BASE/api/memory?userId=$USER_ID" | jq -r '.[]? | "  - [\(.category)] \(.contentPreview)"'

hr "3. 请求规划复习 → Agent 应调用 todo 工具"
chat "帮我规划这周的复习，每天别超过 3 小时。" '["todo","calculator"]'

hr "   本轮工具执行明细 (runId=$LAST_RUN)"
[ -n "$LAST_RUN" ] && curl -s "$BASE/api/tool-executions/runs/$LAST_RUN" | jq -r '.[]? | "  - \(.toolName) [\(.status)]"'

hr "4. 几轮之后，让 Agent 召回上次计划（跨轮 session + 记忆）"
chat "我们继续上次那个复习计划吧，今天该干嘛？" '["todo"]'

hr "5. 一次 run 的 trace（最后一轮 runId=$LAST_RUN）"
[ -n "$LAST_RUN" ] && curl -s "$BASE/api/traces/$LAST_RUN" | jq -r '.[]? | "  - [\(.stepIndex)] \(.eventType)"'

hr "6. 自我改善：Background Review 沉淀的 skill 与 Curator 建议"
curl -s "$BASE/api/skills" | jq -r '.[]? | "  - \(.name) (use=\(.useCount // 0), v\(.version // 1))"'
echo "  --- curator report (unused/narrow/similar 各自数量) ---"
curl -s "$BASE/api/skills/curator-report" | jq '{unused: (.unused|length), narrow: (.narrow|length), similar: (.similar|length)}'

hr "Demo 结束"
echo "会话: $SESSION  用户: $USER_ID  角色: $CHAR"
echo "提示：trace/工具/记忆/skill 均可在 Web UI (http://localhost:5173) 中查看。"
