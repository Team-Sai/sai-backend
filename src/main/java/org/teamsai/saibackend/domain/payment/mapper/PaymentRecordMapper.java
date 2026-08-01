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

    int insert(PaymentRecordDTO paymentRecord);
}
