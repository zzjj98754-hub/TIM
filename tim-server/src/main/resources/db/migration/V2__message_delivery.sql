CREATE TABLE IF NOT EXISTS message_delivery (
  message_id VARCHAR(64) NOT NULL,
  recipient_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  next_retry_at TIMESTAMP NOT NULL,
  lease_owner VARCHAR(128),
  lease_until TIMESTAMP NULL,
  last_error VARCHAR(512),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  acked_at TIMESTAMP NULL,
  PRIMARY KEY(message_id, recipient_id),
  KEY idx_message_delivery_due(status, next_retry_at)
);
