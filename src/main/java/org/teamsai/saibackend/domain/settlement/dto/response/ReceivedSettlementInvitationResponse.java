package org.teamsai.saibackend.domain.settlement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.settlement.type.SettlementInvitationStatus;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivedSettlementInvitationResponse {

    private Long invitationId;
    private Long settlementId;

    private String settlementTitle;
    private String ownerName;

    private SettlementInvitationStatus invitationStatus;
    private LocalDateTime invitedAt;
    private LocalDateTime acceptedAt;



}
