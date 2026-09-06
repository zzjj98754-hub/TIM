CREATE TABLE IF NOT EXISTS offline_user_cursor (
  user_id BIGINT PRIMARY KEY,
  confirmed_cursor BIGINT NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
