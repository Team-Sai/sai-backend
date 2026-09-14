package org.teamsai.saibackend.domain.contract.dto.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ContractRelationType {
    FAMILY("가족/친인척"),
    ACQUAINTANCE("지인/기타");

    private final String description;
}
