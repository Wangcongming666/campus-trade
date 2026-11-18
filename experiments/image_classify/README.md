# experiments/image_classify · 图片自动分类小样本评估

评估后端 CLIP ViT-B/32 零样本分类在本平台 14 个叶子分类上的效果，并做消融（单提示词 vs 提示词集成，fp32 vs int8）。
**结果表见 `results/results.md`**，所有数字都来自真实运行。

## 数据

- 来源：Wikimedia Commons，只取自由许可（CC0 / Public domain / CC BY / CC BY-SA）。逐张来源页、许可、作者见 `samples.csv`，引用或展示图片时须按各自许可署名。
- 抽样：每个分类对应 2–3 个 Commons 分类（`sources.json`），合并后用固定种子 20261118 打乱，按顺序取前 20 张许可合格且能下载的作为候选（`candidates.json`，缩略图 500px）。
- 人工核对：在运行模型**之前**，按拼图（`sheets/`）剔除“主体不是该类商品实物”的候选（人物/场景为主、截图图表、书的内页扫描、零件特写等），剔除清单与原因见 `exclusions.json`；然后每类取前 8 张未剔除的。
- 最终 108 张：12 个分类各 8 张，专业教材 7 张，考研考证 5 张（Commons 上缺少此类图片，见下方局限）。
- 图片、拼图不进 git（`.gitignore`），用 `COLLECT=1 bash run_all.sh` 重新下载。Commons 上的文件可能被改名或删除，重新下载的结果可能略有不同。

## 复现

```bash
bash ../../scripts/ai/download_model.sh            # 下载模型、生成文本向量
pip install -r ../../scripts/ai/requirements.txt    # 或使用上一步建的 venv：PY=../../scripts/ai/models/.venv/bin/python
COLLECT=1 bash run_all.sh                          # 下载样本并评估；已有 images/ 时去掉 COLLECT=1
# 带后端 Java 复核（在后端源码副本里先 mvn compile 和 dependency:build-classpath）：
BACKEND_CLASSES=/path/to/backend/target/classes JAVA_CP="$(cat cp.txt)" bash run_all.sh
```

| 文件 | 说明 |
|---|---|
| `collect_samples.py` | 收集候选、生成拼图、按剔除清单生成 `samples.csv` |
| `eval_zero_shot.py` | Python 参考实现评估：Top1/Top3/一级 Top1、Wilson 置信区间、各类准确率、混淆矩阵、耗时 |
| `JavaEval.java` | 用后端 `com.campus.trade.ai.clip` 的代码跑同一批样本 |
| `compare_java.py` | Java 与 Python 结果对比（向量余弦、Top1 一致率） |
| `make_report.py` | 生成 `results/results.md` |
| `results/` | `metrics.json`、逐张预测 `predictions_*.csv`、混淆矩阵 `confusion_*.csv`、Java 预测 `java_predictions_*.csv` |

## 结论与局限（写论文时请如实说明）

1. 样本量小（108 张），Top1 的 95% 置信区间宽度约 ±8 个百分点；提示词集成相对单提示词的提升在配对 McNemar 检验下**不显著**（p 见 results.md）。写论文时应说“有提升趋势”，不能说“显著提升”。
2. 三个书籍二级分类（专业教材 / 考研考证 / 课外读物）主要靠封面文字区分，CLIP 图像特征难以区分，准确率低；但它们的一级分类“教材书籍”基本判对。建议产品上书籍类只自动建议到一级分类，二级由用户选择。
3. “考研考证”在 Commons 上没有合适图片，用的是**词典**类图片代替，与真实考研书有差异。
4. “运动户外”的 8 张样本都是哑铃（Commons 的羽毛球拍、篮球分类下没有直属图片文件），不能代表整个分类。
5. Commons 图片多为摄影作品，与学生在宿舍随手拍的二手商品照片风格不同，线上真实准确率可能偏低，上线后应用真实发布数据（用户最终选的分类）重新评估。
6. int8 量化模型在这批样本上数字略高，但差异在置信区间内；且 Java 与 Python 上的 int8 结果不完全一致。后端默认用 fp32（结果可复现），内存紧张时可切 int8。
7. 未评估中文 CLIP：Hugging Face 上 Chinese-CLIP 的 ONNX 版（Xenova/chinese-clip-vit-base-patch16）只有图文合一的整体模型，没有单独的图像编码器，接入需要额外拆分导出，本次未做。
