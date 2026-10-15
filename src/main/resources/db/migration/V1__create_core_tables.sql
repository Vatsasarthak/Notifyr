-- Core schema for notifications, recipients, templates and campaigns.
-- Must match the JPA entities, because Hibernate runs with ddl-auto=validate.
-- Databases that were baselined at version 1 (spring.flyway.baseline-version) skip this script.

CREATE TABLE notifications (
    id                     BIGSERIAL PRIMARY KEY,
    recipient_email        VARCHAR(255),
    channel                VARCHAR(255) NOT NULL,
    recipient_phone        VARCHAR(255),
    recipient_device_token VARCHAR(255),
    subject                VARCHAR(255) NOT NULL,
    message                TEXT         NOT NULL,
    status                 VARCHAR(255) NOT NULL,
    created_at             TIMESTAMP(6) NOT NULL,
    updated_at             TIMESTAMP(6),
    failure_reason         TEXT,
    retry_count            INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_notifications_status ON notifications (status);

CREATE TABLE recipients (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    email              VARCHAR(255) NOT NULL UNIQUE,
    phone_number       VARCHAR(255),
    device_token       VARCHAR(255),
    preferred_language VARCHAR(255),
    time_zone          VARCHAR(255),
    status             VARCHAR(255) NOT NULL,
    created_at         TIMESTAMP(6) NOT NULL,
    updated_at         TIMESTAMP(6)
);

CREATE TABLE recipient_tags (
    recipient_id BIGINT NOT NULL REFERENCES recipients (id) ON DELETE CASCADE,
    tag          VARCHAR(255)
);

CREATE INDEX idx_recipient_tags_recipient_id ON recipient_tags (recipient_id);

CREATE TABLE templates (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    subject    VARCHAR(255) NOT NULL,
    body       TEXT         NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6)
);

CREATE TABLE campaigns (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    description       VARCHAR(255),
    channel           VARCHAR(255) NOT NULL,
    template_id       BIGINT       NOT NULL,
    audience_language VARCHAR(255),
    status            VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP(6) NOT NULL,
    updated_at        TIMESTAMP(6),
    scheduled_time    TIMESTAMP(6)
);

CREATE INDEX idx_campaigns_status_scheduled_time ON campaigns (status, scheduled_time);

CREATE TABLE campaign_recipients (
    campaign_id  BIGINT NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    recipient_id BIGINT
);

CREATE INDEX idx_campaign_recipients_campaign_id ON campaign_recipients (campaign_id);

CREATE TABLE campaign_audience_tags (
    campaign_id BIGINT NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    tag         VARCHAR(255)
);

CREATE INDEX idx_campaign_audience_tags_campaign_id ON campaign_audience_tags (campaign_id);

CREATE TABLE campaign_template_variables (
    campaign_id BIGINT       NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    var_key     VARCHAR(255) NOT NULL,
    var_value   VARCHAR(255),
    PRIMARY KEY (campaign_id, var_key)
);
