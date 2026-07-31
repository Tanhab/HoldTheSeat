package com.tanhab.holdtheseat.booking;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Started once per JVM and reused by every subclass, so Postgres boots one time per
 * test run. {@code @ServiceConnection} wires the container's url and credentials into
 * the DataSource, which is why no test datasource is configured anywhere.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static {
        POSTGRES.start();
    }

}
