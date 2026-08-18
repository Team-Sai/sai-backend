package org.teamsai.saibackend.domain.batch.transaction.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.batch.common.scheduler.AbstractDailyBatchScheduler;

@Component
@RequiredArgsConstructor
public class TransactionSyncScheduler extends AbstractDailyBatchScheduler {

    private final Job transactionSyncJob;

    @Scheduled(cron = "0 * * * * *")  // 23:50 — 연체 배치들(자정)보다 먼저
    public void runTransactionSync() throws Exception {
        runDaily();
    }

    @Override
    protected Job targetJob() {
        return transactionSyncJob;
    }

    @Override
    protected String jobLabel() {
        return "transaction.sync";
    }
}