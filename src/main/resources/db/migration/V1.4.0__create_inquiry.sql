CREATE TABLE inquiry (
    id                  BIGINT UNSIGNED     AUTO_INCREMENT  NOT NULL,
    tracking_no         VARCHAR(32)                         NOT NULL,
    user_id             BIGINT UNSIGNED                     NOT NULL,

    type                VARCHAR(20)                         NOT NULL,
    title               VARCHAR(254)                        NOT NULL,
    content             TEXT                                NOT NULL,
    reply_email         VARCHAR(254)                        NOT NULL,

    app_version         VARCHAR(50)                             NULL,
    build_number        VARCHAR(50)                             NULL,
    device_model        VARCHAR(100)                            NULL,
    os_name             VARCHAR(50)                             NULL,
    os_version          VARCHAR(50)                             NULL,
    error_code          VARCHAR(100)                            NULL,
    screen_name         VARCHAR(100)                            NULL,

    created_datetime    DATETIME DEFAULT CURRENT_TIMESTAMP  NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY ux__inquiry__tracking_no (tracking_no),
    INDEX ix__inquiry__user_id (user_id),
    CONSTRAINT fk__inquiry__user_id FOREIGN KEY (user_id) REFERENCES user(id)
) COMMENT '고객 문의';
