-- Local projection of users, built from ms-auth's Kafka events (never read from ms-auth directly).
-- email stays NULL until the registration event is seen (settings may arrive first).
CREATE TABLE recipients (
    user_id               BINARY(16)   NOT NULL,
    email                 VARCHAR(255) NULL,
    notifications_enabled BIT(1)       NOT NULL DEFAULT b'1',
    notify_event_activity BIT(1)       NOT NULL DEFAULT b'1',
    notify_event_messages BIT(1)       NOT NULL DEFAULT b'1',
    notify_documents      BIT(1)       NOT NULL DEFAULT b'1',
    notify_deadlines      BIT(1)       NOT NULL DEFAULT b'1',
    notify_nearby_events  BIT(1)       NOT NULL DEFAULT b'1',
    notify_judgments      BIT(1)       NOT NULL DEFAULT b'1',
    PRIMARY KEY (user_id)
) ENGINE = InnoDB;

-- Ids of the messages already handled (Kafka events and email requests), for idempotency:
-- Kafka delivers at least once, so the same message can be consumed again.
CREATE TABLE processed_events (
    event_id     BINARY(16) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE = InnoDB;
