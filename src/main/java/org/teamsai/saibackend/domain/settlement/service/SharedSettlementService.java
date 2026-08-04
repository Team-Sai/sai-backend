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
import org.teamsai.saibackend.global.exception.DomainException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SharedSettlementService {

    private final SettlementMapper settlementMapper;

    @Transactional
    public CreateSharedSettlementResponse create(Long ownerId, CreateSharedSettlementRequest request){
        LocalDateTime createdAt = LocalDateTime.now();

        SettlementDTO settlement = SettlementDTO.builder()
                .ownerId(ownerId)
                .settlementType(SettlementType.SHARED)
                .settlementStatus(SettlementStatus.IN_PROGRESS)
                .settlementCategory(request.getSettlementCategory())
                .title(request.getTitle())
                .splitType(request.getSplitType())
                .dueDate(request.getDueDate())
                .createdAt(createdAt)
                .build();

        int insertedCount = settlementMapper.insertSettlement(settlement);

        if (insertedCount != 1) {
            throw SettlementErrorCode.SETTLEMENT_CREATE_FAILED.toException();
        }

        return CreateSharedSettlementResponse
                .builder()
                .settlementId(settlement.getSettlementId())
                .settlementType(settlement.getSettlementType())
                .settlementStatus(settlement.getSettlementStatus())
                .title(settlement.getTitle())
                .createdAt(settlement.getCreatedAt())
                .build();
    }
}
