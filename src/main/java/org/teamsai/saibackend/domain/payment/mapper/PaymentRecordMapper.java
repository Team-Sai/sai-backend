package org.teamsai.saibackend.domain.payment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.payment.dto.PaymentRecordDTO;
import org.teamsai.saibackend.domain.payment.type.PaymentTargetType;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface PaymentRecordMapper {


    BigDecimal sumConfirmedAmountByTarget(
            @Param("paymentTargetType") PaymentTargetType paymentTargetType,
            @Param("targetId") Long targetId
    );

    boolean existsByBankTransactionId(
            @Param("bankTransactionId") Long bankTransactionId
    );

    int insert(PaymentRecordDTO paymentRecord);

    List<PaymentRecordDTO> findConfirmedByTargetIds(
            @Param("paymentTargetType") PaymentTargetType paymentTargetType,
            @Param("targetIds") List<Long> targetIds
    );
}
