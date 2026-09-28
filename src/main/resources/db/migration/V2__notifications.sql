-- In-app notifications, shown in the Front's bell. read_at stays NULL until the user reads it.
CREATE TABLE notifications (
    id         BINARY(16)    NOT NULL,
    user_id    BINARY(16)    NOT NULL,
    title      VARCHAR(255)  NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    read_at    DATETIME(6)   NULL,
    PRIMARY KEY (id),
    INDEX idx_notifications_user_created (user_id, created_at)
) ENGINE = InnoDB;
