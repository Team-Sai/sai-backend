package org.teamsai.saibackend.domain.contract.dto.request;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public enum RepaymentMethod {
    EQUAL_PAYMENT,   // 원리금균등상환 (매달 내는 총 금액이 동일)
    EQUAL_PRINCIPAL, // 원금균등상환 (매달 내는 원금이 동일)
    LUMP_SUM         // 만기일시상환 (만기에 한 번에 갚음)
}
