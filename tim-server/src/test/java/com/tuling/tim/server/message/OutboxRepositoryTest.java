package com.tuling.tim.server.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxRepositoryTest {

    @Test
    void claimUsesLeaseAndRetryCanMoveEventToDead() {
        JdbcTemplate jdbc = database();
        OutboxRepository repository = new OutboxRepository(jdbc, new ObjectMapper());
        Timestamp past = Timestamp.from(Instant.now().minusSeconds(1));
        jdbc.update("INSERT INTO outbox_event(event_id,event_type,message_id,payload,status,retry_count,next_retry_at,created_at) VALUES ('event-1','PRIVATE_MESSAGE_CREATED','message-1','{}','PENDING',0,?,?)", past, past);

        assertEquals(1, repository.claimPending(10).size());
        assertTrue(repository.claimPending(10).isEmpty());
        assertEquals(1L, repository.pendingCount());

        repository.retry("event-1", "test failure", 1);

        assertEquals(0L, repository.pendingCount());
        assertEquals(1L, repository.deadCount());
    }

    private JdbcTemplate database() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:outbox-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE outbox_event (event_id VARCHAR(64) PRIMARY KEY, event_type VARCHAR(64) NOT NULL, message_id VARCHAR(64) NOT NULL, payload TEXT NOT NULL, status VARCHAR(16) NOT NULL, retry_count INT NOT NULL DEFAULT 0, next_retry_at TIMESTAMP NOT NULL, created_at TIMESTAMP NOT NULL, sent_at TIMESTAMP, last_error VARCHAR(512))");
        return jdbc;
    }
}
