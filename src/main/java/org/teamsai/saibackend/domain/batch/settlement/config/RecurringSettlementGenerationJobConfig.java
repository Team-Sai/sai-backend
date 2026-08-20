package org.teamsai.saibackend.domain.batch.settlement.config;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.teamsai.saibackend.domain.batch.common.listener.LoggingJobExecutionListener;
import org.teamsai.saibackend.domain.settlement.service.RecurringSettlementBatchResult;
import org.teamsai.saibackend.domain.settlement.service.RecurringSettlementGenerationService;

import java.time.LocalDate;

@Configuration
@RequiredArgsConstructor
public class RecurringSettlementGenerationJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final LoggingJobExecutionListener loggingJobExecutionListener;

    @Bean
    public Job recurringSettlementGenerationJob(Step recurringSettlementGenerationStep) {
        return new JobBuilder("recurringSettlementGenerationJob", jobRepository)
                .start(recurringSettlementGenerationStep)
                .listener(loggingJobExecutionListener)
                .build();
    }

    @Bean
    public Step recurringSettlementGenerationStep(
            RecurringSettlementGenerationService recurringSettlementGenerationService) {

        return new StepBuilder("recurringSettlementGenerationStep", jobRepository)
                .tasklet(recurringSettlementGenerationTasklet(recurringSettlementGenerationService), transactionManager)
                .build();
    }

    private Tasklet recurringSettlementGenerationTasklet(
            RecurringSettlementGenerationService recurringSettlementGenerationService) {

        return (contribution, chunkContext) -> {
            String baseDateParam = (String) chunkContext.getStepContext()
                    .getJobParameters().get("baseDate");
            LocalDate baseDate = LocalDate.parse(baseDateParam);

            RecurringSettlementBatchResult result =
                    recurringSettlementGenerationService.generateTodaySettlements(baseDate);

            contribution.incrementWriteCount(result.succeeded());
            for (int i = 0; i < result.failed(); i++) {
                contribution.incrementWriteSkipCount();
            }

            return RepeatStatus.FINISHED;
        };
    }
}