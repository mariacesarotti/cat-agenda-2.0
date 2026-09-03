-- P4-5: indefinite dismiss for catalog-driven supply items (ADR 0007,
-- Decision 2). Nullable, no default — absence means "not currently
-- dismissed," the same convention already used for last_restocked_at.
ALTER TABLE supply_tracker ADD COLUMN dismissed_at TIMESTAMP;

-- P4-4: manually-added cart items (ADR 0007, Decision 1). Decoupled from
-- supply_tracker/item_type entirely — no cadence rule applies to an
-- arbitrary free-text name, so there's nothing for SupplyReasoningService
-- to compute over it.
CREATE TABLE cart_item (
    id             BIGSERIAL PRIMARY KEY,
    owner_id       BIGINT NOT NULL REFERENCES app_user (id),
    name           VARCHAR(200) NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    restocked_at   DATE,
    dismissed_at   TIMESTAMP
);

CREATE INDEX idx_cart_item_owner_id ON cart_item (owner_id);
