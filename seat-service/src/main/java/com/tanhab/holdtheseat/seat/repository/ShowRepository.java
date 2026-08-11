package com.tanhab.holdtheseat.seat.repository;

import com.tanhab.holdtheseat.seat.domain.Show;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ShowRepository {

    private static final String COLUMNS = "id, name, starts_at, created_at";

    private static final RowMapper<Show> SHOW_MAPPER = (rs, rowNum) -> new Show(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getTimestamp("starts_at").toInstant(),
            rs.getTimestamp("created_at").toInstant()
    );

    private static final RowMapper<UUID> ID_MAPPER = (rs, rowNum) -> rs.getObject("id", UUID.class);

    private final JdbcClient jdbcClient;

    public ShowRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Show> findById(UUID showId) {
        return jdbcClient.sql("SELECT %s FROM shows WHERE id = :id".formatted(COLUMNS))
                .param("id", showId)
                .query(SHOW_MAPPER)
                .optional();
    }

    public List<UUID> findAllIds() {
        return jdbcClient.sql("SELECT id FROM shows")
                .query(ID_MAPPER)
                .list();
    }

}
