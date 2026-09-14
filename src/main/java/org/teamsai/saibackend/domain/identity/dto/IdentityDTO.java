package org.teamsai.saibackend.domain.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.identity.type.IdentityPurpose;
import org.teamsai.saibackend.domain.identity.type.IdentityStatus;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityDTO {

    private Long identityId;
    private String identityVerificationId;
    private Long userId;

    private IdentityPurpose purpose;
    private IdentityStatus status;

    private LocalDateTime requestedAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;

    private String failureReason;
}
