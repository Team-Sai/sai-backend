package org.teamsai.saibackend.domain.contract.dto.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RepaymentMethod {
    EQUAL_PRINCIPAL_AND_INTEREST("원리금균등상환"),
    EQUAL_PRINCIPAL("원금균등상환"),
    BULLET_REPAYMENT("만기일시상환");

    private final String description;
}
