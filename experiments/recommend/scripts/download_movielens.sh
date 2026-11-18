#!/usr/bin/env bash
# 下载 MovieLens-100K（GroupLens 官方发布，许可见 data/ml-100k/README；不入 git）
# 用法：bash scripts/download_movielens.sh [目标目录=data]
set -euo pipefail
cd "$(dirname "$0")/.."
DEST=${1:-data}
if [ -f "$DEST/ml-100k/u.data" ]; then
  echo "已存在 $DEST/ml-100k，跳过下载"
else
  mkdir -p "$DEST"
  curl -fsSL -o "$DEST/ml-100k.zip" https://files.grouplens.org/datasets/movielens/ml-100k.zip
  (cd "$DEST" && unzip -qo ml-100k.zip && rm -f ml-100k.zip)
fi
# 校验（与本实验使用的文件一致）
EXPECT_DATA=6e47046882bad158b0efbb84cd5cb987
EXPECT_ITEM=c9e7f390115d79079537301d5204f3f7
GOT_DATA=$(md5sum "$DEST/ml-100k/u.data" | cut -d' ' -f1)
GOT_ITEM=$(md5sum "$DEST/ml-100k/u.item" | cut -d' ' -f1)
if [ "$GOT_DATA" != "$EXPECT_DATA" ] || [ "$GOT_ITEM" != "$EXPECT_ITEM" ]; then
  echo "警告：MovieLens 文件校验和与本实验不一致（u.data=$GOT_DATA, u.item=$GOT_ITEM），结果可能不同" >&2
else
  echo "MovieLens-100K 校验通过"
fi
