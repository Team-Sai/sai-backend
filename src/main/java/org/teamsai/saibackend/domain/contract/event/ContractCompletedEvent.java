package org.teamsai.saibackend.domain.contract.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;

@Getter
@RequiredArgsConstructor
public class ContractCompletedEvent {
    private final LoanContractResponse completedContract;
}