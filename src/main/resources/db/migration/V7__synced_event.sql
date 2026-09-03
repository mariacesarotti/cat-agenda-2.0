-- P5-2/P5-3/P5-5: local source of truth for "has this CalendarEvent already
-- been pushed to Google, and is it healthy" (ADR 0008, Decision 1). Keyed
-- by the same natural key CalendarEvent already exposes -- cat + event
-- type (+ vaccine type when applicable) -- since CalendarEvent itself has
-- no database id (ADR 0006: it's a stateless view-model, recomputed fresh
-- on every read).
--
-- deactivated_at is nullable, same convention as
-- google_calendar_connection.disconnected_at: presence means "disconnected,
-- kept for reactivation" (ADR 0008, Decision 5), never a hard delete. A row
-- whose underlying due event no longer applies at all (cat removed, vaccine
-- no longer due) is instead hard-deleted by GoogleSyncService -- a distinct
-- operation from deactivation, see Section 0 of the tech-tasks doc.
CREATE TABLE synced_event (
    id                     BIGSERIAL PRIMARY KEY,
    owner_id               BIGINT NOT NULL REFERENCES app_user (id),
    cat_id                 BIGINT NOT NULL REFERENCES cat (id),
    event_type             VARCHAR(20) NOT NULL,
    vaccine_type           VARCHAR(20),
    google_event_id        VARCHAR(255) NOT NULL,
    last_synced_due_date   DATE NOT NULL,
    sync_status            VARCHAR(10) NOT NULL,
    last_error             TEXT,
    last_synced_at         TIMESTAMP NOT NULL,
    deactivated_at         TIMESTAMP,
    created_at             TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (owner_id, cat_id, event_type, vaccine_type)
);

CREATE INDEX idx_synced_event_owner_id ON synced_event (owner_id);
