package org.teamsai.saibackend.domain.batch.transaction.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.batch.common.scheduler.AbstractDailyBatchScheduler;

@Component
@RequiredArgsConstructor
public class BankTransactionRetryScheduler extends AbstractDailyBatchScheduler {

    private final Job bankTransactionRetryJob;

    @Scheduled(cron = "0 55 23 * * *")  // 23:55, transactionSync(23:50) 다음
    public void runRetry() throws Exception {
        runDaily();
    }

    @Override
    protected Job targetJob() { return bankTransactionRetryJob; }

    @Override
    protected String jobLabel() { return "bankTransaction.retry"; }
}
