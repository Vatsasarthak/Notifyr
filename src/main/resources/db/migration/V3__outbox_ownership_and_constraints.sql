-- Transactional outbox, notification ownership/idempotency, dead-letter records and data constraints.

-- ---------------------------------------------------------------------------
-- Outbox: every RabbitMQ message is first written here in the same transaction as the
-- state change that causes it, then published by the outbox relay (see ADR 0001).
-- ---------------------------------------------------------------------------
CREATE TABLE outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    BIGINT       NOT NULL,
    exchange        VARCHAR(255) NOT NULL,
    routing_key     VARCHAR(255) NOT NULL,
    payload         TEXT         NOT NULL,
    message_headers TEXT,
    correlation_id  VARCHAR(64),
    status          VARCHAR(20)  NOT NULL,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    last_error      TEXT,
    available_at    TIMESTAMP(6) NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    sent_at         TIMESTAMP(6),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'SENT')),
    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0)
);

-- the relay polls only pending rows, in id order
CREATE INDEX idx_outbox_pending ON outbox_events (available_at, id) WHERE status = 'PENDING';
-- recovery checks whether a notification already has a pending message
CREATE INDEX idx_outbox_pending_aggregate ON outbox_events (aggregate_type, aggregate_id) WHERE status = 'PENDING';
-- retention cleanup of published rows
CREATE INDEX idx_outbox_sent_at ON outbox_events (sent_at) WHERE status = 'SENT';

-- ---------------------------------------------------------------------------
-- Notifications: owner, durable idempotency, campaign link, delivery timestamp
-- ---------------------------------------------------------------------------
ALTER TABLE notifications ADD COLUMN owner VARCHAR(100);
ALTER TABLE notifications ADD COLUMN idempotency_key VARCHAR(255);
ALTER TABLE notifications ADD COLUMN request_hash VARCHAR(64);
ALTER TABLE notifications ADD COLUMN campaign_id BIGINT REFERENCES campaigns (id) ON DELETE SET NULL;
ALTER TABLE notifications ADD COLUMN sent_at TIMESTAMP(6);

-- the same caller can use an Idempotency-Key only once (Redis is only a cache in front of this)
CREATE UNIQUE INDEX uq_notifications_owner_idempotency_key
    ON notifications (owner, idempotency_key) WHERE idempotency_key IS NOT NULL;
-- GET /notifications lists a caller's notifications newest first
CREATE INDEX idx_notifications_owner_created ON notifications (owner, created_at DESC, id DESC);
-- campaign statistics
CREATE INDEX idx_notifications_campaign_status ON notifications (campaign_id, status) WHERE campaign_id IS NOT NULL;
-- recovery job looks for stale PROCESSING / QUEUED rows
DROP INDEX IF EXISTS idx_notifications_status;
CREATE INDEX idx_notifications_status_updated ON notifications (status, updated_at);

ALTER TABLE notifications ADD CONSTRAINT chk_notifications_status
    CHECK (status IN ('QUEUED', 'PROCESSING', 'SENT', 'SIMULATED', 'FAILED'));
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_channel CHECK (channel IN ('EMAIL', 'SMS', 'PUSH'));
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_retry_count CHECK (retry_count >= 0);

-- ---------------------------------------------------------------------------
-- Campaigns, recipients, users: creator and enum constraints
-- ---------------------------------------------------------------------------
ALTER TABLE campaigns ADD COLUMN created_by VARCHAR(100);
ALTER TABLE campaigns ADD CONSTRAINT chk_campaigns_status
    CHECK (status IN ('DRAFT', 'SCHEDULED', 'RUNNING', 'COMPLETED', 'FAILED'));
ALTER TABLE campaigns ADD CONSTRAINT chk_campaigns_channel CHECK (channel IN ('EMAIL', 'SMS', 'PUSH'));

ALTER TABLE recipients ADD CONSTRAINT chk_recipients_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'UNSUBSCRIBED'));

ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'API_CLIENT'));

-- ---------------------------------------------------------------------------
-- Dead letters: every message that reaches notification.dlq is recorded for inspection/replay
-- ---------------------------------------------------------------------------
CREATE TABLE dead_letters (
    id              BIGSERIAL PRIMARY KEY,
    notification_id BIGINT,
    reason          TEXT,
    payload         TEXT         NOT NULL,
    correlation_id  VARCHAR(64),
    received_at     TIMESTAMP(6) NOT NULL,
    replayed_at     TIMESTAMP(6)
);

CREATE INDEX idx_dead_letters_received_at ON dead_letters (received_at DESC, id DESC);
CREATE INDEX idx_dead_letters_notification_id ON dead_letters (notification_id);
