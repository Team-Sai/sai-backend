package org.teamsai.saibackend.domain.batch.config;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.teamsai.saibackend.domain.batch.common.listener.LoggingJobExecutionListener;
import org.teamsai.saibackend.domain.batch.service.WriteOffBatchService;
import org.teamsai.saibackend.domain.batch.service.WriteOffResult;

import java.time.LocalDate;

@Configuration
@RequiredArgsConstructor
public class WriteOffJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final LoggingJobExecutionListener loggingJobExecutionListener;

    @Bean
    public Job settlementWriteOffJob(Step settlementWriteOffStep) {
        return new JobBuilder("settlementWriteOffJob", jobRepository)
                .start(settlementWriteOffStep)
                .listener(loggingJobExecutionListener)
                .build();
    }

    @Bean
    public Job repaymentWriteOffJob(Step repaymentWriteOffStep) {
        return new JobBuilder("repaymentWriteOffJob", jobRepository)
                .start(repaymentWriteOffStep)
                .listener(loggingJobExecutionListener)
                .build();
    }

    @Bean
    public Step settlementWriteOffStep(WriteOffBatchService writeOffBatchService) {
        return new StepBuilder("settlementWriteOffStep", jobRepository)
                .tasklet(settlementWriteOffTasklet(writeOffBatchService), transactionManager)
                .build();
    }

    @Bean
    public Step repaymentWriteOffStep(WriteOffBatchService writeOffBatchService) {
        return new StepBuilder("repaymentWriteOffStep", jobRepository)
                .tasklet(repaymentWriteOffTasklet(writeOffBatchService), transactionManager)
                .build();
    }

    private Tasklet settlementWriteOffTasklet(WriteOffBatchService writeOffBatchService) {
        return (contribution, chunkContext) -> {
            LocalDate baseDate = resolveBaseDate(chunkContext);
            WriteOffResult result = writeOffBatchService.writeOffSettlementObligations(baseDate);
            contribution.incrementWriteCount(result.obligationCount());
            return RepeatStatus.FINISHED;
        };
    }

    private Tasklet repaymentWriteOffTasklet(WriteOffBatchService writeOffBatchService) {
        return (contribution, chunkContext) -> {
            LocalDate baseDate = resolveBaseDate(chunkContext);
            int count = writeOffBatchService.writeOffRepaymentSchedules(baseDate);
            contribution.incrementWriteCount(count);
            return RepeatStatus.FINISHED;
        };
    }

    private LocalDate resolveBaseDate(org.springframework.batch.core.scope.context.ChunkContext chunkContext) {
        String baseDateParam = (String) chunkContext.getStepContext()
                .getJobParameters().get("baseDate");
        return LocalDate.parse(baseDateParam);
    }
}