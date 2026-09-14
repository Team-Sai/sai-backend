package org.teamsai.saibackend.domain.notification.type;

import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.type.PaymentStatus;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;

public enum ReminderStage {
    D3(3, "정산 마감 예정 안내 (D-3)"),
    D1(1, "정산 마감 예정 안내 (D-1)"),
    DDAY(0, "정산 마감 안내 (오늘)");

    private final int daysBefore;
    private final String title;

    ReminderStage(int daysBefore, String title) {
        this.daysBefore = daysBefore;
        this.title = title;
    }

    public NotificationType type() {
        return switch (this) {
            case D3 -> NotificationType.SETTLEMENT_DUE_REMINDER_D3;
            case D1 -> NotificationType.SETTLEMENT_DUE_REMINDER_D1;
            case DDAY -> NotificationType.SETTLEMENT_DUE_REMINDER_DDAY;
        };
    }

    public String title() { return title; }

    public String contentFor(SettlementDTO settlement, PaymentObligationDTO obligation) {
        String statusNote = obligation.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID
                ? "남은 금액을 마저 납부해주세요."
                : "납부해주세요.";
        return switch (this) {
            case D3 -> String.format("'%s' 정산 마감이 3일 남았습니다. %s", settlement.getTitle(), statusNote);
            case D1 -> String.format("'%s' 정산 마감이 내일입니다. %s", settlement.getTitle(), statusNote);
            case DDAY -> String.format("'%s' 정산이 오늘 마감됩니다. %s", settlement.getTitle(), statusNote);
        };
    }
}
