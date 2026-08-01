package org.teamsai.saibackend.domain.payment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.payment.dto.PaymentRecordDTO;

import java.math.BigDecimal;

@Mapper
public interface PaymentRecordMapper {

    BigDecimal sumConfirmedAmountByObligationId(
            @Param("obligationId") Long obligationId
    );

    // MVP에서는 은행 거래 1건을 여러 납부의무로 분할 반영하지 않는다.
    // 추후 분할 반영을 지원할 경우 거래별 반영 합계 검증으로 확장한다.
    boolean existsByBankTransactionId(
            @Param("bankTransactionId") Long bankTransactionId
    );

    int insert(PaymentRecordDTO paymentRecord);
}
