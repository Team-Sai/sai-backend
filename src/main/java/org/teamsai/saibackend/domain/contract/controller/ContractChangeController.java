package org.teamsai.saibackend.domain.contract.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saibackend.domain.contract.dto.request.ContractChangeRequest;

import org.teamsai.saibackend.domain.contract.dto.LoanContractChangeDTO;

import org.teamsai.saibackend.domain.contract.service.ContractChangeService;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractChangeController {

    private final ContractChangeService contractChangeService;



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
