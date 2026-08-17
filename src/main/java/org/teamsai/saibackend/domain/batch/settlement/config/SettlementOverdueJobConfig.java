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
import org.teamsai.saibackend.domain.settlement.service.OverdueSettlementService;
import org.teamsai.saibackend.domain.settlement.service.OverdueUpdateResult;

import java.time.LocalDate;

@Configuration
@RequiredArgsConstructor
public class SettlementOverdueJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job settlementOverdueJob(Step settlementOverdueStep) {
        return new JobBuilder("settlementOverdueJob", jobRepository)
                .start(settlementOverdueStep)
                .build();
    }

    @Bean
    public Step settlementOverdueStep(OverdueSettlementService overdueSettlementService) {
        return new StepBuilder("settlementOverdueStep", jobRepository)
                .tasklet(settlementOverdueTasklet(overdueSettlementService), transactionManager)
                .build();
    }

    private Tasklet settlementOverdueTasklet(OverdueSettlementService overdueSettlementService) {
        return (contribution, chunkContext) -> {
            String baseDateParam = (String) chunkContext.getStepContext()
                    .getJobParameters().get("baseDate");
            LocalDate baseDate = LocalDate.parse(baseDateParam);

            OverdueUpdateResult result = overdueSettlementService.updateOverdueStatus(baseDate);

            for (int i = 0; i < result.failedCount(); i++) {
                contribution.incrementWriteSkipCount();
            }
            contribution.incrementWriteCount(result.processedCount());

            return RepeatStatus.FINISHED;
        };
    }
}