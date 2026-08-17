package org.teamsai.saibackend.domain.batch.settlement.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.batch.common.scheduler.AbstractDailyBatchScheduler;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class SettlementBatchScheduler extends AbstractDailyBatchScheduler {

    private final Job dailySettlementJob;

    @Scheduled(cron = "0 0 0 * * *")
    public void runDailySettlement() throws Exception {
        runDaily();
    }

    @Override
    protected Job targetJob() {
        return dailySettlementJob;
    }

    @Override
    protected String jobLabel() {
        return "settlement.recurring";
    }
}
