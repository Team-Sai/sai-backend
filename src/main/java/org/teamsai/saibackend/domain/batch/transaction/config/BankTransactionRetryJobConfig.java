package org.teamsai.saibackend.domain.batch.transaction.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisCursorItemReader;
import org.mybatis.spring.batch.builder.MyBatisCursorItemReaderBuilder;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.teamsai.saibackend.domain.account.dto.LinkedAccountSyncTargetDTO;
import org.teamsai.saibackend.domain.batch.common.listener.BaseSkipListener;
import org.teamsai.saibackend.domain.batch.common.listener.LoggingJobExecutionListener;
import org.teamsai.saibackend.domain.matching.service.BankTransactionRetryService;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BankTransactionRetryJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SqlSessionFactory sqlSessionFactory;
    private final LoggingJobExecutionListener loggingJobExecutionListener;

    @Bean
    public Job bankTransactionRetryJob(Step bankTransactionRetryStep) {
        return new JobBuilder("bankTransactionRetryJob", jobRepository)
                .start(bankTransactionRetryStep)
                .listener(loggingJobExecutionListener)
                .build();
    }

    @Bean
    public Step bankTransactionRetryStep(
            BaseSkipListener<LinkedAccountSyncTargetDTO, LinkedAccountSyncTargetDTO> skipListener,
            BankTransactionRetryService bankTransactionRetryService) {

        return new StepBuilder("bankTransactionRetryStep", jobRepository)
                .<LinkedAccountSyncTargetDTO, LinkedAccountSyncTargetDTO>chunk(50)
                .transactionManager(transactionManager)
                .reader(bankTransactionRetryReader())
                .processor(processor(bankTransactionRetryService))
                .writer(noOpWriter())
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(50)
                .listener(skipListener)
                .build();
    }

    @Bean
    public MyBatisCursorItemReader<LinkedAccountSyncTargetDTO> bankTransactionRetryReader() {
        return new MyBatisCursorItemReaderBuilder<LinkedAccountSyncTargetDTO>()
                .sqlSessionFactory(sqlSessionFactory)
                .queryId("org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper.findAllAvailableForSync")
                .build();
    }

    private ItemProcessor<LinkedAccountSyncTargetDTO, LinkedAccountSyncTargetDTO> processor(
            BankTransactionRetryService bankTransactionRetryService) {

        return target -> {
            bankTransactionRetryService.retryForAccount(target.userId(), target.linkedAccountId());
            return target;
        };
    }

    private ItemWriter<LinkedAccountSyncTargetDTO> noOpWriter() {
        return chunk -> log.info("[bankTransactionRetry] 재시도 처리 완료 계좌 {}건", chunk.size());
    }
}