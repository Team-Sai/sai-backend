package org.teamsai.saibackend.domain.contractchange.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contractchange.dto.request.ContractChangeRequest;
import org.teamsai.saibackend.domain.contractchange.service.ContractChangeService;

@Controller
@RequiredArgsConstructor
public class ContractChangeController {

    private final ContractChangeService contractChangeService;

    @GetMapping("/contracts/{contractId}/change-request")
    public String changeRequestPage(
            @PathVariable Long contractId,
            Model model
    ) {
        model.addAttribute("contractId", contractId);
        return "contractchange/request";
    }


    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/contracts/{contractId}/change-requests")
    public LoanContractChangeDTO requestChange(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractChangeRequest request,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ){
        return contractChangeService.requestChange(contractId, request, userId);
    }
}
