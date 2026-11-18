#!/usr/bin/env python3
"""
下载 CLIP ViT-B/32 的 ONNX 模型（Hugging Face: Xenova/clip-vit-base-patch32，MIT 许可，
原始权重 openai/clip-vit-base-patch32），并生成分类文本向量 category_embeddings.json。

模型文件体积大（图像编码器 fp32 约 335MB / int8 量化约 85MB，文本编码器约 242MB），不进 git，
默认放到 scripts/ai/models/（已在 scripts/ai/.gitignore 中忽略）。

用法：
  pip install -r requirements.txt
  python3 download_model.py                 # 下载 fp32 + 量化图像编码器、文本编码器，并生成向量
  python3 download_model.py --skip-quantized
  HF_ENDPOINT=https://hf-mirror.com python3 download_model.py   # 国内网络可用镜像

完成后后端默认配置即可直接使用（campus.ai.clip.model-dir=../scripts/ai/models/clip-vit-base-patch32，
相对后端工作目录 backend/）。文本编码器只在本脚本里用于生成向量，Java 端不加载。
"""
import argparse
import os
import subprocess
import sys

import requests

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = "Xenova/clip-vit-base-patch32"
REVISION = "main"
# (仓库内路径, 本地文件名, 期望字节数；None 表示不校验)
FILES = [
    ("onnx/vision_model.onnx", "vision_model.onnx", 351685709),
    ("onnx/vision_model_quantized.onnx", "vision_model_quantized.onnx", 89117001),
    ("onnx/text_model.onnx", "text_model.onnx", 254058553),
    ("tokenizer.json", "tokenizer.json", None),
    ("preprocessor_config.json", "preprocessor_config.json", None),
    ("config.json", "config.json", None),
]


def download(url, dest, expect):
    if os.path.exists(dest) and (expect is None or os.path.getsize(dest) == expect):
        print(f"已存在，跳过 {os.path.basename(dest)}")
        return
    part = dest + ".part"
    print(f"下载 {url}")
    with requests.get(url, stream=True, timeout=60) as r:
        r.raise_for_status()
        total = int(r.headers.get("content-length", 0))
        done = 0
        with open(part, "wb") as f:
            for chunk in r.iter_content(1 << 20):
                f.write(chunk)
                done += len(chunk)
                if total:
                    print(f"\r  {done / 1e6:.1f}/{total / 1e6:.1f} MB", end="", file=sys.stderr)
        print(file=sys.stderr)
    if expect is not None and os.path.getsize(part) != expect:
        raise SystemExit(f"文件大小不符：{dest} 期望 {expect} 实际 {os.path.getsize(part)}")
    os.replace(part, dest)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model-dir", default=os.path.join(HERE, "models", "clip-vit-base-patch32"))
    ap.add_argument("--skip-quantized", action="store_true", help="不下载 int8 量化图像编码器")
    ap.add_argument("--skip-embeddings", action="store_true", help="只下载，不生成文本向量")
    a = ap.parse_args()
    endpoint = os.environ.get("HF_ENDPOINT", "https://huggingface.co").rstrip("/")
    os.makedirs(a.model_dir, exist_ok=True)
    for remote, local, size in FILES:
        if a.skip_quantized and "quantized" in local:
            continue
        download(f"{endpoint}/{REPO}/resolve/{REVISION}/{remote}", os.path.join(a.model_dir, local), size)
    if not a.skip_embeddings:
        subprocess.check_call([sys.executable, os.path.join(HERE, "build_text_embeddings.py"),
                               "--model-dir", a.model_dir])
    print(f"完成：{a.model_dir}")


if __name__ == "__main__":
    main()
