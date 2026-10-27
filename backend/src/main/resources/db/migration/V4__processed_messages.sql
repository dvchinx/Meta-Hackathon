-- Webhook idempotency: WhatsApp redelivers messages. Only ids are stored, never content or phone numbers.
CREATE TABLE processed_message (
    channel      text        NOT NULL,
    message_id   text        NOT NULL,
    processed_at timestamptz NOT NULL,
    PRIMARY KEY (channel, message_id)
);
