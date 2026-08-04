package org.teamsai.saibackend.domain.settlement.dto;

import lombok.*;
import org.teamsai.saibackend.domain.settlement.type.SettlementInvitationStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementInvitationDTO {
    private Long invitationId;
    private Long settlementId;
    private Long invitedUserId;
    private SettlementInvitationStatus invitationStatus;
    private LocalDateTime invitedAt;
    private LocalDateTime acceptedAt;

}
