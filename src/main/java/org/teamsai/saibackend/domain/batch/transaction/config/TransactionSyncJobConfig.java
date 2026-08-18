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
import org.teamsai.saibackend.domain.transaction.service.TransactionSyncFacade;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TransactionSyncJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SqlSessionFactory sqlSessionFactory;
    private final LoggingJobExecutionListener loggingJobExecutionListener;

    @Bean
    public Job transactionSyncJob(Step transactionSyncStep) {
        return new JobBuilder("transactionSyncJob", jobRepository)
                .start(transactionSyncStep)
                .listener(loggingJobExecutionListener)
                .build();
    }

    @Bean
    public Step transactionSyncStep(
            BaseSkipListener<LinkedAccountSyncTargetDTO, LinkedAccountSyncTargetDTO> skipListener,
            TransactionSyncFacade transactionSyncFacade) {

        return new StepBuilder("transactionSyncStep", jobRepository)
                .<LinkedAccountSyncTargetDTO, LinkedAccountSyncTargetDTO>chunk(50)
                .transactionManager(transactionManager)
                .reader(reader())
                .processor(processor(transactionSyncFacade))
                .writer(noOpWriter())
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(50)
                .listener(skipListener)
                .build();
    }

    @Bean
    public MyBatisCursorItemReader<LinkedAccountSyncTargetDTO> reader() {
        return new MyBatisCursorItemReaderBuilder<LinkedAccountSyncTargetDTO>()
                .sqlSessionFactory(sqlSessionFactory)
                .queryId("org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper.findAllAvailableForSync")
                .build();
    }

    private ItemProcessor<LinkedAccountSyncTargetDTO, LinkedAccountSyncTargetDTO> processor(
            TransactionSyncFacade transactionSyncFacade) {

        return target -> {
            transactionSyncFacade.syncAndMatch(target.userId(), target.linkedAccountId());
            return target; // 성공한 건만 여기까지 도달, 실패는 예외로 전파되어 skip 처리됨
        };
    }

    private ItemWriter<LinkedAccountSyncTargetDTO> noOpWriter() {
        // 실제 동기화/매칭은 Processor 안에서 이미 완료됨(각자 내부 트랜잭션으로 커밋)
        // Writer는 배치 완료 로그만 남기는 역할
        return chunk -> log.info("[transactionSync] 동기화 완료 계좌 {}건: {}",
                chunk.size(),
                chunk.getItems().stream().map(LinkedAccountSyncTargetDTO::linkedAccountId).toList());
    }
}