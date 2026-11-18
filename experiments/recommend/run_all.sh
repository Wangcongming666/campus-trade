#!/usr/bin/env bash
# 一键复现推荐实验：下载 MovieLens → 生成模拟数据 → 编译（仅需 JDK 17，无 Maven 依赖）→ 5 个划分种子 → 汇总/检验 → 画图
# 用法：bash run_all.sh [种子列表=42,43,44,45,46]
# 环境：JDK 17（javac/java 在 PATH 中，或设置 JAVA_HOME）；Python 3 + numpy + scipy + matplotlib（缺 scipy 时自动建 .venv 安装）
set -euo pipefail
cd "$(dirname "$0")"
SEEDS=${1:-42,43,44,45,46}
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
T0=$(date +%s)

bash scripts/download_movielens.sh data
python3 scripts/gen_campus_data.py data/campus

rm -rf build && mkdir -p build
javac -encoding UTF-8 -d build $(find src -name '*.java')

PY=python3
if ! python3 -c "import scipy, numpy, matplotlib" 2>/dev/null; then
  [ -x .venv/bin/python ] || python3 -m venv --system-site-packages .venv
  .venv/bin/python -c "import scipy" 2>/dev/null || .venv/bin/pip install -q -r requirements.txt
  PY=.venv/bin/python
fi

rm -rf results/raw
java -Xmx3g -cp build com.campus.trade.experiments.recommend.RecommendExperiment data results "$SEEDS"
$PY scripts/aggregate.py results
$PY scripts/plot.py results
echo "完成，用时 $(( $(date +%s) - T0 )) 秒；结果见 results/tables.md 与 results/figures/"
