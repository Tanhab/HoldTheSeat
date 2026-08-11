package com.tanhab.holdtheseat.seat;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Started once per JVM and reused by every subclass, so both containers boot one time per
 * test run. {@code @ServiceConnection} wires each container's connection details into the
 * matching auto-configuration, which is why no test datasource or Redis host is configured
 * anywhere.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @ServiceConnection
    static final RedisContainer REDIS = new RedisContainer("redis:8-alpine");

    static {
        POSTGRES.start();
        REDIS.start();
    }

}
