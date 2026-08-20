package org.teamsai.saibackend.domain.batch.common.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.batch.common.notification.JobDisplayNames;
import org.teamsai.saibackend.domain.batch.common.notification.SlackNotifier;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingJobExecutionListener implements JobExecutionListener {

    private final SlackNotifier slackNotifier;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("[BATCH START] job={}, params={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        BatchStatus status = jobExecution.getStatus();
        long durationMs = calculateDuration(jobExecution);

        if (status == BatchStatus.COMPLETED) {
            log.info("[BATCH SUCCESS] job={}, duration={}ms", jobName, durationMs);
        } else {
            log.error("[BATCH FAILED] job={}, status={}, exitStatus={}",
                    jobName, status, jobExecution.getExitStatus());
        }

        slackNotifier.send(buildSummaryMessage(jobExecution, durationMs));
    }

    private String buildSummaryMessage(JobExecution jobExecution, long durationMs) {
        String jobName = jobExecution.getJobInstance().getJobName();
        String displayName = JobDisplayNames.resolve(jobName);
        BatchStatus status = jobExecution.getStatus();
        boolean success = status == BatchStatus.COMPLETED;
        String emoji = success ? "✅" : "❌";
        String statusText = success ? "성공" : "실패";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s *%s* %s (%dms 소요)\n", emoji, displayName, statusText, durationMs));

        for (StepExecution step : jobExecution.getStepExecutions()) {
            long skipCount = step.getWriteSkipCount() + step.getReadSkipCount() + step.getProcessSkipCount();
            sb.append(String.format(
                    "  ·  %s: 조회 %d건, 처리 %d건, 건너뜀 %d건\n",
                    step.getStepName(),
                    step.getReadCount(),
                    step.getWriteCount(),
                    skipCount
            ));
        }

        return sb.toString();
    }

    private long calculateDuration(JobExecution jobExecution) {
        if (jobExecution.getStartTime() == null || jobExecution.getEndTime() == null) {
            return -1;
        }
        return Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis();
    }
}