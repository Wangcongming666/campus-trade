package com.campus.trade;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

/**
 * 测试库初始化：读取 ../sql/schema.sql 与 ../sql/seed.sql（不修改原文件），
 * 把库名替换为 campus_trade_test 后执行；同时清空 Redis db 1。每个 Spring 上下文只执行一次。
 */
@TestConfiguration
public class TestDatabaseInitializer {

    private static volatile boolean initialized;

    private final DataSource dataSource;
    private final StringRedisTemplate redis;

    public TestDatabaseInitializer(DataSource dataSource, StringRedisTemplate redis) {
        this.dataSource = dataSource;
        this.redis = redis;
    }

    @PostConstruct
    public void init() throws Exception {
        synchronized (TestDatabaseInitializer.class) {
            if (initialized) {
                return;
            }
            try (Connection c = dataSource.getConnection()) {
                run(c, "../sql/schema.sql");
                run(c, "../sql/seed.sql");
                c.createStatement().execute("USE `campus_trade_test`");
            }
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            initialized = true;
        }
    }

    private void run(Connection c, String file) throws Exception {
        String sql = Files.readString(Path.of(file), StandardCharsets.UTF_8)
                .replace("`campus_trade`", "`campus_trade_test`");
        ScriptUtils.executeSqlScript(c, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
    }
}
