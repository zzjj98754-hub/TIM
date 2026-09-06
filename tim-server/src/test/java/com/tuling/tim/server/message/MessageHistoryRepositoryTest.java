package com.tuling.tim.server.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageHistoryRepositoryTest {

    @Test
    void ackRequiresRecipientAndLegalPriorStatus() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:history-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE im_message (message_id VARCHAR(64) PRIMARY KEY, client_message_id VARCHAR(128) NOT NULL, from_user_id BIGINT NOT NULL, to_user_id BIGINT NOT NULL, group_id BIGINT NOT NULL, body TEXT NOT NULL, status VARCHAR(16) NOT NULL, created_at BIGINT NOT NULL, acked_at TIMESTAMP)");
        jdbc.update("INSERT INTO im_message VALUES ('message-1','client-1',1,2,0,'{}','PENDING',1,NULL)");
        MessageHistoryRepository repository = new MessageHistoryRepository(jdbc, new ObjectMapper());

        assertFalse(repository.acknowledge("message-1", 3L));
        assertFalse(repository.acknowledge("message-1", 2L));
        repository.markDelivering("message-1", 2L);
        assertTrue(repository.acknowledge("message-1", 2L));
        assertFalse(repository.acknowledge("message-1", 2L));
        assertNotNull(jdbc.queryForObject("SELECT acked_at FROM im_message WHERE message_id='message-1'", java.sql.Timestamp.class));
    }

    @Test
    void offlineCursorIsDatabaseAllocatedAndIdempotentAcrossWorkers() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:offline-index-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE im_message (message_id VARCHAR(64) PRIMARY KEY, status VARCHAR(16) NOT NULL)");
        jdbc.execute("CREATE TABLE offline_message_index (user_id BIGINT NOT NULL, delivery_cursor BIGINT NOT NULL, message_id VARCHAR(64) NOT NULL, PRIMARY KEY(user_id,delivery_cursor), UNIQUE(user_id,message_id))");
        jdbc.execute("CREATE TABLE offline_cursor_sequence (user_id BIGINT PRIMARY KEY, next_cursor BIGINT NOT NULL)");
        for (String id : java.util.List.of("message-a", "message-b", "message-c", "same-message")) {
            jdbc.update("INSERT INTO im_message(message_id,status) VALUES (?, 'PENDING')", id);
        }
        MessageHistoryRepository repository = new MessageHistoryRepository(jdbc, new ObjectMapper());
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(source));

        assertTrue(transactions.execute(status -> repository.indexOffline(7L, "message-a")) == 1L);
        assertTrue(transactions.execute(status -> repository.indexOffline(7L, "message-a")) == 1L);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Long> first = workers.submit(() -> allocateAfter(start, transactions, repository, 7L, "message-b"));
            Future<Long> second = workers.submit(() -> allocateAfter(start, transactions, repository, 7L, "message-c"));
            start.countDown();
            assertEquals(Set.of(2L, 3L), Set.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)));
        } finally {
            workers.shutdownNow();
        }
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM offline_message_index WHERE user_id=7", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM im_message WHERE message_id IN ('message-a','message-b','message-c') AND status='OFFLINE'", Integer.class));

        CountDownLatch duplicateStart = new CountDownLatch(1);
        ExecutorService duplicateWorkers = Executors.newFixedThreadPool(2);
        try {
            Future<Long> first = duplicateWorkers.submit(() -> allocateAfter(duplicateStart, transactions, repository, 8L, "same-message"));
            Future<Long> second = duplicateWorkers.submit(() -> allocateAfter(duplicateStart, transactions, repository, 8L, "same-message"));
            duplicateStart.countDown();
            assertEquals(1L, first.get(10, TimeUnit.SECONDS));
            assertEquals(1L, second.get(10, TimeUnit.SECONDS));
        } finally {
            duplicateWorkers.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM offline_message_index WHERE user_id=8", Integer.class));
    }

    private long allocateAfter(CountDownLatch start, TransactionTemplate transactions,
                               MessageHistoryRepository repository, long userId, String messageId) throws Exception {
        start.await();
        Long cursor = transactions.execute(status -> repository.indexOffline(userId, messageId));
        return cursor == null ? 0L : cursor;
    }
}
