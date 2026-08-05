package org.teamsai.saibackend.domain.contractdetail.controller;

import org.springframework.ui.Model;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.teamsai.saibackend.domain.contractdetail.dto.response.ContractDetailResponse;
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
       ContractDetailResponse result = contractDetailService.getCheck(contractId, userId);
       model.addAttribute("contractId", contractId);
       model.addAttribute("canRequestChange", result.isCanRequestChange());
       return "contractdetail/detail";

    }
}
