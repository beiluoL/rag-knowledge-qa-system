#!/usr/bin/env bash
#
# RAG 企业级知识库问答系统 — 一键启动 / 重启（macOS 双击运行）
# 拉起：PostgreSQL(5432) → Ollama(11434) → 后端 Spring Boot(:9090) → 前端 Vite(:5175)
# 双击：默认 start all（已运行的后端会自动先停再起 = 自动重启）
# 命令行：./start.command [start|restart] [all|backend|frontend|middleware] [-y]
#
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

# ===== 环境引导（双击 .command 时 PATH 极简，必须显式补全）=====
export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null)"
[ -z "$JAVA_HOME" ] && JAVA_HOME="/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home"
export JAVA_HOME
export PATH="/opt/homebrew/bin:/usr/local/bin:$JAVA_HOME/bin:$PATH"
# 代理会污染 localhost 健康检查（经代理 502 被误判为服务就绪），一律清除
unset HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy all_proxy

# ===== 配置 =====
PROJECT_NAME="RAG 企业级知识库问答系统"
BACKEND_PORT=9090
FRONTEND_PORT=5175
BACKEND_DIR="$SCRIPT_DIR/backend"
FRONTEND_DIR="$SCRIPT_DIR/frontend"
BACKEND_URL="http://localhost:${BACKEND_PORT}"
HEALTH_URL="${BACKEND_URL}/api/auth/login"   # GET 会 405，但只要返回任意 HTTP 码即证明服务已起
FRONTEND_URL="http://localhost:${FRONTEND_PORT}"
MAX_WAIT=150                                  # 后端就绪最长等待（秒）

# 工具链绝对路径（存在则用，缺失回退 PATH）
MVN_BIN="/Users/beiluo/Documents/Development/apache-maven-3.8.3/bin/mvn"
MAVEN_REPO="/Users/beiluo/maven_repository"
[ -x "$MVN_BIN" ] || MVN_BIN="$(command -v mvn 2>/dev/null || true)"
NPM_BIN="/Users/beiluo/.workbuddy/binaries/node/versions/22.22.2-3/bin/npm"
[ -x "$NPM_BIN" ] || NPM_BIN="$(command -v npm 2>/dev/null || true)"
OLLAMA_BIN="/usr/local/bin/ollama"
[ -x "$OLLAMA_BIN" ] || OLLAMA_BIN="$(command -v ollama 2>/dev/null || true)"
BREW_BIN="/opt/homebrew/bin/brew"
[ -x "$BREW_BIN" ] || BREW_BIN="$(command -v brew 2>/dev/null || true)"

# 后端进程签名（项目内唯一，用于兜底精准清理；勿改成宽泛匹配）
BACKEND_SIG_JVM="RagKbApplication"
BACKEND_SIG_MVN="maven.repo.local=${MAVEN_REPO}"

# 中间件联动：每项 "名称|端口|连接串|是否关键(y/n)"
# 关键中间件启动失败 → 整个启动中止；非关键失败 → 仅 [WARN]
SERVICES_ENTRIES=(
  "postgres|5432|jdbc:postgresql://localhost:5432|y"
  "ollama|11434|http://localhost:11434|n"
)

BACKEND_LOG="$SCRIPT_DIR/logs/backend.log"
FRONTEND_LOG="$SCRIPT_DIR/logs/frontend.log"
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
ACTION="start"; TARGET="all"
for a in "$@"; do
  case "$a" in
    start|restart) ACTION="$a" ;;
    all|backend|frontend|middleware) TARGET="$a" ;;
    -y|--yes) ;;
    *) warn "未知参数: $a（忽略）" ;;
  esac
done

# ===== 中间件联动 =====
MW_MAX_WAIT=20
port_busy(){ lsof -iTCP:"$1" -sTCP:LISTEN -n -P >/dev/null 2>&1; }
port_pid(){  lsof -iTCP:"$1" -sTCP:LISTEN -n -P -t 2>/dev/null; }

mw_start_one(){
  local name=$1 port=$2 conn=$3 critical=$4
  echo ">>> 检查 $name (端口 $port) ..."
  if port_busy "$port"; then
    # postgres 特有：校验 5432 上跑的是不是项目用的 postgresql@17（曾发生 @16 抢占端口的故障）
    if [ "$name" = "postgres" ]; then
      local bpath; bpath=$(lsof -p "$(port_pid "$port" | head -1)" 2>/dev/null | grep -m1 'bin/postgres' )
      case "$bpath" in
        *postgresql@17*) echo -e "${GREEN}[SKIP]${NC} $name 已在运行 (postgresql@17, 地址: $conn)" ;;
        *postgresql@16*)
          err "端口 5432 被 postgresql@16 占用，而项目数据库在 postgresql@17（无 pgvector 会启动失败）"
          err "修复：brew services stop postgresql@16 && brew services start postgresql@17"
          return 1 ;;
        *) echo -e "${GREEN}[SKIP]${NC} $name 已在运行 (地址: $conn)" ;;
      esac
    else
      echo -e "${GREEN}[SKIP]${NC} $name 已在运行 (PID: $(port_pid "$port" | head -1 || echo '?'), 地址: $conn)"
    fi
    return 0
  fi
  info "$name 未运行，正在启动..."
  case "$name" in
    postgres)
      "$BREW_BIN" services start postgresql@17 2>/dev/null \
        || "$BREW_BIN" services start postgresql 2>/dev/null \
        || { err "PostgreSQL 启动失败（尝试 brew services postgresql@17 / postgresql 均失败）"; return 1; } ;;
    ollama)
      if [ -z "$OLLAMA_BIN" ]; then warn "未找到 ollama 可执行文件，跳过（离线 AI 将不可用，可手动启动）"; return 1; fi
      nohup "$OLLAMA_BIN" serve >> "$SCRIPT_DIR/logs/ollama.log" 2>&1 </dev/null & echo $! > "$MW_PID_DIR/mw_ollama.pid" ;;
    *) warn "未知服务 $name，跳过"; return 1 ;;
  esac
  local r=0
  while ! port_busy "$port" && [ $r -lt "$MW_MAX_WAIT" ]; do echo -n "."; sleep 1; r=$((r+1)); done
  echo ""
  if port_busy "$port"; then
    ok "$name 启动成功 (PID: $(port_pid "$port" | head -1 || echo '?'), 地址: $conn)"
    return 0
  fi
  err "$name 启动超时（${MW_MAX_WAIT}s 内端口 $port 未就绪）"
  return 1
}

mw_start_all(){
  local entry s p c crit
  for entry in "${SERVICES_ENTRIES[@]}"; do
    IFS='|' read -r s p c crit <<< "$entry"
    if mw_start_one "$s" "$p" "$c" "$crit"; then continue; fi
    if [ "$crit" = "y" ]; then
      err "关键中间件 $s 启动失败，终止启动流程（后端没有它无法工作）"
      exit 1
    fi
    warn "非关键中间件 $s 启动失败，继续（相关 AI 能力可能不可用）"
  done
}

mw_stop_all(){
  local entry s p c crit pidf pid leaked=0
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

mw_print_status(){
  local entry s p c crit
  for entry in "${SERVICES_ENTRIES[@]}"; do
    IFS='|' read -r s p c crit <<< "$entry"
    if port_busy "$p"; then echo -e "  - ${GREEN}$s: 运行中 ($c)${NC}"
    else echo -e "  - ${RED}$s: 未运行${NC}"; fi
  done
}

# ===== 停止本项目前后端（精准 PID + 端口 + 签名兜底）=====
kill_pid_graceful(){ local pid=$1; kill "$pid" 2>/dev/null; sleep 2; kill -0 "$pid" 2>/dev/null && { kill -9 "$pid" 2>/dev/null; sleep 1; }; }
stop_frontend(){
  local stopped=0
  if [ -f "$PID_FRONTEND" ]; then
    local pid; pid=$(cat "$PID_FRONTEND")
    if kill -0 "$pid" 2>/dev/null; then info "停止前端进程 (PID $pid) ..."; kill_pid_graceful "$pid"; stopped=1; fi
    rm -f "$PID_FRONTEND"
  fi
  if port_busy "$FRONTEND_PORT"; then port_pid "$FRONTEND_PORT" | xargs -r kill_pid_graceful; stopped=1; fi
  [ $stopped -eq 0 ] && { ok "前端未运行（无需停止）"; return 0; }
  port_busy "$FRONTEND_PORT" && warn "前端端口 $FRONTEND_PORT 仍被占用，请手动释放（lsof -iTCP:$FRONTEND_PORT）" || ok "前端已停止（端口 $FRONTEND_PORT 已释放）"
}
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

# ===== 前置依赖检查 =====
check_deps(){
  info "检查运行环境（${PROJECT_NAME}）..."
  local missing=0
  [ -x "$MVN_BIN" ] || { err "未找到 Maven（$MVN_BIN 不存在且 PATH 无 mvn）"; missing=1; }
  [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ] || { err "未找到 JDK（/usr/libexec/java_home 失败），请安装 JDK 17"; missing=1; }
  command -v curl >/dev/null 2>&1 || { err "未找到 curl"; missing=1; }
  if [ "$TARGET" = "all" ] || [ "$TARGET" = "frontend" ]; then
    [ -x "$NPM_BIN" ] || { err "未找到 npm（$NPM_BIN 不存在且 PATH 无 npm）"; missing=1; }
  fi
  [ -d "$BACKEND_DIR" ] || { err "后端目录不存在: $BACKEND_DIR"; missing=1; }
  [ $missing -eq 1 ] && exit 1
  ok "环境就绪（JAVA_HOME=$JAVA_HOME）"
}

# ===== 启动后端 =====
start_backend(){
  info "启动后端（Maven spring-boot:run, 离线仓库）-> ${BACKEND_URL}"
  mkdir -p "$SCRIPT_DIR/logs" "$RUN_DIR"
  # 生成独立启动脚本：主脚本与其彻底解耦（disown 后绝不等待服务进程）
  cat > "$RUN_DIR/run-backend.sh" <<RBEOF
#!/bin/bash
cd "$BACKEND_DIR" || exit 1
exec env -u SERVER__PORT "$MVN_BIN" -o -Dmaven.repo.local="$MAVEN_REPO" spring-boot:run
RBEOF
  chmod +x "$RUN_DIR/run-backend.sh"
  : > "$BACKEND_LOG"   # 清空旧日志，避免残留的 BUILD FAILURE 误报
  nohup "$RUN_DIR/run-backend.sh" >> "$BACKEND_LOG" 2>&1 </dev/null &
  echo $! > "$PID_BACKEND"
  disown %+ 2>/dev/null || true
  ok "后端进程已拉起 (Maven PID $(cat "$PID_BACKEND" 2>/dev/null || echo '?'))"
  info "等待后端就绪（最长 ${MAX_WAIT}s，首次启动需编译请耐心等待）..."
  local i=0 code=""
  while [ $i -lt "$MAX_WAIT" ]; do
    code=$(curl --noproxy '*' -s --max-time 3 -o /dev/null -w "%{http_code}" "$HEALTH_URL" 2>/dev/null)
    # 就绪双重校验：端口确实在本进程监听 + 返回了任意 HTTP 码（401/403/405 也算 Tomcat 已起）
    if port_busy "$BACKEND_PORT" && [ -n "$code" ] && [ "$code" != "000" ]; then break; fi
    # 编译失败提前退出
    if grep -qE "BUILD FAILURE|APPLICATION FAILED TO START" "$BACKEND_LOG" 2>/dev/null; then
      err "后端启动失败（编译或装配错误）"; return 1
    fi
    sleep 2; i=$((i+2))
  done
  if [ -z "$code" ] || [ "$code" = "000" ]; then
    err "后端启动超时（${MAX_WAIT}s）。最近日志："
    tail -n 25 "$BACKEND_LOG" 2>/dev/null
    warn "正在清理已拉起的进程..."
    [ -f "$PID_BACKEND" ] && kill "$(cat "$PID_BACKEND")" 2>/dev/null; rm -f "$PID_BACKEND"
    return 1
  fi
  ok "后端就绪：${BACKEND_URL}"
}

# ===== 启动前端 =====
start_frontend(){
  if port_busy "$FRONTEND_PORT"; then
    echo -e "${GREEN}[SKIP]${NC} 前端已在运行 (端口 $FRONTEND_PORT, PID $(port_pid "$FRONTEND_PORT" | head -1))：${FRONTEND_URL}"
    return 0
  fi
  if [ ! -x "$NPM_BIN" ]; then warn "未找到 npm，跳过前端启动"; return 1; fi
  info "启动前端（Vite dev）-> ${FRONTEND_URL}"
  mkdir -p "$SCRIPT_DIR/logs" "$RUN_DIR"
  cat > "$RUN_DIR/run-frontend.sh" <<RFEOF
#!/bin/bash
cd "$FRONTEND_DIR" || exit 1
exec "$NPM_BIN" run dev
RFEOF
  chmod +x "$RUN_DIR/run-frontend.sh"
  : > "$FRONTEND_LOG"
  nohup "$RUN_DIR/run-frontend.sh" >> "$FRONTEND_LOG" 2>&1 </dev/null &
  echo $! > "$PID_FRONTEND"
  disown %+ 2>/dev/null || true
  local i=0 code=""
  while [ $i -lt 30 ]; do
    code=$(curl --noproxy '*' -s --max-time 3 -o /dev/null -w "%{http_code}" "$FRONTEND_URL" 2>/dev/null)
    [ "$code" = "200" ] && break
    sleep 1; i=$((i+1))
  done
  if [ "$code" = "200" ]; then ok "前端就绪：${FRONTEND_URL}"
  else
    warn "前端启动异常（不影响后端）。日志：tail -n 20 logs/frontend.log"
    tail -n 15 "$FRONTEND_LOG" 2>/dev/null
    rm -f "$PID_FRONTEND"; return 1
  fi
}

# ===== 主流程 =====
do_middleware(){ mw_start_all; }
do_backend(){
  if port_busy "$BACKEND_PORT" || pkill -0 -f "$BACKEND_SIG_JVM" 2>/dev/null; then
    warn "检测到后端已在运行，先关闭再重启（自动重启）..."
    stop_backend
  fi
  start_backend || exit 1
}
do_frontend(){
  if [ "$ACTION" = "restart" ]; then stop_frontend; start_frontend || exit 1; return; fi
  start_frontend || exit 1
}

echo -e "${BOLD}========== ${PROJECT_NAME} · ${ACTION}（目标: $TARGET）==========${NC}"
check_deps
case "$TARGET" in
  backend)    do_backend ;;
  frontend)   do_frontend ;;
  middleware) do_middleware ;;
  all|*)      do_middleware; do_backend; do_frontend ;;
esac

# ===== 状态总览 =====
echo ""
echo -e "${BOLD}================ ${PROJECT_NAME} 服务状态 ================${NC}"
if [ "$TARGET" = "backend" ] || [ "$TARGET" = "all" ]; then
  port_busy "$BACKEND_PORT" \
    && echo -e "  ${GREEN}●${NC} 后端 (API)  : 运行中   ${BACKEND_URL}" \
    || echo -e "  ${RED}●${NC} 后端 (API)  : 未运行"
fi
if [ "$TARGET" = "frontend" ] || [ "$TARGET" = "all" ]; then
  port_busy "$FRONTEND_PORT" \
    && echo -e "  ${GREEN}●${NC} 前端 (页面) : 运行中   ${FRONTEND_URL}" \
    || echo -e "  ${RED}●${NC} 前端 (页面) : 未运行"
fi
if [ "$TARGET" = "middleware" ] || [ "$TARGET" = "all" ]; then
  echo -e "  ${CYAN}·${NC} 中间件状态:"
  mw_print_status
fi
echo -e "----------------------------------------------"
echo -e "  后端日志 : tail -f logs/backend.log"
echo -e "  前端日志 : tail -f logs/frontend.log"
echo -e "  一键关闭 : 双击 stop.command（重启: restart.command）"
echo -e "${BOLD}==========================================${NC}"
echo ""
exit 0
