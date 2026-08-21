package org.teamsai.saibackend.domain.batch.common.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DailyBatchStartupRunner implements ApplicationRunner {

    private final DailyBatchPipelineScheduler dailyBatchPipelineScheduler;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[dev] 앱 시작 시 배치 파이프라인 1회 즉시 실행");
        dailyBatchPipelineScheduler.runPipeline();
    }
}