package org.teamsai.saibackend.domain.contractdashboard.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum TransactionCategory {

    RECEIVE("수취"), PAY("납부");

    private final String description;
}
