package nz.kiwifinance.bankfeed;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class BankFeedConfig {

    /**
     * Syncs spend most of their time waiting on the provider, so each runs on its own virtual
     * thread. Concurrency per connection is limited by the sync lease, not the executor.
     */
    @Bean
    Executor bankSyncExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("bank-sync-");
        executor.setVirtualThreads(true);
        executor.setTaskTerminationTimeout(30_000);
        return executor;
    }
}
