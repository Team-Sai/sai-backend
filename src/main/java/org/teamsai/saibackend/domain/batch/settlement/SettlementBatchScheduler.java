package org.teamsai.saibackend.domain.batch.settlement;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class SettlementBatchScheduler {

    private final JobOperator jobOperator;
    private final Job dailySettlementJob;

    @Scheduled(cron = "0 0 1 * * *")
    public void runDailySettlement() throws Exception{
        LocalDate today = LocalDate.now();

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("baseDate", today.toString())
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();
        jobOperator.start(dailySettlementJob,jobParameters);
    }
}
