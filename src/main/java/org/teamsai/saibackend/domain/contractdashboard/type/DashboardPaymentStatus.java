package org.teamsai.saibackend.domain.contractdashboard.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum DashboardPaymentStatus {

    WAITING("대기"),
    PAID("납부"),
    NONE("-");

    private final String description;


}
