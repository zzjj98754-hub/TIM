CREATE TABLE IF NOT EXISTS im_message (
  message_id VARCHAR(64) PRIMARY KEY,
  client_message_id VARCHAR(128) NOT NULL,
  from_user_id BIGINT NOT NULL,
  to_user_id BIGINT NOT NULL,
  group_id BIGINT DEFAULT 0,
  body TEXT NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_im_message_recipient ON im_message(to_user_id, created_at);
CREATE UNIQUE INDEX IF NOT EXISTS uk_im_message_client ON im_message(from_user_id, client_message_id);
CREATE TABLE IF NOT EXISTS outbox_event (
  event_id VARCHAR(64) PRIMARY KEY,
  event_type VARCHAR(64) NOT NULL,
  message_id VARCHAR(64) NOT NULL,
  payload TEXT NOT NULL,
  status VARCHAR(16) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL,
  sent_at TIMESTAMP,
  last_error VARCHAR(512)
);
CREATE TABLE IF NOT EXISTS im_group (group_id BIGINT PRIMARY KEY, name VARCHAR(128) NOT NULL, created_at TIMESTAMP NOT NULL);
CREATE TABLE IF NOT EXISTS group_member (group_id BIGINT NOT NULL, user_id BIGINT NOT NULL, joined_at TIMESTAMP NOT NULL, PRIMARY KEY(group_id, user_id));
CREATE TABLE IF NOT EXISTS group_message (message_id VARCHAR(64) PRIMARY KEY, group_id BIGINT NOT NULL, sender_id BIGINT NOT NULL, content TEXT NOT NULL, created_at TIMESTAMP NOT NULL);
CREATE TABLE IF NOT EXISTS group_message_inbox (group_id BIGINT NOT NULL, user_id BIGINT NOT NULL, message_id VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL, PRIMARY KEY(group_id, user_id, message_id));
CREATE TABLE IF NOT EXISTS group_member_cursor (group_id BIGINT NOT NULL, user_id BIGINT NOT NULL, last_read_sequence BIGINT NOT NULL DEFAULT 0, PRIMARY KEY(group_id, user_id));
CREATE TABLE IF NOT EXISTS offline_message_index (user_id BIGINT NOT NULL, delivery_cursor BIGINT NOT NULL, message_id VARCHAR(64) NOT NULL, PRIMARY KEY(user_id, delivery_cursor), UNIQUE(user_id, message_id));
