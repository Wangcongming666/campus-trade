package com.campus.trade.experiments.recommend;

import com.campus.trade.recommend.core.Behavior;
import com.campus.trade.recommend.core.BehaviorWeightConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 实验数据集：行为 + 物品分类（可为 null）+ 用户注册兴趣（可为空）+ 行为权重配置。 */
final class Dataset {

    final String id;
    final List<Behavior> behaviors;
    final Map<Long, Long> itemCategory;
    final Map<Long, List<Long>> userInterests;
    final BehaviorWeightConfig weightConfig;
    /** 是否有多种行为类型（MovieLens 只有"评过分"一种，行为加权不适用） */
    final boolean multiBehavior;

    Dataset(String id, List<Behavior> behaviors, Map<Long, Long> itemCategory, Map<Long, List<Long>> userInterests,
            BehaviorWeightConfig weightConfig, boolean multiBehavior) {
        this.id = id;
        this.behaviors = behaviors;
        this.itemCategory = itemCategory;
        this.userInterests = userInterests;
        this.weightConfig = weightConfig;
        this.multiBehavior = multiBehavior;
    }

    /**
     * MovieLens-100K：u.data（user \t item \t rating \t timestamp），任何评分都视为一次隐式交互（二值）。
     * 物品分类 = u.item 中第一个为 1 的电影类型（跳过 unknown，除非只有 unknown）；没有注册兴趣。
     */
    static Dataset movieLens(Path dir) throws IOException {
        List<Behavior> list = new ArrayList<>(100_000);
        try (BufferedReader r = Files.newBufferedReader(dir.resolve("u.data"), StandardCharsets.ISO_8859_1)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t");
                list.add(new Behavior(Long.parseLong(f[0]), Long.parseLong(f[1]), BehaviorWeightConfig.BROWSE,
                        Long.parseLong(f[3]) * 1000L));
            }
        }
        Map<Long, Long> genre = new HashMap<>();
        try (BufferedReader r = Files.newBufferedReader(dir.resolve("u.item"), StandardCharsets.ISO_8859_1)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] f = line.split("\\|", -1);
                long g = 0; // unknown
                for (int k = 1; k <= 18; k++) {
                    if ("1".equals(f[5 + k])) {
                        g = k;
                        break;
                    }
                }
                genre.put(Long.parseLong(f[0]), g);
            }
        }
        return new Dataset("ml100k", list, genre, Map.of(), BehaviorWeightConfig.binary(), false);
    }

    /** 模拟校园二手数据（scripts/gen_campus_data.py 生成）。行为按 浏览1/收藏3/聊天4/下单5 加权 + 对数压缩。 */
    static Dataset campus(Path dir) throws IOException {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        List<Behavior> list = new ArrayList<>();
        for (String[] f : csv(dir.resolve("user_behavior.csv"))) {
            long t = LocalDateTime.parse(f[4], fmt).atZone(zone).toInstant().toEpochMilli();
            list.add(new Behavior(Long.parseLong(f[1]), Long.parseLong(f[2]), Integer.parseInt(f[3]), t));
        }
        Map<Long, Long> cat = new HashMap<>();
        for (String[] f : csv(dir.resolve("goods.csv"))) {
            cat.put(Long.parseLong(f[0]), Long.parseLong(f[2]));
        }
        Map<Long, List<Long>> interests = new HashMap<>();
        for (String[] f : csv(dir.resolve("users.csv"))) {
            List<Long> cs = new ArrayList<>();
            for (String s : f[1].split("\\|")) {
                if (!s.isBlank()) {
                    cs.add(Long.parseLong(s));
                }
            }
            interests.put(Long.parseLong(f[0]), cs);
        }
        return new Dataset("campus", list, cat, interests, BehaviorWeightConfig.defaults(), true);
    }

    private static List<String[]> csv(Path p) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line = r.readLine();
            while ((line = r.readLine()) != null) {
                if (!line.isBlank()) {
                    rows.add(line.split(",", -1));
                }
            }
        }
        return rows;
    }
}
