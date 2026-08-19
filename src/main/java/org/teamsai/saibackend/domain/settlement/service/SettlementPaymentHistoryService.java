package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.payment.dto.PaymentRecordDTO;
import org.teamsai.saibackend.domain.payment.service.PaymentRecordService;
import org.teamsai.saibackend.domain.payment.type.PaymentTargetType;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentHistoryResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentObligationResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentStatusResponse;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.mapper.BankTransactionMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 참여자별 납부/은행거래 상세 내역을 다른 도메인의 기존 조회 로직을 조합해서 구성한다.
 * - 참여자 이름: SettlementPaymentStatusService.getPaymentStatus()의 obligations 재사용
 * - 납부 승인 내역: PaymentRecordService.findConfirmedRecordsByTargetIds() 재사용
 * - 연결 거래 정보: BankTransactionMapper.findById() 재사용
 */
@Service
@RequiredArgsConstructor
public class SettlementPaymentHistoryService {

    private final SettlementPaymentStatusService settlementPaymentStatusService;
    private final PaymentRecordService paymentRecordService;
    private final BankTransactionMapper bankTransactionMapper;

    @Transactional(readOnly = true)
    public List<SettlementPaymentHistoryResponse> getPaymentHistory(Long settlementId, Long userId) {
        SettlementPaymentStatusResponse paymentStatus =
                settlementPaymentStatusService.getPaymentStatus(settlementId, userId);

        Map<Long, String> payerNameByObligationId = paymentStatus.getObligations().stream()
                .collect(Collectors.toMap(
                        SettlementPaymentObligationResponse::getPaymentObligationId,
                        SettlementPaymentObligationResponse::getParticipantName
                ));

        List<PaymentRecordDTO> records = paymentRecordService.findConfirmedRecordsByTargetIds(
                PaymentTargetType.SETTLEMENT,
                List.copyOf(payerNameByObligationId.keySet())
        );

        return records.stream()
                .map(record -> toResponse(record, payerNameByObligationId))
                .toList();
    }

    private SettlementPaymentHistoryResponse toResponse(
            PaymentRecordDTO record,
            Map<Long, String> payerNameByObligationId
    ) {
        BankTransactionDTO transaction = bankTransactionMapper
                .findById(record.getBankTransactionId())
                .orElse(null);

        return SettlementPaymentHistoryResponse.builder()
                .paymentRecordId(record.getPaymentRecordId())
                .recordedAt(record.getRecordedAt())
                .payerName(payerNameByObligationId.get(record.getTargetId()))
                .amount(record.getAmount())
                .sourceType(record.getSourceType())
                .bankTransactionId(record.getBankTransactionId())
                .counterpartyName(transaction != null ? transaction.getCounterpartyName() : null)
                .externalTransactionId(transaction != null ? transaction.getExternalTransactionId() : null)
                .build();
    }
}
