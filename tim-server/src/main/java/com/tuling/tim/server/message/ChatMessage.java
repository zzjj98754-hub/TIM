package com.tuling.tim.server.message;

/** JSON body carried by a CHAT/GROUP_CHAT frame and persisted for the demo. */
public class ChatMessage {
    private String messageId;
    private String clientMessageId;
    private long fromUserId;
    private long toUserId;
    private long groupId;
    private String content;
    private long createdAt;

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getClientMessageId() { return clientMessageId; }
    public void setClientMessageId(String clientMessageId) { this.clientMessageId = clientMessageId; }
    public long getFromUserId() { return fromUserId; }
    public void setFromUserId(long fromUserId) { this.fromUserId = fromUserId; }
    public long getToUserId() { return toUserId; }
    public void setToUserId(long toUserId) { this.toUserId = toUserId; }
    public long getGroupId() { return groupId; }
    public void setGroupId(long groupId) { this.groupId = groupId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
