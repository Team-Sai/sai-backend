package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementInvitationDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSettlementInvitationRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.CreateSettlementInvitationResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementInvitationMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementInvitationStatus;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.service.UserService;

import java.time.LocalDateTime;


@Service
@RequiredArgsConstructor
public class SettlementInvitationService {

    private final SettlementMapper settlementMapper;
    private final SettlementInvitationMapper invitationMapper;
    private final SettlementParticipantMapper participantMapper;
    private final UserService userService;
    private final SettlementInvitationValidator invitationValidator;

    @Transactional
    public CreateSettlementInvitationResponse invite(Long ownerId, Long settlementId, CreateSettlementInvitationRequest request){
        SettlementDTO settlement = settlementMapper.findById(settlementId)
                .orElseThrow(
                        SettlementErrorCode.SETTLEMENT_NOT_FOUND::toException
                );
        invitationValidator.validateInvitableSettlement(settlement,ownerId);
        UserDTO invitedUser = userService.findRequestTarget(ownerId,request.getUserToken());
        invitationValidator.validateInviteTarget(settlementId,invitedUser.getUserId());

        boolean alreadyParticipant = participantMapper.existsActiveParticipant(settlementId,invitedUser.getUserId());

        if(alreadyParticipant){
            throw SettlementErrorCode.ALREADY_CLOSED_SETTLEMENT.toException();
        }

        boolean duplicateInvitation = invitationMapper.existsInvitedInvitation(settlementId,invitedUser.getUserId());

        if(duplicateInvitation){
            throw SettlementErrorCode.DUPLICATE_SETTLEMENT_INVITATION.toException();
        }

        LocalDateTime invitedAt = LocalDateTime.now();


        SettlementInvitationDTO invitation =
                SettlementInvitationDTO.builder()
                        .settlementId(settlementId)
                        .invitedUserId(invitedUser.getUserId())
                        .invitationStatus(SettlementInvitationStatus.INVITED)
                        .invitedAt(invitedAt)
                        .acceptedAt(null)
                        .build();
        int insertCount = invitationMapper.insert(invitation);

        if(insertCount != 1){
            throw SettlementErrorCode.SETTLEMENT_INVITATION_CREATE_FAILED.toException();
        }

        return CreateSettlementInvitationResponse.builder()
                .invitationId(invitation.getInvitationId())
                .settlementId(settlementId)
                .invitedUserToken(invitedUser.getUserToken())
                .invitedUserName(invitedUser.getName())
                .invitationStatus(invitation.getInvitationStatus())
                .invitedAt(invitation.getInvitedAt())
                .build();


    }

    private SettlementDTO findSettlement(Long settlementId){
        return settlementMapper.findById(settlementId)
                .orElseThrow(SettlementErrorCode.SETTLEMENT_NOT_FOUND::toException);
    }


}
