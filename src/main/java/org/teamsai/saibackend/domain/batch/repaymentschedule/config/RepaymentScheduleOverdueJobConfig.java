package org.teamsai.saibackend.domain.batch.repaymentschedule.config;

import lombok.RequiredArgsConstructor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisBatchItemWriter;
import org.mybatis.spring.batch.MyBatisPagingItemReader;
import org.mybatis.spring.batch.builder.MyBatisBatchItemWriterBuilder;
import org.mybatis.spring.batch.builder.MyBatisPagingItemReaderBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.teamsai.saibackend.domain.batch.common.listener.BaseSkipListener;
import org.teamsai.saibackend.domain.batch.common.listener.LoggingJobExecutionListener;
import org.teamsai.saibackend.domain.batch.repaymentschedule.dto.OverdueUpdateCommand;
import org.teamsai.saibackend.domain.contract.dto.RepaymentScheduleDTO;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.teamsai.saibackend.domain.contract.type.RepaymentScheduleStatus.OVERDUE;

@Configuration
@RequiredArgsConstructor
public class RepaymentScheduleOverdueJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SqlSessionFactory sqlSessionFactory;
    private final LoggingJobExecutionListener loggingJobExecutionListener;

    @Bean
    public Job repaymentScheduleOverdueJob(Step repaymentScheduleOverdueStep) {
        return new JobBuilder("repaymentScheduleOverdueJob", jobRepository)
                .start(repaymentScheduleOverdueStep)
                .listener(loggingJobExecutionListener)
                .build();
    }

    @Bean
    public Step repaymentScheduleOverdueStep(
            MyBatisPagingItemReader<RepaymentScheduleDTO> repaymentScheduleOverdueReader,
            BaseSkipListener<RepaymentScheduleDTO, OverdueUpdateCommand> skipListener) {
        
        return new StepBuilder("repaymentScheduleOverdueStep", jobRepository)
                .<RepaymentScheduleDTO, OverdueUpdateCommand>chunk(100)
                .transactionManager(transactionManager)
                .reader(repaymentScheduleOverdueReader)
                .processor(processor())
                .writer(writer())
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(10)
                .listener(skipListener)
                .build();
    }

    @Bean
    @StepScope
    public MyBatisPagingItemReader<RepaymentScheduleDTO> repaymentScheduleOverdueReader(
            @Value("#{jobParameters['baseDate']}") String baseDateParam) {

        LocalDate baseDate = LocalDate.parse(baseDateParam);
        Map<String, Object> params = new HashMap<>();
        params.put("baseDate", baseDate);

        return new MyBatisPagingItemReaderBuilder<RepaymentScheduleDTO>()
                .sqlSessionFactory(sqlSessionFactory)
                .queryId("org.teamsai.saibackend.domain.contract.mapper.RepaymentScheduleMapper.findOverdueCandidates")
                .parameterValues(params)
                .pageSize(100)
                .build();
    }

    private ItemProcessor<RepaymentScheduleDTO, OverdueUpdateCommand> processor() {
        return dto -> new OverdueUpdateCommand(dto.getScheduleId());
    }

    private MyBatisBatchItemWriter<OverdueUpdateCommand> writer() {
        return new MyBatisBatchItemWriterBuilder<OverdueUpdateCommand>()
                .sqlSessionFactory(sqlSessionFactory)
                .statementId("org.teamsai.saibackend.domain.contract.mapper.RepaymentScheduleMapper.updateStatusToOverdue")
                .build();
    }
}