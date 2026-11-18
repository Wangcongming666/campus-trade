#!/usr/bin/env bash
# 便捷入口：安装 Python 依赖（独立 venv）并下载模型、生成分类文本向量。
# 用法：bash scripts/ai/download_model.sh  [download_model.py 的参数]
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
VENV="${AI_VENV:-$DIR/models/.venv}"
if [ ! -x "$VENV/bin/python" ]; then
  python3 -m venv "$VENV"
  "$VENV/bin/pip" install -q -r "$DIR/requirements.txt"
fi
exec "$VENV/bin/python" "$DIR/download_model.py" "$@"
