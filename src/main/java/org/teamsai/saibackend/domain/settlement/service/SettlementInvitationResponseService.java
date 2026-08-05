package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementInvitationDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementParticipantDTO;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementInvitationMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantRole;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantStatus;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SettlementInvitationResponseService {

    private final SettlementInvitationMapper invitationMapper;
    private final SettlementMapper settlementMapper;
    private final SettlementParticipantMapper participantMapper;
    private final SettlementInvitationValidator invitationValidator;

    @Transactional
    public void accept(Long userId, Long invitationId){
        SettlementInvitationDTO invitation = findInvitation(invitationId);

        invitationValidator.validateRespondableInvitation(invitation, userId);

        SettlementDTO settlement = findSettlementForUpdate(invitation.getSettlementId());

        invitationValidator.validateAcceptableSettlement(settlement);

        LocalDateTime acceptedAt = LocalDateTime.now();

        acceptInvitation(invitationId, acceptedAt);

        createMemberParticipant(invitationId,acceptedAt);

    }

    @Transactional
    public void reject(Long userId, Long invitationId){
        SettlementInvitationDTO invitation = findInvitation(invitationId);

        invitationValidator.validateRespondableInvitation(invitation, userId);

        int updatedCount = invitationMapper.reject(invitationId);

        if(updatedCount !=1 ){
            throw SettlementErrorCode.INVITATION_ALREADY_PROCESSED.toException();
        }
    }

    private SettlementInvitationDTO findInvitation(Long invitationId){
        return invitationMapper.findById(invitationId)
                .orElseThrow(SettlementErrorCode.SETTLEMENT_INVITATION_NOT_FOUND::toException);
    }

    private SettlementDTO findSettlementForUpdate(
            Long settlementId
    ) {
        return settlementMapper
                .findByIdForUpdate(settlementId)
                .orElseThrow(
                        SettlementErrorCode
                                .SETTLEMENT_NOT_FOUND
                                ::toException
                );
    }

    private void acceptInvitation(Long invitationID, LocalDateTime acceptedAt){
        int updateCount = invitationMapper.accept(invitationID, acceptedAt);

        if(updateCount != 1){
            throw SettlementErrorCode.INVITATION_ALREADY_PROCESSED.toException();
        }
    }

    private void createMemberParticipant(Long invitationId, LocalDateTime joinedAt){
        SettlementParticipantDTO participant = SettlementParticipantDTO.builder()
                .invitationId(invitationId)
                .participantRole(SettlementParticipantRole.MEMBER)
                .participantStatus(SettlementParticipantStatus.ACTIVE)
                .joinedAt(joinedAt)
                .build();

        int insertCount = participantMapper.insert(participant);

        if(insertCount != 1){
            throw  SettlementErrorCode.SETTLEMENT_PARTICIPANT_CREATE_FAILED.toException();
        }
    }
}
