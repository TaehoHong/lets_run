ALTER TABLE running_record ADD COLUMN shoe_id BIGINT UNSIGNED NOT NULL AFTER user_id;
ALTER TABLE running_record ADD CONSTRAINT fk__running_record_shoe_id FOREIGN KEY (shoe_id) REFERENCES shoe(id);
