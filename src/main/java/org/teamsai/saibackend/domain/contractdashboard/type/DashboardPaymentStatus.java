package org.teamsai.saibackend.domain.contractdashboard.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum DashboardPaymentStatus {

    ONGOING("납부중"),
    PAID("납부완료");

    private final String description;


}
