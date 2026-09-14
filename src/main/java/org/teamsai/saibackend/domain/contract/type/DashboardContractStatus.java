package org.teamsai.saibackend.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum DashboardContractStatus {

    ONGOING("진행중"), COMPLETED("완료");

    private final String description;
}
