package org.teamsai.saibackend.domain.batch.settlement.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.batch.common.scheduler.AbstractDailyBatchScheduler;

@Component
@RequiredArgsConstructor
public class SettlementOverdueScheduler extends AbstractDailyBatchScheduler {

    private final Job settlementOverdueJob;

    @Scheduled(cron = "0 0 0 * * *")  // 테스트용
    public void runSettlementOverdue() throws Exception {
        runDaily();
    }

    @Override
    protected Job targetJob() {
        return settlementOverdueJob;
    }

    @Override
    protected String jobLabel() {
        return "settlement.overdue";
    }
}