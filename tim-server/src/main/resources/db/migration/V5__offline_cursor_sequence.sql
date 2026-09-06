CREATE TABLE IF NOT EXISTS offline_cursor_sequence (
  user_id BIGINT PRIMARY KEY,
  next_cursor BIGINT NOT NULL
);

INSERT INTO offline_cursor_sequence (user_id, next_cursor)
SELECT user_id, MAX(delivery_cursor) + 1
FROM offline_message_index
GROUP BY user_id;
