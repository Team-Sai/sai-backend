package org.teamsai.saibackend.domain.contract.type;

public enum RepaymentScheduleStatus {
    PENDING,
    OVERDUE,
    PAID,
    WRITTEN_OFF;

    public boolean isUnresolved() {
        return this == PENDING || this == OVERDUE;
    }

    public boolean isSettled() {
        return this == PAID || this == WRITTEN_OFF;
    }
}
