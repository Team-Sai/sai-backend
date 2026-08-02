package org.teamsai.saibackend.domain.contractchange.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.teamsai.saibackend.domain.contractchange.service.ContractChangeService;

@Controller
@RequiredArgsConstructor
public class ContractChangePageController {

    private  final ContractChangeService contractChangeService;

    @GetMapping("/contracts/{contractId}/change-request")
    public String changeRequestPage(
            @PathVariable Long contractId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        contractChangeService.getContract(contractId, userId);
        return "contractchange/request";
    }
}