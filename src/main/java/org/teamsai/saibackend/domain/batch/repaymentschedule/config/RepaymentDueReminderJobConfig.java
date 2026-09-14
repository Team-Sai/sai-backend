package org.teamsai.saibackend.domain.batch.repaymentschedule.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisCursorItemReader;
import org.mybatis.spring.batch.builder.MyBatisCursorItemReaderBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.teamsai.saibackend.domain.batch.common.listener.BaseSkipListener;
import org.teamsai.saibackend.domain.batch.common.listener.LoggingJobExecutionListener;
import org.teamsai.saibackend.domain.contract.dto.RepaymentScheduleDTO;
import org.teamsai.saibackend.domain.contract.mapper.RepaymentScheduleMapper;
import org.teamsai.saibackend.domain.notification.service.NotificationService;
import org.teamsai.saibackend.domain.notification.type.NotificationType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RepaymentDueReminderJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SqlSessionFactory sqlSessionFactory;
    private final NotificationService notificationService;
    private final RepaymentScheduleMapper repaymentScheduleMapper;
    private final LoggingJobExecutionListener loggingJobExecutionListener;

    @Bean
    public Job repaymentDueReminderJob(Step repaymentDueReminderStep) {
        return new JobBuilder("repaymentDueReminderJob", jobRepository)
                .start(repaymentDueReminderStep)
                .listener(loggingJobExecutionListener)
                .build();
    }

    @Bean
    public Step repaymentDueReminderStep(
            MyBatisCursorItemReader<RepaymentScheduleDTO> repaymentDueReminderReader,
            ItemWriter<RepaymentScheduleDTO> repaymentDueReminderWriter,
            BaseSkipListener<RepaymentScheduleDTO, RepaymentScheduleDTO> skipListener) {

        return new StepBuilder("repaymentDueReminderStep", jobRepository)
                .<RepaymentScheduleDTO, RepaymentScheduleDTO>chunk(100)
                .transactionManager(transactionManager)
                .reader(repaymentDueReminderReader)
                .writer(repaymentDueReminderWriter)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(10)
                .listener(skipListener)
                .build();
    }

    @Bean
    @StepScope
    public MyBatisCursorItemReader<RepaymentScheduleDTO> repaymentDueReminderReader(
            @Value("#{jobParameters['baseDate']}") String baseDateParam) {

        LocalDate baseDate = LocalDate.parse(baseDateParam);
        List<LocalDate> targetDates = List.of(
                baseDate.plusDays(3),
                baseDate.plusDays(1),
                baseDate
        );

        Map<String, Object> params = new HashMap<>();
        params.put("dueDates", targetDates);

        return new MyBatisCursorItemReaderBuilder<RepaymentScheduleDTO>()
                .sqlSessionFactory(sqlSessionFactory)
                .queryId("org.teamsai.saibackend.domain.contract.mapper.RepaymentScheduleMapper.findDueOnDates")
                .parameterValues(params)
                .build();
    }

    @Bean
    @StepScope
    public ItemWriter<RepaymentScheduleDTO> repaymentDueReminderWriter(
            @Value("#{jobParameters['baseDate']}") String baseDateParam) {

        LocalDate baseDate = LocalDate.parse(baseDateParam);

        return chunk -> {
            for (RepaymentScheduleDTO schedule : chunk.getItems()) {
                NotificationStage stage = resolveStage(schedule.getDueDate(), baseDate);
                if (stage == null) {
                    continue;
                }

                Long debtorUserId = repaymentScheduleMapper.findDebtorUserIdByContractId(schedule.getContractId());
                if (debtorUserId == null) {
                    log.warn("[repaymentDueReminder] 채무자 조회 실패 - contractId={}", schedule.getContractId());
                    continue;
                }

                notificationService.createIfAbsent(
                        debtorUserId,
                        stage.type(),
                        stage.title(),
                        stage.contentFor(schedule.getDueDate()),
                        schedule.getScheduleId(),
                        null
                );
            }
        };
    }

    private NotificationStage resolveStage(LocalDate dueDate, LocalDate today) {
        long daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate);
        return switch ((int) daysUntilDue) {
            case 3 -> NotificationStage.D3;
            case 1 -> NotificationStage.D1;
            case 0 -> NotificationStage.DDAY;
            default -> null;
        };
    }

    private enum NotificationStage {
        D3(NotificationType.REPAYMENT_DUE_REMINDER_D3, "상환 예정일 안내 (D-3)") {
            String contentFor(LocalDate dueDate) {
                return String.format("3일 후(%s) 상환 예정입니다.", dueDate);
            }
        },
        D1(NotificationType.REPAYMENT_DUE_REMINDER_D1, "상환 예정일 안내 (D-1)") {
            String contentFor(LocalDate dueDate) {
                return String.format("내일(%s) 상환 예정입니다.", dueDate);
            }
        },
        DDAY(NotificationType.REPAYMENT_DUE_REMINDER_DDAY, "상환 예정일 안내 (오늘)") {
            String contentFor(LocalDate dueDate) {
                return "오늘 상환 예정입니다.";
            }
        };

        private final NotificationType type;
        private final String title;

        NotificationStage(NotificationType type, String title) {
            this.type = type;
            this.title = title;
        }

        NotificationType type() { return type; }
        String title() { return title; }
        abstract String contentFor(LocalDate dueDate);
    }
}
