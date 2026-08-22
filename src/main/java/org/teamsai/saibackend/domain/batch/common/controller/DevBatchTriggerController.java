package org.teamsai.saibackend.domain.batch.common.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dev/batch")
@Profile("dev")
@RequiredArgsConstructor
public class DevBatchTriggerController {

    private final JobOperator jobOperator;
    private final Map<String, Job> jobs; // Spring이 등록된 모든 Job 빈을 이름→빈으로 자동 주입

    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "repaymentScheduleOverdueJob", "상환 연체 처리",
            "settlementOverdueJob", "정산 연체 처리",
            "transactionSyncJob", "은행거래 동기화",
            "repaymentDueReminderJob", "상환 예정일 리마인드",
            "settlementDueReminderJob", "정산 마감일 리마인드",
            "recurringSettlementGenerationJob", "정기정산 자동생성",
            "bankTransactionRetryJob", "은행거래 매칭 재시도",
            "settlementAbandonmentJob", "정산 장기방치 감지",
            "settlementWriteOffJob", "결제의무 상각 처리(정산)",
            "repaymentWriteOffJob", "결제의무 상각 처리(상환)"
    );

    @GetMapping
    public List<Map<String, Object>> list() {
        return DISPLAY_NAMES.entrySet().stream()
                .map(e -> Map.<String, Object>of(
                        "jobName", e.getKey(),
                        "displayName", e.getValue(),
                        "registered", jobs.containsKey(e.getKey())
                ))
                .toList();
    }

    @PostMapping("/{jobName}")
    public ResponseEntity<?> trigger(
            @PathVariable String jobName,
            @RequestParam(required = false) String baseDate
    ) throws Exception {
        Job job = jobs.get(jobName);
        if (job == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "존재하지 않는 Job입니다: " + jobName,
                    "availableJobs", jobs.keySet()
            ));
        }

        LocalDate date = (baseDate != null) ? LocalDate.parse(baseDate) : LocalDate.now();
        JobParameters params = new JobParametersBuilder()
                .addString("baseDate", date.toString())
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

        log.info("[dev-trigger] {} 수동 실행 - baseDate={}", jobName, date);
        JobExecution execution = jobOperator.start(job, params);

        long readCount = execution.getStepExecutions().stream()
                .mapToLong(s -> s.getReadCount()).sum();
        long writeCount = execution.getStepExecutions().stream()
                .mapToLong(s -> s.getWriteCount()).sum();

        return ResponseEntity.ok(Map.of(
                "jobName", jobName,
                "displayName", DISPLAY_NAMES.getOrDefault(jobName, jobName),
                "baseDate", date.toString(),
                "status", execution.getStatus().toString(),
                "readCount", readCount,
                "writeCount", writeCount
        ));
    }
}