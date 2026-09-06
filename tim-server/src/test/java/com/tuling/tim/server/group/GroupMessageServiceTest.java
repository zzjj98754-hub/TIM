package com.tuling.tim.server.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuling.tim.server.message.ChatMessage;
import com.tuling.tim.server.message.OutboxRepository;
import com.tuling.tim.server.message.ReliableMessageService;
import com.tuling.tim.server.message.SnowflakeIdGenerator;
import com.tuling.tim.server.mq.NodeMessageBus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class GroupMessageServiceTest {

    @Test
    void ordinaryGroupBatchWritesInboxAndRecordsDuration() {
        Fixture fixture = fixture(500);
        fixture.addMembers(10L, 1L, 2L);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        fixture.service.setMetrics(metrics);

        ChatMessage message = message(10L, 1L);
        assertEquals("WRITE_FANOUT", fixture.service.send(message));

        assertEquals(2, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM group_message_inbox", Integer.class));
        assertEquals(1, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class));
        assertEquals(1L, message.getGroupSequence());
        assertEquals(1L, metrics.get("tim_group_fanout_duration").timer().count());
    }

    @Test
    void largeGroupCommitSurvivesMissingRedisProjectionAndPullsFromMysql() {
        Fixture fixture = fixture(2);
        fixture.addMembers(20L, 1L, 2L);
        when(fixture.redis.opsForZSet()).thenThrow(new RedisConnectionFailureException("test outage"));

        ChatMessage message = message(20L, 1L);
        assertEquals("READ_FANOUT", fixture.service.send(message));

        assertEquals(1, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM group_message", Integer.class));
        assertEquals(0, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM group_message_inbox", Integer.class));
        assertEquals(1, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class));
        List<String> pulled = fixture.service.pull(20L, 2L, 0L, 10);
        assertEquals(1, pulled.size());
        assertTrue(pulled.get(0).contains("hello"));
    }

    @Test
    void concurrentTransactionsAllocateDistinctMonotonicSequences() throws Exception {
        Fixture fixture = fixture(500);
        fixture.addMembers(30L, 1L);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(fixture.source));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        ChatMessage first = message(30L, 1L);
        ChatMessage second = message(30L, 1L);
        try {
            Future<?> one = workers.submit(() -> runAfter(start, () -> transactions.executeWithoutResult(status -> fixture.service.send(first))));
            Future<?> two = workers.submit(() -> runAfter(start, () -> transactions.executeWithoutResult(status -> fixture.service.send(second))));
            start.countDown();
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
        }

        assertEquals(Set.of(1L, 2L), Set.of(first.getGroupSequence(), second.getGroupSequence()));
        assertEquals(2, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM group_message", Integer.class));
    }

    @Test
    void outboxFailureRollsBackMessageAndInboxInSameTransaction() {
        Fixture fixture = fixture(500);
        fixture.addMembers(40L, 1L, 2L);
        OutboxRepository failingOutbox = mock(OutboxRepository.class);
        doThrow(new IllegalStateException("outbox unavailable")).when(failingOutbox).appendGroup(org.mockito.ArgumentMatchers.any());
        GroupMessageService service = new GroupMessageService(fixture.redis, mock(ReliableMessageService.class), new ObjectMapper(), fixture.jdbc,
                mock(NodeMessageBus.class), failingOutbox, new GroupFanoutStrategySelector(500), new SnowflakeIdGenerator());
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(fixture.source));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> transactions.executeWithoutResult(status -> service.send(message(40L, 1L))));

        assertEquals(0, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM group_message", Integer.class));
        assertEquals(0, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM group_message_inbox", Integer.class));
        assertEquals(0, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM group_sequence", Integer.class));
    }

    private Fixture fixture(int threshold) {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:group-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE group_member (group_id BIGINT NOT NULL, user_id BIGINT NOT NULL, joined_at TIMESTAMP NOT NULL, PRIMARY KEY(group_id,user_id))");
        jdbc.execute("CREATE TABLE group_sequence (group_id BIGINT PRIMARY KEY, next_sequence BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE group_message (message_id VARCHAR(64) PRIMARY KEY, group_id BIGINT NOT NULL, group_sequence BIGINT NOT NULL, sender_id BIGINT NOT NULL, content TEXT NOT NULL, created_at TIMESTAMP NOT NULL, UNIQUE(group_id,group_sequence))");
        jdbc.execute("CREATE TABLE group_message_inbox (group_id BIGINT NOT NULL, user_id BIGINT NOT NULL, message_id VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL, PRIMARY KEY(group_id,user_id,message_id))");
        jdbc.execute("CREATE TABLE outbox_event (event_id VARCHAR(64) PRIMARY KEY, event_type VARCHAR(64) NOT NULL, message_id VARCHAR(64) NOT NULL, payload TEXT NOT NULL, status VARCHAR(16) NOT NULL, retry_count INT NOT NULL DEFAULT 0, next_retry_at TIMESTAMP NOT NULL, created_at TIMESTAMP NOT NULL, sent_at TIMESTAMP, last_error VARCHAR(512))");
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ObjectMapper json = new ObjectMapper();
        OutboxRepository outbox = new OutboxRepository(jdbc, json);
        GroupMessageService service = new GroupMessageService(redis, mock(ReliableMessageService.class), json, jdbc,
                mock(NodeMessageBus.class), outbox, new GroupFanoutStrategySelector(threshold), new SnowflakeIdGenerator());
        return new Fixture(source, jdbc, redis, service);
    }

    private ChatMessage message(long groupId, long senderId) {
        ChatMessage message = new ChatMessage();
        message.setGroupId(groupId);
        message.setFromUserId(senderId);
        message.setContent("hello");
        return message;
    }

    private void runAfter(CountDownLatch start, Runnable action) {
        try {
            start.await();
            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private record Fixture(DriverManagerDataSource source, JdbcTemplate jdbc, StringRedisTemplate redis, GroupMessageService service) {
        void addMembers(long groupId, long... userIds) {
            for (long userId : userIds) {
                jdbc.update("INSERT INTO group_member(group_id,user_id,joined_at) VALUES (?,?,CURRENT_TIMESTAMP)", groupId, userId);
            }
        }
    }
}
