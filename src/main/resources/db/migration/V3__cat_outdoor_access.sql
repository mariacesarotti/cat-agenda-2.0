-- Nullable, no default: existing cats end up NULL ("not confirmed"), not
-- FALSE ("known indoor-only") — see ADR 0005.
ALTER TABLE cat ADD COLUMN outdoor_access BOOLEAN;
