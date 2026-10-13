-- P6-1/P6-2/P6-3: current-state singleton per cat tracking spay/neuter
-- scheduling intent (ADR 0009, Decision 1) -- one row per cat (cat_id
-- UNIQUE), mutated in place, not append-only history like
-- checkup_record/vaccination_record (a cat is either currently tracked for
-- this or it isn't; there's no cumulative history to keep, unlike booster
-- shots or vet visits).
--
-- appointment_date is nullable: absent means "intent flagged, not yet
-- scheduled" -- the P6-4 "pending" reminder state
-- (SpayNeuterReasoningService / CalendarEventType.SPAY_NEUTER_PENDING).
-- Present means a real appointment date is on the books
-- (CalendarEventType.SPAY_NEUTER_SCHEDULED).
--
-- The whole row is deleted when intent is unset (P6-1) or when the
-- procedure is marked completed (P6-3, alongside flipping
-- cat.spayed_neutered) -- an appointment should never outlive the intent
-- flag it depends on. Only appointment_date is cleared (row stays) when
-- just the appointment is removed (P6-2), which falls the cat back to the
-- pending state rather than dropping it from "what's due" entirely.
--
-- ON DELETE CASCADE mirrors condition/vaccination_record/checkup_record:
-- unlike those, SpayNeuterPlan has no JPA-level @OneToMany on Cat (queried
-- directly by cat instead, ADR 0009), so this database-level cascade is the
-- only thing preventing an orphaned row if a cat is ever deleted.
CREATE TABLE spay_neuter_plan (
    id                 BIGSERIAL PRIMARY KEY,
    cat_id             BIGINT NOT NULL UNIQUE REFERENCES cat (id) ON DELETE CASCADE,
    intent_flagged_at  TIMESTAMP NOT NULL,
    appointment_date   DATE,
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);
