CREATE TABLE IF NOT EXISTS term (
    id                  INT UNSIGNED,
    type                VARCHAR(128)    NOT NULL,

    link                VARCHAR(255)    NOT NULL,
    version             VARCHAR(64)     NOT NULL,

    is_required         TINYINT(1)      NOT NULL,
    is_enabled          TINYINT(1)      NOT NULL,

    created_datetime    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP(),

    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS user_agreement(
    id              BIGINT UNSIGNED     AUTO_INCREMENT,

    user_id         BIGINT UNSIGNED     NOT NULL,
    term_id         INT UNSIGNED        NOT NULL,

    is_agreed       TINYINT(1)          NOT NULL,

    agreed_datetime TIMESTAMP           NOT NULL,

    PRIMARY KEY (id),
    UNIQUE (user_id, term_id),
    CONSTRAINT fk__user_agreement_user_id FOREIGN KEY(user_id) REFERENCES user(id),
    CONSTRAINT fk__user_agreement_term_id FOREIGN KEY(term_id) REFERENCES term(id)
)