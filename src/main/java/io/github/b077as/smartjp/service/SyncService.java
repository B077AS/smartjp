package io.github.b077as.smartjp.service;

import org.springframework.stereotype.Service;

import io.github.b077as.smartjp.model.dto.summary.SyncSummary;
import io.github.b077as.smartjp.model.dto.sync.SyncOutcome;
import io.github.b077as.smartjp.model.dto.sync.SyncSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * What this side does with a sync, whichever side it is: the website answers the desktop's requests with these, and the desktop
 * app uses them on itself. Not transactional on purpose: a replace saves a backup first (VACUUM cannot run inside a transaction)
 * and then lets SyncStore do the work in one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final SyncStore store;
    private final BackupService backup;

    /**
     * A snapshot to replace a side with must list answers, lists, cards and deletions (each may be empty). A missing part is a
     * broken or foreign message, and treating it as "nothing" would wipe the side that takes it in.
     */
    public static boolean isComplete(SyncSnapshot snapshot) {
        return snapshot != null && snapshot.getLists() != null && snapshot.getCustomCards() != null && snapshot.getAnswers() != null && snapshot.getDeletions() != null;
    }

    public SyncSummary summary() {
        return store.summary();
    }

    public SyncSnapshot snapshot() {
        return store.snapshot();
    }

    /** Takes in what the other side has: nothing here is lost. */
    public SyncOutcome mergeIn(SyncSnapshot incoming) {
        SyncOutcome outcome = store.merge(incoming);
        store.markSynced();
        return outcome;
    }

    /** The other side overwrites this one. This side is saved to a backup file first, and the outcome names it. */
    public SyncOutcome replaceWith(SyncSnapshot incoming) {
        String file = backup.make();
        SyncOutcome outcome = store.replaceAll(incoming);
        outcome.setBackup(file);
        store.markSynced();
        return outcome;
    }

    /** This side took part in a sync that it did not run itself (the desktop replaced the server). */
    public void markSynced() {
        store.markSynced();
    }
}
