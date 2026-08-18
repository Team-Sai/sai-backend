package org.teamsai.saibackend.domain.batch.settlement.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.batch.common.scheduler.AbstractDailyBatchScheduler;

@Component
@RequiredArgsConstructor
public class SettlementDueReminderScheduler extends AbstractDailyBatchScheduler {

    private final Job settlementDueReminderJob;

    @Scheduled(cron = "0 * * * * *")
    public void runSettlementDueReminder() throws Exception {
        runDaily();
    }

    @Override
    protected Job targetJob() { return settlementDueReminderJob; }

    @Override
    protected String jobLabel() { return "settlement.dueReminder"; }
}