package com.tanhab.holdtheseat.seat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

class SeatServiceApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private LettuceConnectionFactory connectionFactory;

    @Test
    void contextLoads() {
    }

    @Test
    void migrationsSeedTheDemoShow() {
        Integer seats = jdbcClient.sql("SELECT count(*) FROM seats WHERE show_id = :id")
                .param("id", java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .query(Integer.class)
                .single();

        assertThat(seats).isEqualTo(10);
    }

    // Lettuce connects lazily, so a context that boots proves nothing about Redis. Without
    // this the suite would pass against a developer's local compose stack on port 6379.
    @Test
    void redisIsTheContainerAndNotTheLocalStack() {
        assertThat(connectionFactory.getPort()).isEqualTo(REDIS.getFirstMappedPort());
    }

    @Test
    void redisRoundTripsAString() {
        redis.opsForValue().set("hts:probe", "ok");

        assertThat(redis.opsForValue().get("hts:probe")).isEqualTo("ok");
    }

}
