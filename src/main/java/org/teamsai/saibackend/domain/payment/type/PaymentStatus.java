package org.teamsai.saibackend.domain.payment.type;

public enum PaymentStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID;

    public boolean isUnresolved() {
        return this == UNPAID || this == PARTIALLY_PAID;
    }
}
