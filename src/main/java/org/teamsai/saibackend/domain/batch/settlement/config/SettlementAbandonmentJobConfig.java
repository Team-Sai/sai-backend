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
import org.teamsai.saibackend.domain.settlement.service.SettlementAbandonmentDetectionService;
import org.teamsai.saibackend.domain.settlement.service.SettlementAbandonmentResult;

import java.time.LocalDate;

@Configuration
@RequiredArgsConstructor
public class SettlementAbandonmentJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final LoggingJobExecutionListener loggingJobExecutionListener;

    @Bean
    public Job settlementAbandonmentJob(Step settlementAbandonmentStep) {
        return new JobBuilder("settlementAbandonmentJob", jobRepository)
                .start(settlementAbandonmentStep)
                .listener(loggingJobExecutionListener)
                .build();
    }

    @Bean
    public Step settlementAbandonmentStep(SettlementAbandonmentDetectionService service) {
        return new StepBuilder("settlementAbandonmentStep", jobRepository)
                .tasklet(settlementAbandonmentTasklet(service), transactionManager)
                .build();
    }

    private Tasklet settlementAbandonmentTasklet(SettlementAbandonmentDetectionService service) {
        return (contribution, chunkContext) -> {
            String baseDateParam = (String) chunkContext.getStepContext()
                    .getJobParameters().get("baseDate");
            LocalDate baseDate = LocalDate.parse(baseDateParam);

            SettlementAbandonmentResult result = service.detectAbandoned(baseDate);

            contribution.incrementWriteCount(result.detectedCount());

            return RepeatStatus.FINISHED;
        };
    }
}