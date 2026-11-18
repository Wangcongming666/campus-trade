package com.campus.trade.recommend.config;

import com.campus.trade.recommend.core.BehaviorWeightConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 推荐模块配置入口：开启定时任务（后端主类未开启 @EnableScheduling，这里在推荐包内开启，不改动主类）。
 * 参数前缀 campus.recommend，全部有默认值，不配置也能运行。
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RecommendProperties.class)
public class RecommendConfig {

    /** 用户兴趣权重（1/3/4/5 + 对数压缩 + 兴趣时间衰减）。 */
    @Bean
    public BehaviorWeightConfig interestWeightConfig(RecommendProperties p) {
        return BehaviorWeightConfig.defaults()
                .withWeights(p.getWeights())
                .withHalfLifeDays(p.getInterestHalfLifeDays())
                .withMaxPerPair(p.getMaxInterestPerPair())
                .withLogScale(p.isLogScale());
    }
}
