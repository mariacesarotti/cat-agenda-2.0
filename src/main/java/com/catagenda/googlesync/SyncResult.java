package com.catagenda.googlesync;

/**
 * The outcome of one {@link GoogleSyncService#syncForOwner} run — a plain
 * DTO {@code record} (same "record for a value built once and only read
 * afterward" reasoning already established for {@code CalendarEvent}/
 * {@code DueVaccine}), returned to {@code GoogleSyncController} (T5.1) so
 * "Sync now" can show a result summary (counts created/updated/removed/
 * failed), which also directly serves P5-5's "see current sync health"
 * requirement.
 */
public record SyncResult(boolean skipped, int created, int updated, int removed, int failed) {

    // Named ownerSkipped()/counted(...) rather than skipped()/of(...) --
    // a static factory can't share a name with the no-arg instance accessor
    // a record generates for its own "skipped" component (skipped()); the
    // compiler treats that as an invalid attempt to redeclare the accessor.
    static SyncResult ownerSkipped() {
        return new SyncResult(true, 0, 0, 0, 0);
    }

    static SyncResult counted(int created, int updated, int removed, int failed) {
        return new SyncResult(false, created, updated, removed, failed);
    }
}
