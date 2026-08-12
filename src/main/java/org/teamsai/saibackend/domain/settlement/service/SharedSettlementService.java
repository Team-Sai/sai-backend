package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSharedSettlementRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.CreateSharedSettlementResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementType;
import org.teamsai.saibackend.domain.settlement.type.SplitType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SharedSettlementService {

    private final SettlementMapper settlementMapper;
    private final SettlementAccountService settlementAccountService;
    private final SettlementValidator settlementValidator;
    private final SettlementParticipantRegistrationService participantRegistrationService;
    private final SettlementAmountCalculator settlementAmountCalculator;

    @Transactional
    public CreateSharedSettlementResponse create(
            Long ownerId,
            CreateSharedSettlementRequest request
    ) {
       settlementValidator.validateCreateRequest(request);
        BigDecimal perPersonAmount =
                settlementAmountCalculator.calculateEqualAmount(
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
        participantRegistrationService.registerParticipants(
                ownerId,
                settlement.getSettlementId(),
                request.getParticipants(),
                perPersonAmount
        );

        settlementAccountService.selectAccount(
                ownerId,
                settlement.getSettlementId(),
                request.getLinkedAccountId()
        );

        return CreateSharedSettlementResponse.builder()
                .settlementId(settlement.getSettlementId())
                .settlementType(settlement.getSettlementType())
                .settlementStatus(settlement.getSettlementStatus())
                .title(settlement.getTitle())
                .createdAt(settlement.getCreatedAt())
                .build();
    }
}