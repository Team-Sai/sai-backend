package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.payment.service.SettlementPaymentService;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSettlementParticipantRequest;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSharedSettlementRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.CreateSharedSettlementResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementDetailResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementListResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementType;
import org.teamsai.saibackend.domain.settlement.type.SplitType;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.service.UserService;

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
    private final SettlementParticipantService participantService;
    private final SettlementPaymentService settlementPaymentService;
    private final UserService userService;
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
                        request.getParticipants().size()
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
        createParticipantsAndObligations(
                ownerId,
                settlement.getSettlementId(),
                request.getParticipants(),
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

    @Transactional(readOnly = true)
    public SettlementDetailResponse getSettlementDetail(Long settlementId, Long userId){
        SettlementDetailResponse response = settlementMapper.findDetailById(settlementId,userId)
                .orElseThrow(SettlementErrorCode.SETTLEMENT_NOT_FOUND::toException);

        if("NONE".equals(response.role())){
            throw SettlementErrorCode.SETTLEMENT_ACCESS_DENIED.toException();
        }
        return response;
    }

    private void validateCreateRequest(
            CreateSharedSettlementRequest request
    ) {
        if (request == null) {
            throw SettlementErrorCode
                    .INVALID_SETTLEMENT_REQUEST
                    .toException();
        }

        List<CreateSettlementParticipantRequest> participants =
                request.getParticipants();

        if (participants == null || participants.isEmpty()) {
            throw SettlementErrorCode
                    .SETTLEMENT_PARTICIPANT_REQUIRED
                    .toException();
        }

        validateDuplicateParticipants(participants);
    }

    private void validateDuplicateParticipants(
            List<CreateSettlementParticipantRequest> participants
    ) {
        Set<String> userTokens = new HashSet<>();

        for (CreateSettlementParticipantRequest participant
                : participants) {

            if (participant == null
                    || participant.getUserToken() == null
                    || participant.getUserToken().isBlank()) {
                throw SettlementErrorCode
                        .INVALID_SETTLEMENT_PARTICIPANT
                        .toException();
            }

            if (!userTokens.add(participant.getUserToken())) {
                throw SettlementErrorCode
                        .DUPLICATE_SETTLEMENT_PARTICIPANT
                        .toException();
            }
        }
    }


    private void createParticipantsAndObligations(
            Long ownerId,
            Long settlementId,
            List<CreateSettlementParticipantRequest> participants,
            BigDecimal expectedAmount
    ) {
        for (CreateSettlementParticipantRequest participantRequest
                : participants) {

            UserDTO participantUser =
                    userService.findRequestTarget(
                            ownerId,
                            participantRequest.getUserToken()
                    );

            Long participantId =
                    participantService.createParticipant(
                            settlementId,
                            participantUser.getUserId()
                    );

            settlementPaymentService.createObligation(
                    participantId,
                    expectedAmount
            );
        }
    }

    private BigDecimal calculatePerPersonAmount(
            BigDecimal totalAmount,
            int participantCount
    ) {
        if (totalAmount == null
                || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw SettlementErrorCode
                    .INVALID_SETTLEMENT_AMOUNT
                    .toException();
        }

        int totalParticipantCount = participantCount + 1;

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