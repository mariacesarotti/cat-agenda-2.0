CREATE TABLE supply_tracker (
    id                 BIGSERIAL PRIMARY KEY,
    owner_id           BIGINT NOT NULL REFERENCES app_user (id),
    item_type          VARCHAR(30) NOT NULL,
    last_restocked_at  DATE,
    created_at         TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_supply_tracker_owner_item UNIQUE (owner_id, item_type)
);

CREATE INDEX idx_supply_tracker_owner_id ON supply_tracker (owner_id);
