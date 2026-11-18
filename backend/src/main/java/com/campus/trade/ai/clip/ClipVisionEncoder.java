package com.campus.trade.ai.clip;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Map;

/**
 * CLIP 图像编码器（ONNX Runtime，CPU）。输入 pixel_values [1,3,224,224]，输出 image_embeds [1,512]。
 * OrtSession.run 本身线程安全，单个实例可被多线程共享。
 */
public final class ClipVisionEncoder implements AutoCloseable {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;
    private final String outputName;

    public ClipVisionEncoder(Path modelFile, int intraOpThreads) throws OrtException {
        this.env = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            opts.setIntraOpNumThreads(Math.max(1, intraOpThreads));
            opts.setInterOpNumThreads(1);
            this.session = env.createSession(modelFile.toString(), opts);
        }
        this.inputName = session.getInputNames().contains("pixel_values")
                ? "pixel_values" : session.getInputNames().iterator().next();
        this.outputName = session.getOutputNames().contains("image_embeds")
                ? "image_embeds" : session.getOutputNames().iterator().next();
    }

    /** 编码一张已预处理的图片（长度 3*224*224），返回图像向量（未归一化）。 */
    public float[] encode(float[] pixels) throws OrtException {
        long[] shape = {1, 3, ImagePreprocessor.SIZE, ImagePreprocessor.SIZE};
        try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(pixels), shape);
             OrtSession.Result r = session.run(Map.of(inputName, input), java.util.Set.of(outputName))) {
            float[][] out = (float[][]) r.get(0).getValue();
            return out[0];
        }
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }
}
