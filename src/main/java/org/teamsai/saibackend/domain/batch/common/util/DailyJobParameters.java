package org.teamsai.saibackend.domain.batch.common.util;

import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import java.time.LocalDate;

public class DailyJobParameters {

    private static final String BASE_DATE = "baseDate";
    private static final String RUN_ID = "runId";

    private DailyJobParameters() {
    }

    public static JobParameters today() {
        LocalDate today = LocalDate.now();
        return new JobParametersBuilder()
                .addString(BASE_DATE, today.toString())
                .addLong(RUN_ID, System.currentTimeMillis())
                .toJobParameters();
    }
}