package org.teamsai.saibackend.domain.batch.common.scheduler;

import lombok.extern.slf4j.Slf4j;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.teamsai.saibackend.domain.batch.common.util.DailyJobParameters;

@Slf4j
public abstract class AbstractDailyBatchScheduler {

    @Autowired
    private JobOperator jobOperator;

    protected abstract Job targetJob();

    protected abstract String jobLabel();

    protected void runDaily() throws Exception {
        JobParameters jobParameters = DailyJobParameters.today();
        log.info("[{}] batch start - baseDate={}", jobLabel(), jobParameters.getString("baseDate"));
        try {
            jobOperator.start(targetJob(), jobParameters);
        } catch (Exception e) {
            log.error("[{}] batch execution failed", jobLabel(), e);
            throw e;
        }
    }
}