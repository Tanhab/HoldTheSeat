package com.tanhab.holdtheseat.seat;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Started once per JVM and reused by every subclass, so all three containers boot one time
 * per test run. {@code @ServiceConnection} wires each container's connection details into
 * the matching auto-configuration, which is why no test datasource, Redis host or bootstrap
 * server is configured anywhere.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @ServiceConnection
    protected static final RedisContainer REDIS = new RedisContainer("redis:8-alpine");

    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
    }

}
