package org.teamsai.saibackend.domain.contractdetail.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.teamsai.saibackend.domain.contractdetail.dto.response.ContractDetailResponse;
import org.teamsai.saibackend.domain.contractdetail.service.ContractDetailService;

@Controller
@RequiredArgsConstructor
public class ContractDetailController {

    private final ContractDetailService contractDetailService;


    @GetMapping("/contracts/{contractId}/contract-detail")
    public String detailResponsePage(
            @PathVariable Long contractId,
            Model model
    ) {
        model.addAttribute("contractId", contractId);
        return "contractdetail/detail";
    }

    @ResponseBody
    @GetMapping("/api/contracts/{contractId}/contract-detail")
    public ContractDetailResponse responseDetail(
            @PathVariable Long contractId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return contractDetailService.getCheck(contractId, userId);
    }


}

