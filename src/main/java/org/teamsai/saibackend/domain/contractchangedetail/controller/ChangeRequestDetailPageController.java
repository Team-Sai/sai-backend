package org.teamsai.saibackend.domain.contractchangedetail.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.teamsai.saibackend.domain.contract.service.ContractChangeService;

@Controller
@RequiredArgsConstructor
public class ChangeRequestDetailPageController {

    private final ContractChangeService contractChangeService;

    @GetMapping("/contracts/{contractId}/change-requests/{changeRequestId}")
    public String changeRequestDetailPage(
            @PathVariable Long contractId,
            @PathVariable Long changeRequestId,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            Model model
    ) {
        contractChangeService.checkAccess(contractId, userId);
        model.addAttribute("contractId", contractId);
        model.addAttribute("changeRequestId", changeRequestId);
        return "contractchangedetail/request-detail";
    }
}
