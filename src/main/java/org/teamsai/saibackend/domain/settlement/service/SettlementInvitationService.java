package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.payment.service.PaymentService;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementInvitationDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSettlementInvitationRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.CreateSettlementInvitationResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.ReceivedSettlementInvitationResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementInvitationMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementInvitationStatus;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.service.UserService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementInvitationService {

    private final SettlementMapper settlementMapper;
    private final SettlementInvitationMapper invitationMapper;
    private final UserService userService;
    private final SettlementInvitationValidator invitationValidator;
    private final PaymentService paymentService;
    private final SettlementParticipantService participantService;

    @Transactional
    public CreateSettlementInvitationResponse invite(
            Long ownerId,
            Long settlementId,
            CreateSettlementInvitationRequest request,
            BigDecimal expectedAmount
    ) {
        SettlementDTO settlement =
                findSettlement(settlementId);

        invitationValidator.validateInvitableSettlement(
                settlement,
                ownerId
        );

        validateExpectedAmount(expectedAmount);

        return createInvitationAndObligation(
                ownerId,
                settlementId,
                request,
                expectedAmount
        );
    }

    @Transactional
    public List<CreateSettlementInvitationResponse> inviteAll(
            Long ownerId,
            Long settlementId,
            List<CreateSettlementInvitationRequest> requests,
            BigDecimal expectedAmount
    ) {
        SettlementDTO settlement =
                findSettlement(settlementId);

        invitationValidator.validateInvitableSettlement(
                settlement,
                ownerId
        );

        validateExpectedAmount(expectedAmount);

        if (requests == null || requests.isEmpty()) {
            throw SettlementErrorCode
                    .SETTLEMENT_INVITEE_REQUIRED
                    .toException();
        }

        List<CreateSettlementInvitationResponse> responses =
                new ArrayList<>();

        for (CreateSettlementInvitationRequest request : requests) {
            responses.add(
                    createInvitationAndObligation(
                            ownerId,
                            settlementId,
                            request,
                            expectedAmount
                    )
            );
        }

        return responses;
    }

    private CreateSettlementInvitationResponse
    createInvitationAndObligation(
            Long ownerId,
            Long settlementId,
            CreateSettlementInvitationRequest request,
            BigDecimal expectedAmount
    ) {
        if (request == null
                || request.getUserToken() == null
                || request.getUserToken().isBlank()) {
            throw SettlementErrorCode
                    .INVALID_SETTLEMENT_INVITEE
                    .toException();
        }

        UserDTO invitedUser =
                userService.findRequestTarget(
                        ownerId,
                        request.getUserToken()
                );

        invitationValidator.validateInviteTarget(
                settlementId,
                invitedUser.getUserId()
        );

        LocalDateTime invitedAt = LocalDateTime.now();

        SettlementInvitationDTO invitation =
                SettlementInvitationDTO.builder()
                        .settlementId(settlementId)
                        .invitedUserId(invitedUser.getUserId())
                        .invitationStatus(
                                SettlementInvitationStatus.INVITED
                        )
                        .invitedAt(invitedAt)
                        .acceptedAt(null)
                        .build();

        int insertedCount =
                invitationMapper.insert(invitation);

        if (insertedCount != 1) {
            throw SettlementErrorCode
                    .SETTLEMENT_INVITATION_CREATE_FAILED
                    .toException();
        }

        Long participantId =
                participantService.createFromInvitation(
                        invitation.getInvitationId()
                );

        paymentService.createObligation(
                participantId,
                expectedAmount
        );

        return CreateSettlementInvitationResponse.builder()
                .invitationId(invitation.getInvitationId())
                .settlementId(settlementId)
                .invitedUserToken(invitedUser.getUserToken())
                .invitedUserName(invitedUser.getName())
                .invitationStatus(invitation.getInvitationStatus())
                .invitedAt(invitation.getInvitedAt())
                .build();
    }

    private void validateExpectedAmount(
            BigDecimal expectedAmount
    ) {
        if (expectedAmount == null
                || expectedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw SettlementErrorCode
                    .INVALID_SETTLEMENT_AMOUNT
                    .toException();
        }
    }

    private SettlementDTO findSettlement(
            Long settlementId
    ) {
        return settlementMapper.findById(settlementId)
                .orElseThrow(
                        SettlementErrorCode
                                .SETTLEMENT_NOT_FOUND
                                ::toException
                );
    }

    public List<ReceivedSettlementInvitationResponse>
    findReceivedInvitations(Long userId) {
        return invitationMapper.findReceivedInvitations(userId);
    }
}