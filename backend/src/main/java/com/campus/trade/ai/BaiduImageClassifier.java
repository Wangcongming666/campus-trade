package com.campus.trade.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 百度图像识别「通用物体和场景识别」（advanced_general）实现（provider=baidu）。
 * API Key / Secret Key 从环境变量 BAIDU_AI_API_KEY / BAIDU_AI_SECRET_KEY 读取（也可用 campus.ai.baidu.* 配置覆盖）；
 * 未配置时 isAvailable()=false，接口返回 source=NONE（不降级 mock）。识别出的关键词经 {@link CategoryKeywordMatcher}
 * 映射为现有子分类，置信度取百度 score。
 */
@Slf4j
@Component
public class BaiduImageClassifier implements ImageClassifier {

    public static final String NAME = "baidu";
    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String CLASSIFY_URL = "https://aip.baidubce.com/rest/2.0/image-classify/v2/advanced_general";

    private final CategoryResolver resolver;
    private final ObjectMapper om;
    private final String apiKey;
    private final String secretKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile String token;
    private volatile long tokenExpireAt;

    public BaiduImageClassifier(CategoryResolver resolver, ObjectMapper om,
                                @Value("${campus.ai.baidu.api-key:${BAIDU_AI_API_KEY:}}") String apiKey,
                                @Value("${campus.ai.baidu.secret-key:${BAIDU_AI_SECRET_KEY:}}") String secretKey) {
        this.resolver = resolver;
        this.om = om;
        this.apiKey = apiKey;
        this.secretKey = secretKey;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(secretKey);
    }

    @Override
    public ClassifyResult classify(byte[] imageBytes, String filename) {
        try {
            String image = Base64.getEncoder().encodeToString(imageBytes);
            String body = "image=" + URLEncoder.encode(image, StandardCharsets.UTF_8) + "&baike_num=0";
            HttpRequest req = HttpRequest.newBuilder(URI.create(CLASSIFY_URL + "?access_token=" + accessToken()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            JsonNode resp = om.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
            if (resp.has("error_code")) {
                throw new IllegalStateException("baidu error " + resp.get("error_code") + ": " + resp.path("error_msg").asText());
            }
            return fromKeywords(resp.path("result"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException("baidu classify failed: " + e.getMessage(), e);
        }
    }

    /** 百度返回 result:[{keyword, root, score}] → 子分类候选（同分类取最高分）。包可见便于单元测试。 */
    ClassifyResult fromKeywords(JsonNode result) {
        Map<String, Double> best = new LinkedHashMap<>();
        for (JsonNode r : result) {
            String text = r.path("keyword").asText("") + " " + r.path("root").asText("");
            double score = r.path("score").asDouble(0);
            CategoryKeywordMatcher.match(text).keySet().forEach(cat -> best.merge(cat, score, Math::max));
        }
        List<ClassifyResult.Candidate> ranked = new ArrayList<>();
        best.forEach((cat, score) -> resolver.byName(cat, score).ifPresent(ranked::add));
        ranked.sort(Comparator.comparingDouble(ClassifyResult.Candidate::confidence).reversed());
        return ClassifyResult.of(ranked, NAME);
    }

    private synchronized String accessToken() throws Exception {
        if (token != null && System.currentTimeMillis() < tokenExpireAt) {
            return token;
        }
        String url = TOKEN_URL + "?grant_type=client_credentials&client_id="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8) + "&client_secret="
                + URLEncoder.encode(secretKey, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        JsonNode n = om.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
        if (!n.hasNonNull("access_token")) {
            throw new IllegalStateException("baidu token failed: " + n);
        }
        token = n.get("access_token").asText();
        long ttl = n.path("expires_in").asLong(2592000);
        tokenExpireAt = System.currentTimeMillis() + Math.max(60, ttl - 3600) * 1000;
        return token;
    }
}
