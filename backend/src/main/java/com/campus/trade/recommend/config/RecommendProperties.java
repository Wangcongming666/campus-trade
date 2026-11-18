package com.campus.trade.recommend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 推荐模块配置，前缀 campus.recommend，全部有默认值；需要调整时在 application.yml 追加即可。
 */
@ConfigurationProperties(prefix = "campus.recommend")
public class RecommendProperties {

    /** 行为权重：behavior_type → 权重（浏览1/收藏3/聊天4/下单5） */
    private Map<Integer, Double> weights = new HashMap<>(Map.of(1, 1.0, 2, 3.0, 3, 4.0, 4, 5.0));
    /** 兴趣时间衰减半衰期（天），<=0 关闭；离线实验为随机划分故未开启，线上建议 30 */
    private double interestHalfLifeDays = 30;
    /** 热度时间衰减半衰期（天） */
    private double hotHalfLifeDays = 7;
    /** 单个(用户,商品)兴趣上限 */
    private double maxInterestPerPair = 10;
    /** 兴趣是否做 ln(1+r) 对数压缩（离线实验验证有效） */
    private boolean logScale = true;
    /** ItemCF 每个商品保留的邻居数 K */
    private int topK = 40;
    /** 是否使用 IUF */
    private boolean iuf = true;
    /** 相似度是否按行最大值归一化 */
    private boolean normalize = true;
    /** 离线训练使用最近多少天的行为 */
    private int trainWindowDays = 180;
    /** 有效交互商品数少于该值视为冷启动用户 */
    private int coldStartThreshold = 5;
    /** 是否读取 user_interest 注册兴趣分类用于冷启动 */
    private boolean userInterestEnabled = true;
    /** 冷启动列表中兴趣分类热度所占比例 */
    private double coldInterestRatio = 0.8;
    /** 冷启动用户已有少量行为（有种子商品）时，ItemCF 邻居 / 内容相似结果最多占 size 的比例（向上取整），0 表示不用 */
    private double coldCfRatio = 0.1;
    /** 在线读取用户最近行为条数上限 */
    private int userBehaviorLimit = 300;
    /** "新商品"定义：发布不超过多少天（用于内容相似补齐） */
    private int newGoodsDays = 7;
    /** 内容相似候选池大小 */
    private int contentCandidateLimit = 500;
    /** 内容相似中分类一致的权重 α */
    private double contentCategoryWeight = 0.3;
    /** 全局/分类热度榜保存条数 */
    private int hotListSize = 500;
    /** 模型存储：redis | memory（单机调试） */
    private String store = "redis";
    /** Redis key 过期时间（小时），应大于离线任务周期 */
    private long keyTtlHours = 50;
    /** 离线任务 cron（默认每天 03:30） */
    private String cron = "0 30 3 * * ?";
    /** 启动后是否立即跑一次离线任务 */
    private boolean buildOnStartup = true;

    public Map<Integer, Double> getWeights() { return weights; }
    public void setWeights(Map<Integer, Double> weights) { this.weights = weights; }
    public double getInterestHalfLifeDays() { return interestHalfLifeDays; }
    public void setInterestHalfLifeDays(double v) { this.interestHalfLifeDays = v; }
    public double getHotHalfLifeDays() { return hotHalfLifeDays; }
    public void setHotHalfLifeDays(double v) { this.hotHalfLifeDays = v; }
    public double getMaxInterestPerPair() { return maxInterestPerPair; }
    public void setMaxInterestPerPair(double v) { this.maxInterestPerPair = v; }
    public boolean isLogScale() { return logScale; }
    public void setLogScale(boolean logScale) { this.logScale = logScale; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
    public boolean isIuf() { return iuf; }
    public void setIuf(boolean iuf) { this.iuf = iuf; }
    public boolean isNormalize() { return normalize; }
    public void setNormalize(boolean normalize) { this.normalize = normalize; }
    public int getTrainWindowDays() { return trainWindowDays; }
    public void setTrainWindowDays(int v) { this.trainWindowDays = v; }
    public int getColdStartThreshold() { return coldStartThreshold; }
    public void setColdStartThreshold(int v) { this.coldStartThreshold = v; }
    public boolean isUserInterestEnabled() { return userInterestEnabled; }
    public void setUserInterestEnabled(boolean v) { this.userInterestEnabled = v; }
    public double getColdInterestRatio() { return coldInterestRatio; }
    public void setColdInterestRatio(double v) { this.coldInterestRatio = v; }
    public double getColdCfRatio() { return coldCfRatio; }
    public void setColdCfRatio(double v) { this.coldCfRatio = v; }
    public int getUserBehaviorLimit() { return userBehaviorLimit; }
    public void setUserBehaviorLimit(int v) { this.userBehaviorLimit = v; }
    public int getNewGoodsDays() { return newGoodsDays; }
    public void setNewGoodsDays(int v) { this.newGoodsDays = v; }
    public int getContentCandidateLimit() { return contentCandidateLimit; }
    public void setContentCandidateLimit(int v) { this.contentCandidateLimit = v; }
    public double getContentCategoryWeight() { return contentCategoryWeight; }
    public void setContentCategoryWeight(double v) { this.contentCategoryWeight = v; }
    public int getHotListSize() { return hotListSize; }
    public void setHotListSize(int v) { this.hotListSize = v; }
    public String getStore() { return store; }
    public void setStore(String store) { this.store = store; }
    public long getKeyTtlHours() { return keyTtlHours; }
    public void setKeyTtlHours(long v) { this.keyTtlHours = v; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public boolean isBuildOnStartup() { return buildOnStartup; }
    public void setBuildOnStartup(boolean v) { this.buildOnStartup = v; }
}
