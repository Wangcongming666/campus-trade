package com.campus.trade.recommend.task;

import com.campus.trade.recommend.config.RecommendProperties;
import com.campus.trade.recommend.service.RecommendOfflineService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 离线相似度矩阵 + 热度榜定时计算（默认每天 03:30，campus.recommend.cron 可改）。 */
@Component
@RequiredArgsConstructor
public class RecommendScheduleTask {

    private final RecommendOfflineService offlineService;
    private final RecommendProperties props;

    @Scheduled(cron = "${campus.recommend.cron:0 30 3 * * ?}", zone = "Asia/Shanghai")
    public void nightly() {
        offlineService.rebuild();
    }

    /**
     * 启动后在独立线程跑一次，保证新部署时就有模型（Redis 中已有当前版本则跳过）。
     * 不用 @Async：避免占用行为日志线程池 behaviorExecutor。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (props.isBuildOnStartup()) {
            Thread t = new Thread(offlineService::rebuildIfAbsent, "recommend-startup");
            t.setDaemon(true);
            t.start();
        }
    }
}
