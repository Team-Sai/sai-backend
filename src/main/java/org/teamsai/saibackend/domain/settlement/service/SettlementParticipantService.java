package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.settlement.dto.SettlementParticipantDTO;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantRole;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantStatus;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SettlementParticipantService {

    private final SettlementParticipantMapper settlementParticipantMapper;
@Transactional

    public Long createParticipant(
            Long settlementId,
            Long userId
    ) {
        SettlementParticipantDTO participant =
                SettlementParticipantDTO.builder()
                        .settlementId(settlementId)
                        .userId(userId)
                        .participantRole(
                                SettlementParticipantRole.MEMBER
                        )
                        .participantStatus(
                                SettlementParticipantStatus.ACTIVE
                        )
                        .joinedAt(LocalDateTime.now())
                        .build();

        int insertedCount =
                settlementParticipantMapper.insert(participant);

        if (insertedCount != 1) {
            throw SettlementErrorCode
                    .SETTLEMENT_PARTICIPANT_CREATE_FAILED
                    .toException();
        }

        return participant.getParticipantId();
    }
}
