package org.teamsai.saibackend.domain.batch.common.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingJobExecutionListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("[BATCH START] job={}, params={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        BatchStatus status = jobExecution.getStatus();
        if (status == BatchStatus.COMPLETED) {
            log.info("[BATCH SUCCESS] job={}, duration={}ms",
                    jobExecution.getJobInstance().getJobName(),
                    calculateDuration(jobExecution));
        } else {
            log.error("[BATCH FAILED] job={}, status={}, exitStatus={}",
                    jobExecution.getJobInstance().getJobName(),
                    status,
                    jobExecution.getExitStatus());
        }
    }

    private long calculateDuration(JobExecution jobExecution) {
        if (jobExecution.getStartTime() == null || jobExecution.getEndTime() == null) {
            return -1;
        }
        return java.time.Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis();
    }
}