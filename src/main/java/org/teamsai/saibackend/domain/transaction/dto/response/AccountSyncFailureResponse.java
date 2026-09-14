package org.teamsai.saibackend.domain.transaction.dto.response;

public record AccountSyncFailureResponse(
        Long linkedAccountId,
        String errorCode
) {
}
