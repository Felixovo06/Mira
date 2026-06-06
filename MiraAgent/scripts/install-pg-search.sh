#!/usr/bin/env bash
#
# 给 Docker 化的 PostgreSQL 加装 ParadeDB pg_search(BM25),保留 pgvector,数据卷不动。
# 做法:FROM 你现有的 PG 镜像构建新镜像(自动对齐 PG 大版本)+ 装 pg_search deb,
#       先 pg_dump 备份,旧容器只改名不删(可回退),新容器挂同一数据卷起。
#
# 用法:  在 DB 服务器上(能跑 docker 的机器)执行:
#           bash install-pg-search.sh <postgres容器名>
#         不带参数会列出容器供你挑。
#
# 安全:破坏性步骤(停删旧容器)前会要求输入 yes 确认;旧容器改名为 <名>_old_<时间戳> 保留。
#
set -euo pipefail

PARADE_VERSION="${PARADE_VERSION:-0.23.5}"   # 可用环境变量覆盖
DB_NAME="${DB_NAME:-miraagent}"
DB_USER="${DB_USER:-postgres}"

C="${1:-}"
if [ -z "$C" ]; then
  echo "用法: bash $0 <postgres容器名>"
  echo "当前容器:"
  docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}'
  exit 1
fi

echo "==> 探测容器 [$C] ..."
PGV_NUM=$(docker exec "$C" psql -U "$DB_USER" -tAc "show server_version_num;" | tr -d '[:space:]')
PGV=$(( PGV_NUM / 10000 ))
IMAGE=$(docker inspect "$C" --format '{{.Config.Image}}')
CODENAME=$(docker exec "$C" sh -lc '. /etc/os-release 2>/dev/null; echo "${VERSION_CODENAME:-bookworm}"')
ARCH=$(docker exec "$C" dpkg --print-architecture)
VOL=$(docker inspect "$C" --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}')
PORT=$(docker inspect "$C" --format '{{with index .NetworkSettings.Ports "5432/tcp"}}{{(index . 0).HostPort}}{{end}}')
RESTART=$(docker inspect "$C" --format '{{.HostConfig.RestartPolicy.Name}}')
[ -z "$RESTART" ] || [ "$RESTART" = "no" ] && RESTART=unless-stopped

# 保留原有 POSTGRES_* 环境变量
ENV_ARGS=()
while IFS= read -r e; do [ -n "$e" ] && ENV_ARGS+=( -e "$e" ); done \
  < <(docker inspect "$C" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -E '^POSTGRES_' || true)

if [ -z "$VOL" ]; then
  echo "!! 没找到挂在 /var/lib/postgresql/data 的命名卷,可能是 bind mount;请手动确认数据目录后再操作。" >&2
  docker inspect "$C" --format '{{json .Mounts}}'
  exit 1
fi

DEB="postgresql-${PGV}-pg-search_${PARADE_VERSION}-1PARADEDB-${CODENAME}_${ARCH}.deb"
URL="https://github.com/paradedb/paradedb/releases/download/v${PARADE_VERSION}/${DEB}"
NEW_IMG="$(echo "$IMAGE" | sed 's/[:/].*//')-pgsearch:pg${PGV}"

cat <<INFO

  容器名      : $C
  当前镜像    : $IMAGE
  PG 大版本   : $PGV   (server_version_num=$PGV_NUM)
  发行版代号  : $CODENAME
  架构        : $ARCH
  数据卷      : $VOL
  宿主端口    : ${PORT:-未映射}
  重启策略    : $RESTART
  pg_search   : v${PARADE_VERSION}
  下载包      : $DEB
  新镜像 tag  : $NEW_IMG

INFO

# ---------- 1. 备份 ----------
TS=$(date +%Y%m%d_%H%M%S)
BAK="./${DB_NAME}_${TS}.dump"
echo "==> 备份数据库到 $BAK ..."
docker exec "$C" pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc -f "/tmp/${DB_NAME}_${TS}.dump"
docker cp "$C:/tmp/${DB_NAME}_${TS}.dump" "$BAK"
echo "    备份完成: $BAK ($(du -h "$BAK" | cut -f1))"

# ---------- 2. 构建带 pg_search 的镜像 ----------
echo "==> 构建新镜像 $NEW_IMG (FROM $IMAGE) ..."
docker build -t "$NEW_IMG" -f - . <<DOCKERFILE
FROM $IMAGE
USER root
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends curl ca-certificates; \
    curl -fsSL -o /tmp/pgsearch.deb "$URL"; \
    apt-get install -y /tmp/pgsearch.deb; \
    rm -rf /tmp/pgsearch.deb /var/lib/apt/lists/*
DOCKERFILE

# ---------- 3. 确认后切换容器 ----------
echo
read -rp "将停止旧容器并用新镜像挂同卷 [$VOL] 重建(旧容器改名保留,可回退)。继续? 输入 yes: " ans
[ "$ans" = "yes" ] || { echo "已取消(镜像已构建、备份已留)。"; exit 1; }

PORT_ARG=()
[ -n "${PORT:-}" ] && PORT_ARG=( -p "${PORT}:5432" )

echo "==> 停止并改名旧容器 -> ${C}_old_${TS}"
docker stop "$C"
docker rename "$C" "${C}_old_${TS}"

echo "==> 启动新容器 $C(预加载 pg_search)..."
docker run -d --name "$C" --restart "$RESTART" \
  "${ENV_ARGS[@]}" "${PORT_ARG[@]}" \
  --shm-size=1g \
  -v "${VOL}:/var/lib/postgresql/data" \
  "$NEW_IMG" \
  -c shared_preload_libraries=pg_search

# ---------- 4. 等待就绪 + 建扩展/索引/验证 ----------
echo "==> 等待 PG 就绪 ..."
ok=0
for _ in $(seq 1 30); do
  if docker exec "$C" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then ok=1; break; fi
  sleep 2
done
if [ "$ok" != "1" ]; then
  echo "!! 新容器未就绪。回退命令:" >&2
  echo "   docker stop $C && docker rm $C && docker rename ${C}_old_${TS} $C && docker start $C" >&2
  docker logs --tail 50 "$C" >&2 || true
  exit 1
fi

echo "==> 建扩展 + BM25 索引 + 验证 ..."
docker exec -i "$C" psql -U "$DB_USER" -d "$DB_NAME" <<SQL
CREATE EXTENSION IF NOT EXISTS pg_search;
CREATE EXTENSION IF NOT EXISTS vector;
SELECT '测试中文分词'::pdb.jieba::text[] AS jieba_check;
CREATE INDEX IF NOT EXISTS idx_memory_index_bm25
    ON memory_index USING bm25 (id, (content_preview::pdb.jieba))
    WITH (key_field = 'id');
\dx
SQL

cat <<DONE

✅ 完成。pg_search + pgvector 已就绪,idx_memory_index_bm25 已建。
   旧容器保留为 ${C}_old_${TS}(确认稳定后可 docker rm 删除)。
   备份文件:$BAK

回退(如需):
   docker stop $C && docker rm $C && docker rename ${C}_old_${TS} $C && docker start $C
DONE
