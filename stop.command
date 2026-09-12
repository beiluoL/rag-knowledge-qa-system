#!/usr/bin/env bash
#
# RAG 企业级知识库问答系统 — 一键关闭（macOS 双击运行）
# 顺序：前端(5175) → 后端(9090) → 中间件（仅本脚本拉起的；外部已有的不碰）
# 双击：默认停 all。命令行：./stop.command [all|backend|frontend|middleware] [--force] [-c]
#
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null)"
export PATH="/opt/homebrew/bin:/usr/local/bin:${JAVA_HOME:+$JAVA_HOME/bin}:$PATH"

# ===== 配置（与 start.command 保持一致）=====
PROJECT_NAME="RAG 企业级知识库问答系统"
BACKEND_PORT=9090
FRONTEND_PORT=5175
MAVEN_REPO="/Users/beiluo/maven_repository"
BACKEND_SIG_JVM="RagKbApplication"
BACKEND_SIG_MVN="maven.repo.local=${MAVEN_REPO}"
# 与 start.command 一致：名称|端口|连接串|是否关键
SERVICES_ENTRIES=(
  "postgres|5432|jdbc:postgresql://localhost:5432|y"
  "ollama|11434|http://localhost:11434|n"
)

RUN_DIR="$SCRIPT_DIR/.run"
PID_BACKEND="$RUN_DIR/backend.pid"
PID_FRONTEND="$RUN_DIR/frontend.pid"
MW_PID_DIR="$RUN_DIR"

# ===== 颜色 / 状态输出 =====
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
info(){ echo -e "${CYAN}[INFO]${NC} $*"; }
ok(){   echo -e "${GREEN}[ OK ]${NC} $*"; }
warn(){ echo -e "${YELLOW}[WARN]${NC} $*"; }
err(){  echo -e "${RED}[FAIL]${NC} $*"; }

# ===== 参数解析 =====
TARGET="all"; FORCE=0; CONFIRM=0; YES=0
for a in "$@"; do
  case "$a" in
    all|backend|frontend|middleware) TARGET="$a" ;;
    --force|-f) FORCE=1 ;;
    -c|--confirm) CONFIRM=1 ;;
    -y|--yes) YES=1 ;;
    *) warn "未知参数: $a（忽略）" ;;
  esac
done

port_busy(){ lsof -iTCP:"$1" -sTCP:LISTEN -n -P >/dev/null 2>&1; }
port_pid(){  lsof -iTCP:"$1" -sTCP:LISTEN -n -P -t 2>/dev/null; }
kill_pid_graceful(){
  local pid=$1
  [ "$FORCE" = "1" ] && { kill -9 "$pid" 2>/dev/null; return; }
  kill "$pid" 2>/dev/null; sleep 2
  kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null
  sleep 1
}

# ===== 前端 =====
stop_frontend(){
  local stopped=0
  if [ -f "$PID_FRONTEND" ]; then
    local pid; pid=$(cat "$PID_FRONTEND")
    if kill -0 "$pid" 2>/dev/null; then info "停止前端进程 (PID $pid) ..."; kill_pid_graceful "$pid"; stopped=1; fi
    rm -f "$PID_FRONTEND"
  fi
  if port_busy "$FRONTEND_PORT"; then
    info "停止前端端口占用进程 (端口 $FRONTEND_PORT, PID $(port_pid "$FRONTEND_PORT" | tr '\n' ' ')) ..."
    port_pid "$FRONTEND_PORT" | xargs -r kill_pid_graceful; stopped=1
  fi
  [ $stopped -eq 0 ] && { ok "前端未运行（无需停止）"; return 0; }
  port_busy "$FRONTEND_PORT" && warn "前端端口 $FRONTEND_PORT 仍被占用，请手动释放（lsof -iTCP:$FRONTEND_PORT）" || ok "前端已停止（端口 $FRONTEND_PORT 已释放）"
}

# ===== 后端 =====
stop_backend(){
  local stopped=0
  if [ -f "$PID_BACKEND" ]; then
    local pid; pid=$(cat "$PID_BACKEND")
    if kill -0 "$pid" 2>/dev/null; then info "停止后端 Maven 进程 (PID $pid) ..."; kill_pid_graceful "$pid"; stopped=1; fi
    rm -f "$PID_BACKEND"
  fi
  if port_busy "$BACKEND_PORT"; then
    info "停止后端服务进程 (端口 $BACKEND_PORT, PID $(port_pid "$BACKEND_PORT" | tr '\n' ' ')) ..."
    port_pid "$BACKEND_PORT" | xargs -r kill_pid_graceful; stopped=1
  fi
  # 签名兜底：清理可能残留的本项目 JVM / Maven 进程（签名项目内唯一，不误伤他项目）
  pkill -f "$BACKEND_SIG_JVM" 2>/dev/null && { stopped=1; sleep 1; }
  pkill -f "$BACKEND_SIG_MVN" 2>/dev/null && { stopped=1; sleep 1; }
  [ $stopped -eq 0 ] && { ok "后端未运行（无需停止）"; return 0; }
  port_busy "$BACKEND_PORT" && warn "后端端口 $BACKEND_PORT 仍被占用，请手动释放（lsof -iTCP:$BACKEND_PORT）" || ok "后端已停止（端口 $BACKEND_PORT 已释放）"
}

# ===== 中间件（只停本脚本拉起的：以 .run/mw_*.pid 为准）=====
stop_middleware(){
  local entry s p c crit pidf pid leaked=0
  if [ "$CONFIRM" = "1" ]; then
    printf "确认停止本脚本拉起的中间件? [y/N]: "
    read -r ans
    case "$ans" in y|Y) ;; *) info "已跳过中间件停止"; return 0 ;; esac
  fi
  for entry in "${SERVICES_ENTRIES[@]}"; do
    IFS='|' read -r s p c crit <<< "$entry"
    pidf="$MW_PID_DIR/mw_${s}.pid"
    if [ -f "$pidf" ]; then
      pid=$(cat "$pidf")
      echo ">>> 停止 $s (本脚本启动, PID: $pid, 端口 $p) ..."
      kill "$pid" 2>/dev/null; sleep 2
      port_busy "$p" && port_pid "$p" | xargs -r kill -9 2>/dev/null && sleep 1
      if port_busy "$p"; then warn "$s 端口 $p 仍被占用，请手动检查"; leaked=1
      else ok "$s 已停止（端口 $p 已释放）"; fi
      rm -f "$pidf"
    else
      if port_busy "$p"; then info "$s 此前已在运行（非本脚本启动），未停止，请自行管理"
      else info "$s 未运行，无需停止"; fi
    fi
  done
  return $leaked
}

echo -e "${BOLD}========== ${PROJECT_NAME} · 一键关闭（目标: $TARGET）==========${NC}"
case "$TARGET" in
  backend)    stop_backend ;;
  frontend)   stop_frontend ;;
  middleware) stop_middleware ;;
  all|*)      stop_frontend; stop_backend; stop_middleware ;;
esac

# ===== 校验端口释放 =====
echo ""
echo -e "${BOLD}================ 关闭结果 ================${NC}"
FINAL=0
if [ "$TARGET" = "backend" ] || [ "$TARGET" = "all" ]; then
  port_busy "$BACKEND_PORT" && { echo -e "  ${RED}●${NC} 后端端口 $BACKEND_PORT : 仍被占用"; FINAL=1; } \
    || echo -e "  ${GREEN}●${NC} 后端端口 $BACKEND_PORT : 已释放"
fi
if [ "$TARGET" = "frontend" ] || [ "$TARGET" = "all" ]; then
  port_busy "$FRONTEND_PORT" && { echo -e "  ${RED}●${NC} 前端端口 $FRONTEND_PORT : 仍被占用"; FINAL=1; } \
    || echo -e "  ${GREEN}●${NC} 前端端口 $FRONTEND_PORT : 已释放"
fi
[ $FINAL -eq 0 ] && ok "已停止" || err "部分端口未能释放，请按上方提示手动处理"
echo -e "${BOLD}==========================================${NC}"
echo ""
exit $FINAL
