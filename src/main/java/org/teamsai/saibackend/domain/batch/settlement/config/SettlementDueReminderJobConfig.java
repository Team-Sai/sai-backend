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
import org.teamsai.saibackend.domain.settlement.service.SettlementDueReminderService;
import org.teamsai.saibackend.domain.settlement.service.SettlementReminderResult;

import java.time.LocalDate;

@Configuration
@RequiredArgsConstructor
public class SettlementDueReminderJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job settlementDueReminderJob(Step settlementDueReminderStep) {
        return new JobBuilder("settlementDueReminderJob", jobRepository)
                .start(settlementDueReminderStep)
                .build();
    }

    @Bean
    public Step settlementDueReminderStep(SettlementDueReminderService settlementDueReminderService) {
        return new StepBuilder("settlementDueReminderStep", jobRepository)
                .tasklet(settlementDueReminderTasklet(settlementDueReminderService), transactionManager)
                .build();
    }

    private Tasklet settlementDueReminderTasklet(SettlementDueReminderService settlementDueReminderService) {
        return (contribution, chunkContext) -> {
            String baseDateParam = (String) chunkContext.getStepContext()
                    .getJobParameters().get("baseDate");
            LocalDate baseDate = LocalDate.parse(baseDateParam);

            SettlementReminderResult result = settlementDueReminderService.sendDueReminders(baseDate);

            contribution.incrementWriteCount(result.processedCount());
            for (int i = 0; i < result.failedCount(); i++) {
                contribution.incrementWriteSkipCount();
            }

            return RepeatStatus.FINISHED;
        };
    }
}