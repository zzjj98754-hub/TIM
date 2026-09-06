package com.tuling.tim.server.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryRepositoryTest {

    @Test
    void leaseCanBeClaimedOnlyOnceUntilItExpires() {
        JdbcTemplate jdbc = database();
        DeliveryRepository repository = new DeliveryRepository(jdbc, new ObjectMapper());
        insertDueDelivery(jdbc, "message-1");

        List<DeliveryRepository.DeliveryCandidate> first = repository.claimDue(10, "worker-a", 60_000);
        List<DeliveryRepository.DeliveryCandidate> competing = repository.claimDue(10, "worker-b", 60_000);

        assertEquals(1, first.size());
        assertTrue(competing.isEmpty());
        assertEquals("worker-a", jdbc.queryForObject("SELECT lease_owner FROM message_delivery WHERE message_id='message-1'", String.class));

        jdbc.update("UPDATE message_delivery SET lease_until=? WHERE message_id='message-1'", Timestamp.from(Instant.now().minusSeconds(1)));
        assertEquals(1, repository.claimDue(10, "worker-b", 60_000).size());
        assertEquals("worker-b", jdbc.queryForObject("SELECT lease_owner FROM message_delivery WHERE message_id='message-1'", String.class));
    }

    @Test
    void exposesDurablePendingCountAndAge() {
        JdbcTemplate jdbc = database();
        DeliveryRepository repository = new DeliveryRepository(jdbc, new ObjectMapper());
        insertDueDelivery(jdbc, "message-2");

        assertEquals(1L, repository.pendingCount());
        assertTrue(repository.oldestPendingSeconds() >= 1D);
    }

    private JdbcTemplate database() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:delivery-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE im_message (message_id VARCHAR(64) PRIMARY KEY, body TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE message_delivery (message_id VARCHAR(64) NOT NULL, recipient_id BIGINT NOT NULL, status VARCHAR(16) NOT NULL, attempt_count INT NOT NULL DEFAULT 0, next_retry_at TIMESTAMP NOT NULL, lease_owner VARCHAR(128), lease_until TIMESTAMP, last_error VARCHAR(512), created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, acked_at TIMESTAMP, PRIMARY KEY(message_id, recipient_id))");
        return jdbc;
    }

    private void insertDueDelivery(JdbcTemplate jdbc, String messageId) {
        jdbc.update("INSERT INTO im_message(message_id, body) VALUES (?, ?)", messageId,
                "{\"messageId\":\"" + messageId + "\",\"toUserId\":2}");
        Timestamp past = Timestamp.from(Instant.now().minusSeconds(2));
        jdbc.update("INSERT INTO message_delivery(message_id, recipient_id, status, next_retry_at, created_at, updated_at) VALUES (?, 2, 'PENDING', ?, ?, ?)",
                messageId, past, past, past);
    }
}
