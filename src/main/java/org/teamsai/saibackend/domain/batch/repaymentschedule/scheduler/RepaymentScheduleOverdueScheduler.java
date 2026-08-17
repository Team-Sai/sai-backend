package org.teamsai.saibackend.domain.batch.repaymentschedule.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.batch.common.scheduler.AbstractDailyBatchScheduler;

@Component
@RequiredArgsConstructor
public class RepaymentScheduleOverdueScheduler extends AbstractDailyBatchScheduler {

    private final Job repaymentScheduleOverdueJob;

    @Scheduled(cron = "0 * * * * *")
    public void runOverdueCheck() throws Exception {
        runDaily();
    }

    @Override
    protected Job targetJob() {
        return repaymentScheduleOverdueJob;
    }

    @Override
    protected String jobLabel() {
        return "repaymentSchedule.overdue";
    }
}