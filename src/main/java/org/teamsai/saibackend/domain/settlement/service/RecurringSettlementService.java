package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.settlement.dto.RecurringSettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateRecurringSettlementRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.CreateRecurringSettlementResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.RecurringSettlementManagementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementType;
import org.teamsai.saibackend.domain.settlement.type.SplitType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RecurringSettlementService {
    private final RecurringSettlementManagementMapper recurringSettlementManagementMapper;

    private final SettlementMapper settlementMapper;

    private final RecurringSettlementValidator recurringSettlementValidator;

    private final SettlementAmountCalculator settlementAmountCalculator;

    private final SettlementParticipantRegistrationService participantRegistrationService;

    private final SettlementAccountService settlementAccountService;

    @Transactional
    public CreateRecurringSettlementResponse create(
            Long ownerId, CreateRecurringSettlementRequest request
    ){
        recurringSettlementValidator.validateCreateRequest(request);

        BigDecimal perPersonAmount =
                settlementAmountCalculator.calculateEqualAmount(
                        request.getTotalAmount(),
                        request.getParticipants().size()
                );

        LocalDateTime createdAt = LocalDateTime.now();

        RecurringSettlementDTO recurringSettlement =
                RecurringSettlementDTO.builder()
                        .ownerId(ownerId)
                        .settlementCategory(request.getSettlementCategory())
                        .title(request.getTitle())
                        .splitType(SplitType.EQUAL)
                        .totalAmount(request.getTotalAmount())
                        .cycleRule(request.getCycleRule())
                        .startDate(request.getStartDate())
                        .endDate(request.getEndDate())
                        .createdAt(createdAt)
                        .build();
        int recurringInserted = recurringSettlementManagementMapper.insert(
                recurringSettlement
        );

        if(recurringInserted != 1){
            throw SettlementErrorCode.SETTLEMENT_CREATE_FAILED.toException();
        }

        SettlementDTO firstSettlement =
                SettlementDTO.builder()
                        .recurringSettlementId(recurringSettlement.getRecurringSettlementId())
                        .ownerId(ownerId)
                        .settlementType(SettlementType.RECURRING)
                        .settlementStatus(SettlementStatus.IN_PROGRESS)
                        .settlementCategory(request.getSettlementCategory())
                        .title(request.getTitle())
                        .splitType(SplitType.EQUAL)
                        .totalAmount(request.getTotalAmount())
                        .dueDate(null)
                        .cycleDate(request.getStartDate())
                        .createdAt(createdAt)
                        .build();

        int settlementInserted =
                settlementMapper.insertSettlement(firstSettlement);

        if(settlementInserted != 1){
            throw SettlementErrorCode.SETTLEMENT_CREATE_FAILED.toException();
        }

        participantRegistrationService.registerParticipants(
                ownerId,firstSettlement.getSettlementId(),request.getParticipants(),perPersonAmount
        );

        settlementAccountService.selectAccount(
                ownerId, firstSettlement.getSettlementId(), request.getLinkedAccountId());

        return CreateRecurringSettlementResponse.builder()
                .recurringSettlementId(recurringSettlement.getRecurringSettlementId())
                .firstSettlementId(firstSettlement.getSettlementId())
                .settlementType(firstSettlement.getSettlementType())
                .title(firstSettlement.getTitle())
                .cycleRule(recurringSettlement.getCycleRule())
                .startDate(recurringSettlement.getStartDate())
                .endDate(recurringSettlement.getEndDate())
                .createdAt(createdAt)
                .build();

    }
}
