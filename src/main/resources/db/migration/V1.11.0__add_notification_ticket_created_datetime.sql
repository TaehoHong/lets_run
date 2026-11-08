ALTER TABLE notification_push
    ADD COLUMN ticket_created_datetime DATETIME NULL
    AFTER provider_ticket_id;
