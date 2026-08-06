package org.teamsai.saibackend.domain.contractdetail.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.teamsai.saibackend.domain.contractdetail.service.ContractDetailService;

@Controller
@RequiredArgsConstructor
public class ContractDetailPageController {

    private final ContractDetailService contractDetailService;

    @GetMapping("/contracts/{contractId}/contract-detail")
    public String detailResponsePage(
            @PathVariable Long contractId,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            Model model
    ) {
        boolean canRequestChange = contractDetailService.canRequestChange(contractId, userId);
       model.addAttribute("contractId", contractId);
       model.addAttribute("canRequestChange", canRequestChange);
       return "contractdetail/detail";

    }
}
