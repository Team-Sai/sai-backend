package org.teamsai.saibackend.domain.batch.common.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.batch.common.util.DailyJobParameters;

import java.time.LocalDate;

@Slf4j
@Component
public class DailyBatchPipelineScheduler {

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job transactionSyncJob;
    @Autowired
    private Job bankTransactionRetryJob;

    @Autowired
    private Job recurringSettlementGenerationJob;

    @Autowired
    private Job repaymentScheduleOverdueJob;
    @Autowired
    private Job settlementOverdueJob;
    @Autowired
    private Job settlementWriteOffJob;
    @Autowired
    private Job repaymentWriteOffJob;

    @Scheduled(cron = "0 * * * * *")
    public void runPipeline() {
        LocalDate baseDate = LocalDate.now();
        runStep("transactionSync", transactionSyncJob, baseDate);
        runStep("bankTransactionRetry", bankTransactionRetryJob, baseDate);
        runStep("recurringSettlementGeneration", recurringSettlementGenerationJob, baseDate);
        runStep("repaymentScheduleOverdue", repaymentScheduleOverdueJob, baseDate);
        runStep("settlementOverdue", settlementOverdueJob, baseDate);
        runStep("settlementWriteOff", settlementWriteOffJob, baseDate);
        runStep("repaymentWriteOff", repaymentWriteOffJob, baseDate);
    }

    private void runStep(String label, Job job, LocalDate baseDate) {
        JobParameters params = new JobParametersBuilder()
                .addString("baseDate", baseDate.toString())
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();
        log.info("[daily.pipeline] {} 시작 - baseDate={}", label, baseDate);
        try {
            jobOperator.start(job, params);
        } catch (Exception e) {
            log.error("[daily.pipeline] {} 실패, 다음 단계 계속 진행", label, e);
        }
    }
}