#!/usr/bin/env bash
#
# RAG 企业级知识库问答系统 — 一键重启（macOS 双击运行）
# 逻辑：先按目标执行 stop.command，再执行 start.command（中间件仅复用/拉起，不误停外部服务）
# 命令行：./restart.command [all|backend|frontend|middleware]
#
set -o pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

TARGET="all"
for a in "$@"; do
  case "$a" in
    all|backend|frontend|middleware) TARGET="$a" ;;
    *) echo "[WARN] 未知参数: $a（忽略）" ;;
  esac
done

echo "[INFO] 重启目标: $TARGET —— 第 1 步: 停止"
bash "$SCRIPT_DIR/stop.command" "$TARGET"
STOP_RC=$?

echo ""
echo "[INFO] 第 2 步: 启动"
bash "$SCRIPT_DIR/start.command" start "$TARGET"
START_RC=$?

echo ""
if [ $START_RC -eq 0 ]; then
  echo "[ OK ] 重启完成（目标: $TARGET）"
else
  echo "[FAIL] 重启失败（stop 退出码: $STOP_RC, start 退出码: $START_RC），请查看上方日志"
fi
exit $START_RC
