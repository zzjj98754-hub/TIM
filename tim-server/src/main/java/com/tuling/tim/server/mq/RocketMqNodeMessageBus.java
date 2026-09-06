package com.tuling.tim.server.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuling.tim.server.message.ChatMessage;
import com.tuling.tim.server.message.ReliableMessageService;
import com.tuling.tim.server.group.GroupChannelPushService;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Real RocketMQ transport. Set tim.mq.mode=rocketmq and a running nameserver to enable it. */
@Service
@ConditionalOnProperty(name = "tim.mq.mode", havingValue = "rocketmq")
public class RocketMqNodeMessageBus implements NodeMessageBus, SmartLifecycle {
    private final ObjectMapper json;
    private final ReliableMessageService messages;
    private final String namesrv;
    private final String serverId;
    private final GroupChannelPushService groups;
    private DefaultMQProducer producer;
    private DefaultMQPushConsumer consumer;
    private DefaultMQPushConsumer groupConsumer;
    private volatile boolean running;

    public RocketMqNodeMessageBus(ObjectMapper json, ReliableMessageService messages, GroupChannelPushService groups,
                                  @Value("${tim.mq.namesrv:127.0.0.1:9876}") String namesrv,
                                  @Value("${tim.server.id:im-server-1}") String serverId) {
        this.json = json; this.messages = messages; this.groups = groups; this.namesrv = namesrv; this.serverId = serverId;
    }
    @Override public void start() {
        try {
            producer = new DefaultMQProducer("tim-producer-" + serverId); producer.setNamesrvAddr(namesrv); producer.start();
            consumer = new DefaultMQPushConsumer("tim-node-" + serverId); consumer.setNamesrvAddr(namesrv);
            consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
            consumer.subscribe("TIM_NODE_MESSAGE", serverId);
            consumer.registerMessageListener((List<MessageExt> batch, ConsumeConcurrentlyContext context) -> {
                for (MessageExt item : batch) {
                    try { messages.receiveFromNode(json.readValue(item.getBody(), ChatMessage.class)); }
                    catch (Exception e) { return ConsumeConcurrentlyStatus.RECONSUME_LATER; }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            consumer.start();
            groupConsumer = new DefaultMQPushConsumer("tim-group-broadcast-" + serverId);
            groupConsumer.setNamesrvAddr(namesrv); groupConsumer.setMessageModel(MessageModel.BROADCASTING);
            groupConsumer.subscribe("TIM_GROUP_BROADCAST", "*");
            groupConsumer.registerMessageListener((List<MessageExt> batch, ConsumeConcurrentlyContext context) -> {
                for (MessageExt item : batch) {
                    try { groups.push(json.readValue(item.getBody(), ChatMessage.class)); }
                    catch (Exception e) { return ConsumeConcurrentlyStatus.RECONSUME_LATER; }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            groupConsumer.start(); running = true;
        } catch (Exception e) { throw new IllegalStateException("RocketMQ startup failed; use tim.mq.mode=local when broker is absent", e); }
    }
    @Override public void stop() { if (groupConsumer != null) groupConsumer.shutdown(); if (consumer != null) consumer.shutdown(); if (producer != null) producer.shutdown(); running = false; }
    @Override public boolean isRunning() { return running; }
    @Override public void forward(String targetServerId, ChatMessage message) {
        try { producer.send(new Message("TIM_NODE_MESSAGE", targetServerId, json.writeValueAsBytes(message))); }
        catch (Exception e) { throw new IllegalStateException("RocketMQ send failed", e); }
    }
    @Override public void broadcastGroup(ChatMessage message) {
        try { producer.send(new Message("TIM_GROUP_BROADCAST", "group-" + message.getGroupId(), json.writeValueAsBytes(message))); }
        catch (Exception e) { throw new IllegalStateException("RocketMQ group broadcast failed", e); }
    }
}
