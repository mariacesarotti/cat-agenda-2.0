CREATE TABLE checkup_record (
    id          BIGSERIAL PRIMARY KEY,
    cat_id      BIGINT NOT NULL REFERENCES cat (id) ON DELETE CASCADE,
    visit_date  DATE NOT NULL
);

CREATE INDEX idx_checkup_record_cat_id ON checkup_record (cat_id);
