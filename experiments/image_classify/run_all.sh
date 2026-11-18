#!/usr/bin/env bash
# 一键复现图片分类小样本实验（需先运行 scripts/ai/download_model.sh 下载模型并生成文本向量）。
#   bash run_all.sh            # 已有 images/ 时直接评估
#   COLLECT=1 bash run_all.sh  # 重新从 Wikimedia Commons 下载候选图（人工剔除结果见 exclusions.json）
# 环境变量：PY（Python 解释器）、BACKEND_CLASSES（后端编译产物 target/classes）、JAVA_CP（后端依赖 classpath）
set -euo pipefail
cd "$(dirname "$0")"
PY="${PY:-python3}"
MODEL_DIR="$(cd ../../scripts/ai/models/clip-vit-base-patch32 && pwd)"
if [ "${COLLECT:-0}" = "1" ]; then
  "$PY" collect_samples.py candidates
fi
"$PY" collect_samples.py finalize
"$PY" eval_zero_shot.py --threads 2
if [ -n "${BACKEND_CLASSES:-}" ] && [ -n "${JAVA_CP:-}" ]; then
  for f in vision_model.onnx vision_model_quantized.onnx; do
    [ -f "$MODEL_DIR/$f" ] && java -cp "$BACKEND_CLASSES:$JAVA_CP" JavaEval.java "$MODEL_DIR" "$f" 2
  done
  "$PY" compare_java.py
else
  echo "未设置 BACKEND_CLASSES/JAVA_CP，跳过 Java 复核（见 README）"
fi
"$PY" make_report.py
