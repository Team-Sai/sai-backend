package org.teamsai.saibackend.domain.matching.type;

public enum FailureReason {
    TRANSIENT_SERVER_ERROR,
    DATA_MISMATCH,
    VALIDATION_ERROR;

    public int maxRetryCount() {
        return switch (this) {
            case TRANSIENT_SERVER_ERROR -> 5;
            case DATA_MISMATCH -> 14;
            case VALIDATION_ERROR -> 0;
        };
    }
}