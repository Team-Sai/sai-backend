package org.teamsai.saibackend.domain.contractchange.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saibackend.domain.contractchange.dto.ContractChangeRequest;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractReadDTO;
import org.teamsai.saibackend.domain.contractchange.service.ContractChangeService;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractChangeController {

    private final ContractChangeService contractChangeService;

    @GetMapping("/{contractId}")
    public LoanContractReadDTO getContract(@PathVariable Long contractId) {
        return contractChangeService.getContract(contractId);


    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{contractId}/change-requests")
    public LoanContractChangeDTO requestChange(
            @PathVariable Long contractId,
            @RequestBody ContractChangeRequest request
    ){
        return contractChangeService.requestChange(contractId, request);
    }
}
