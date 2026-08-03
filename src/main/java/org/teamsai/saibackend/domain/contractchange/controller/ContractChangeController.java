package org.teamsai.saibackend.domain.contractchange.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saibackend.domain.contractchange.dto.request.ContractChangeRequest;
import org.teamsai.saibackend.domain.contractchange.dto.response.ContractSummaryResponse;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractReadDTO;
import org.teamsai.saibackend.domain.contractchange.service.ContractChangeService;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractChangeController {

    private final ContractChangeService contractChangeService;

    @GetMapping("/{contractId}")
    public ContractSummaryResponse getContract(
            @PathVariable Long contractId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        LoanContractReadDTO contract = contractChangeService.getContract(contractId, userId);
        return ContractSummaryResponse.from(contract);


    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{contractId}/change-requests")
    public LoanContractChangeDTO requestChange(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractChangeRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ){
        return contractChangeService.requestChange(contractId, request, userId);
    }
}
