package org.teamsai.saibackend.domain.batch.common.notification;

import java.util.Map;

public class JobDisplayNames {

    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "repaymentScheduleOverdueJob", "상환 연체 처리",
            "settlementOverdueJob", "정산 연체 처리",
            "transactionSyncJob", "은행거래 동기화",
            "repaymentDueReminderJob", "상환 예정일 리마인드",
            "settlementDueReminderJob", "정산 마감일 리마인드",
            "recurringSettlementGenerationJob", "정기정산 자동생성"
    );

    private JobDisplayNames() {
    }

    public static String resolve(String jobName) {
        return DISPLAY_NAMES.getOrDefault(jobName, jobName);
    }
}