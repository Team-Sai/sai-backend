package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSettlementInvitationRequest;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSharedSettlementRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.CreateSharedSettlementResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementListResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementType;
import org.teamsai.saibackend.domain.settlement.type.SplitType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SharedSettlementService {

    private static final int WON_SCALE = 0;

    private final SettlementMapper settlementMapper;
    private final SettlementInvitationService invitationService;
    private final SettlementAccountService settlementAccountService;

    @Transactional
    public CreateSharedSettlementResponse create(
            Long ownerId,
            CreateSharedSettlementRequest request
    ) {
        validateCreateRequest(request);

        BigDecimal perPersonAmount =
                calculatePerPersonAmount(
                        request.getTotalAmount(),
                        request.getInvitations().size()
                );

        LocalDateTime createdAt = LocalDateTime.now();

        SettlementDTO settlement =
                SettlementDTO.builder()
                        .ownerId(ownerId)
                        .settlementType(SettlementType.SHARED)
                        .settlementStatus(
                                SettlementStatus.IN_PROGRESS
                        )
                        .settlementCategory(
                                request.getSettlementCategory()
                        )
                        .title(request.getTitle())

                        // 실제 N빵 enum 상수명으로 변경
                        .splitType(SplitType.EQUAL)

                        .totalAmount(request.getTotalAmount())
                        .dueDate(request.getDueDate())
                        .createdAt(createdAt)
                        .build();

        int insertedCount =
                settlementMapper.insertSettlement(settlement);

        if (insertedCount != 1) {
            throw SettlementErrorCode
                    .SETTLEMENT_CREATE_FAILED
                    .toException();
        }

        invitationService.inviteAll(
                ownerId,
                settlement.getSettlementId(),
                request.getInvitations(),
                perPersonAmount
        );

        settlementAccountService.selectAccount(
            ownerId,settlement.getSettlementId(),request.getLinkedAccountId()
        );

        return CreateSharedSettlementResponse.builder()
                .settlementId(settlement.getSettlementId())
                .settlementType(settlement.getSettlementType())
                .settlementStatus(settlement.getSettlementStatus())
                .title(settlement.getTitle())
                .createdAt(settlement.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<SettlementListResponse> getSettlementList(Long userId) {
        return settlementMapper.findAllByUserId(userId);
    }

    private void validateCreateRequest(
            CreateSharedSettlementRequest request
    ) {
        if (request == null) {
            throw SettlementErrorCode
                    .INVALID_SETTLEMENT_REQUEST
                    .toException();
        }

        List<CreateSettlementInvitationRequest> invitations =
                request.getInvitations();

        if (invitations == null || invitations.isEmpty()) {
            throw SettlementErrorCode
                    .SETTLEMENT_INVITEE_REQUIRED
                    .toException();
        }

        validateDuplicateInvitees(invitations);
    }

    private void validateDuplicateInvitees(
            List<CreateSettlementInvitationRequest> invitations
    ) {
        Set<String> userTokens = new HashSet<>();

        for (CreateSettlementInvitationRequest invitation
                : invitations) {
            if (invitation == null
                    || invitation.getUserToken() == null
                    || invitation.getUserToken().isBlank()) {
                throw SettlementErrorCode
                        .INVALID_SETTLEMENT_INVITEE
                        .toException();
            }

            if (!userTokens.add(invitation.getUserToken())) {
                throw SettlementErrorCode
                        .DUPLICATE_SETTLEMENT_INVITEE
                        .toException();
            }
        }
    }

    private BigDecimal calculatePerPersonAmount(
            BigDecimal totalAmount,
            int inviteeCount
    ) {
        if (totalAmount == null
                || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw SettlementErrorCode
                    .INVALID_SETTLEMENT_AMOUNT
                    .toException();
        }

        int totalParticipantCount = inviteeCount + 1;

        BigDecimal perPersonAmount =
                totalAmount.divide(
                        BigDecimal.valueOf(totalParticipantCount),
                        WON_SCALE,
                        RoundingMode.DOWN
                );

        if (perPersonAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw SettlementErrorCode
                    .INVALID_SETTLEMENT_AMOUNT
                    .toException();
        }

        return perPersonAmount;
    }
}