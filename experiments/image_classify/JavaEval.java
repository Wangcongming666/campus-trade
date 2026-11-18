import com.campus.trade.ai.clip.CategoryEmbeddings;
import com.campus.trade.ai.clip.ClipVisionEncoder;
import com.campus.trade.ai.clip.ImagePreprocessor;
import com.campus.trade.ai.clip.ZeroShotScorer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 用后端的 Java 实现（com.campus.trade.ai.clip，与线上同一套代码）在小样本上跑一遍，
 * 输出逐张预测、图像向量和耗时，供 compare_java.py 与 Python 参考实现对比。
 * 运行方式见 run_all.sh。参数：modelDir visionModelFile threads
 */
public class JavaEval {
    public static void main(String[] args) throws Exception {
        Path here = Path.of(".").toAbsolutePath().normalize();
        Path modelDir = Path.of(args[0]);
        String vision = args.length > 1 ? args[1] : "vision_model.onnx";
        int threads = args.length > 2 ? Integer.parseInt(args[2]) : 2;
        CategoryEmbeddings emb;
        try (InputStream in = Files.newInputStream(modelDir.resolve("category_embeddings.json"))) {
            emb = CategoryEmbeddings.load(in, true);
        }
        ImagePreprocessor pre = new ImagePreprocessor(80_000_000L);
        Path out = here.resolve("results");
        Files.createDirectories(out);
        String tag = vision.contains("quantized") ? "int8" : "fp32";
        try (ClipVisionEncoder enc = new ClipVisionEncoder(modelDir.resolve(vision), threads);
             BufferedReader r = Files.newBufferedReader(here.resolve("samples.csv"), StandardCharsets.UTF_8);
             PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out.resolve("java_predictions_" + tag + ".csv")));
             PrintWriter ew = new PrintWriter(Files.newBufferedWriter(out.resolve("java_embeds_" + tag + ".csv")))) {
            enc.encode(new float[3 * 224 * 224]); // 预热
            pw.println("file,truthId,top1Id,top1Conf,top2Id,top3Id,decodePreprocessMs,inferMs");
            String header = r.readLine();
            List<String> lines = new ArrayList<>();
            for (String l; (l = r.readLine()) != null; ) {
                lines.add(l);
            }
            for (String l : lines) {
                String[] f = l.split(",", -1);
                byte[] bytes = Files.readAllBytes(here.resolve("images").resolve(f[0]));
                long t0 = System.nanoTime();
                float[] px = pre.preprocess(bytes);
                long t1 = System.nanoTime();
                float[] e = enc.encode(px);
                long t2 = System.nanoTime();
                List<ZeroShotScorer.Scored> top = ZeroShotScorer.score(e, emb.entries(), 100, 3);
                pw.printf(Locale.ROOT, "%s,%s,%d,%.4f,%d,%d,%.2f,%.2f%n", f[0], f[1],
                        top.get(0).category().categoryId(), top.get(0).probability(),
                        top.get(1).category().categoryId(), top.get(2).category().categoryId(),
                        (t1 - t0) / 1e6, (t2 - t1) / 1e6);
                StringBuilder sb = new StringBuilder(f[0]);
                for (float v : ZeroShotScorer.l2normalize(e)) {
                    sb.append(',').append(v);
                }
                ew.println(sb);
            }
            System.out.println("Java 评估完成：" + lines.size() + " 张，" + tag + "，header=" + header.substring(0, 20) + "…");
        }
    }
}
