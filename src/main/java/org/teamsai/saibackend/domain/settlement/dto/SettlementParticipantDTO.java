package org.teamsai.saibackend.domain.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantRole;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementParticipantDTO {

    private Long participantId;
    private Long invitationId;

    private SettlementParticipantRole participantRole;
    private SettlementParticipantStatus participantStatus;

    private LocalDateTime joinedAt;
}