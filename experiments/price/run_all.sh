#!/usr/bin/env bash
# 一键复现价格建议实验（模拟数据）：生成数据 → 编译（仅需 JDK 17）→ 验证划分（split seed 7..11，选参用）→ 正式划分（42..46）→ 作图
# 用法：bash run_all.sh
# 环境：JDK 17（javac/java 在 PATH 中，或设置 JAVA_HOME）；Python 3 + numpy + matplotlib
set -euo pipefail
cd "$(dirname "$0")"
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
T0=$(date +%s)
python3 scripts/gen_campus_data.py data/campus
rm -rf build && mkdir -p build
javac -encoding UTF-8 -d build $(find src -name '*.java')
# 参数：数据目录 输出目录 起始划分种子 划分次数
java -Xmx2g -cp build com.campus.trade.price.experiment.PriceExperimentMain data/campus results/validation_seed7 7 5
java -Xmx2g -cp build com.campus.trade.price.experiment.PriceExperimentMain data/campus results 42 5
python3 scripts/plot_price.py results
echo "完成，用时 $(( $(date +%s) - T0 )) 秒；结果见 results/price_results.md 与 results/figures/"
