package org.teamsai.saibackend.domain.identity.dto.response;

public record IdentityPrepareResponse(
        String identityVerificationId,
        String storeId,
        String channelKey
) {
}
