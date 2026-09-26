package nz.kiwifinance.bankfeed;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps bank feeds fresh without anyone pressing "sync". Akahu refreshes personal-app data from
 * banks about once a day, so a few runs a day are enough to pick changes up promptly.
 */
@Component
@ConditionalOnProperty(prefix = "kiwi.akahu.scheduled-sync", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
class BankSyncScheduler {

    private final BankSyncService syncService;

    @Scheduled(cron = "${kiwi.akahu.scheduled-sync.cron}", zone = "Pacific/Auckland")
    void syncAll() {
        syncService.syncAll();
    }
}
