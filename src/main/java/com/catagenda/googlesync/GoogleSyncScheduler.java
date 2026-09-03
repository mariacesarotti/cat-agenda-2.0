package com.catagenda.googlesync;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * T5.2 — the staleness backstop (ADR 0008, Decision 3): a periodic job that
 * closes the gap an explicit-only "Sync now" action would leave, since a
 * user who never clicks it would otherwise never see a due-date change
 * propagate (e.g. "due soon" purely because today's date advanced).
 * <p>
 * {@code @Scheduled(fixedDelay = ...)} runs this method repeatedly with a
 * fixed gap between the end of one run and the start of the next, with no
 * external job-scheduler infrastructure required — {@code @EnableScheduling}
 * (in {@link GoogleSyncConfig}, T7.3) is what activates this annotation's
 * processing at all.
 * <p>
 * The interval itself is an operational choice, not an architectural one
 * (OI-2 in the tech-tasks doc) — configurable via
 * {@code catagenda.google-sync.scheduled-sync-fixed-delay-ms} (default: one
 * hour, a reasonable placeholder pending a real answer once usage patterns
 * are known — see {@code application.yml}'s comment).
 * <p>
 * Deliberately a one-line delegation, kept free of any reconciliation logic
 * itself: {@link GoogleSyncService#syncAllActiveOwners()} is the single
 * place that logic lives, shared with T5.1's "Sync now" action.
 */
@Component
public class GoogleSyncScheduler {

    private final GoogleSyncService googleSyncService;

    public GoogleSyncScheduler(GoogleSyncService googleSyncService) {
        this.googleSyncService = googleSyncService;
    }

    @Scheduled(fixedDelayString = "${catagenda.google-sync.scheduled-sync-fixed-delay-ms}")
    public void syncAllOwners() {
        googleSyncService.syncAllActiveOwners();
    }
}
