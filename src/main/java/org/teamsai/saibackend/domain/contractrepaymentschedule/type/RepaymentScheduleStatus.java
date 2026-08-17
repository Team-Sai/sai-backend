package org.teamsai.saibackend.domain.contractrepaymentschedule.type;

public enum RepaymentScheduleStatus {
    PENDING, PAID, OVERDUE;

    public boolean isUnresolved() {
        return this == PENDING || this == OVERDUE;
    }

    public boolean isSettled() {
        return this == PAID;
    }
}
