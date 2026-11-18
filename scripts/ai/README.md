# scripts/ai · 图片识别自动分类模型

商品发布时上传第一张图，后端用 **CLIP ViT-B/32 零样本分类** 给出 Top3 分类建议。全程离线运行，不依赖任何付费 API 或密钥。

## 1. 一键准备模型

```bash
bash scripts/ai/download_model.sh                 # 建独立 venv、装依赖、下载模型、生成分类文本向量
# 国内网络：HF_ENDPOINT=https://hf-mirror.com bash scripts/ai/download_model.sh
```

产物都在 `scripts/ai/models/clip-vit-base-patch32/`，已被 `scripts/ai/.gitignore` 忽略，不进 git：

| 文件 | 大小 | 用途 |
|---|---|---|
| `vision_model.onnx` | 约 352 MB | 图像编码器 fp32（后端默认加载） |
| `vision_model_quantized.onnx` | 约 89 MB | 图像编码器 int8 量化版（可选，省内存，更快） |
| `text_model.onnx` | 约 254 MB | 文本编码器，**只在 Python 里**生成文本向量，后端不加载 |
| `tokenizer.json` 等 | 小 | 分词器与预处理参数 |
| `category_embeddings.json` | 约 170 KB | 每个分类的文本向量（按 category_id），后端加载 |

模型来源：Hugging Face `Xenova/clip-vit-base-patch32`（`openai/clip-vit-base-patch32` 的 ONNX 导出版，MIT 许可）。
不需要 PyTorch。

## 2. 设计

- **Java 端只跑图像编码器**：图片 → 预处理（短边 224、双三次缩放、中心裁剪、CLIP 均值方差标准化）→ ONNX Runtime → 512 维向量 → 与分类文本向量算余弦 → ×100 后 softmax → Top3。不需要在 Java 里实现 BPE 分词。
- **提示词集成（prompt ensembling）**：`category_prompts.json` 里每个分类写 5–7 个英文物品描述（如 "a mountain bike"），与 5 个模板（如 "a photo of a used {}."）组合成 25–35 句，分别编码、归一化后取均值。每类另有一句 `single`，仅用于消融实验。
- **分类清单**：只放可挂商品的叶子分类（种子数据里的二级分类，以及没有子分类的一级分类：服饰鞋包、运动户外、美妆个护、乐器文娱）。key 为 `category.id`。
- 后端运行时还会读 `category` 表：停用的分类不参与打分（概率在剩余分类中重新归一化），名称以数据库为准。表里新增了分类但向量文件里没有时，启动日志会提示。

## 3. 新增或修改分类后

1. 编辑 `category_prompts.json`（新增 key = 新分类的 id）。
2. `python3 scripts/ai/build_text_embeddings.py`（或重新运行 `download_model.sh`，已下载的模型会跳过）。
3. 重启后端，或等加载失败后的重试间隔过去。

## 4. 后端配置（`campus.ai.clip.*`，都有默认值）

| 配置 | 默认 | 说明 |
|---|---|---|
| `enabled` | true | 总开关（环境变量 `CAMPUS_AI_CLIP_ENABLED`） |
| `model-dir` | `../scripts/ai/models/clip-vit-base-patch32` | 相对后端工作目录 `backend/`，Docker 部署时挂载目录并用 `CAMPUS_AI_CLIP_MODEL_DIR` 指定 |
| `vision-model` | `vision_model.onnx` | 可改 `vision_model_quantized.onnx`（`CAMPUS_AI_CLIP_VISION_MODEL`） |
| `embeddings` | `category_embeddings.json` | 文本向量文件 |
| `prompt-ensemble` | true | false 时用单提示词向量（仅消融用） |
| `logit-scale` | 100 | softmax 温度 |
| `top-k` | 3 | 候选数 |
| `preload` | true | provider=clip 时启动后异步预加载，否则首次调用时懒加载 |
| `intra-op-threads` / `max-concurrent` | 2 / 2 | 单次推理线程数 / 同时推理数（多出的请求排队） |
| `acquire-timeout-ms` | 5000 | 排队超时，超时按不可用处理 |
| `retry-interval-seconds` | 300 | 加载失败后多久重试，补齐模型后无需重启 |
| `max-pixels` | 80000000 | 解码前的像素上限（防解压炸弹） |
| `category-refresh-seconds` | 60 | 分类表缓存时间 |

## 5. 一致性校验

后端 Java 预处理按 Pillow 的 BICUBIC 重采样算法实现，`ImagePreprocessorTest.matchesPillowReference` 用固定图案与 PIL 结果对比。
在 108 张实验样本上，Java 与 Python 的 fp32 图像向量余弦均值 0.9999995，Top1 完全一致（见 `experiments/image_classify/results/results.md`）。
