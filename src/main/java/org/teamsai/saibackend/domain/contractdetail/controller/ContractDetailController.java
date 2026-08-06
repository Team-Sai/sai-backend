package org.teamsai.saibackend.domain.contractdetail.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saibackend.domain.contractdetail.dto.response.ContractDetailResponse;
import org.teamsai.saibackend.domain.contractdetail.service.ContractDetailService;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractDetailController {

    private final ContractDetailService contractDetailService;


    @GetMapping("/{contractId}/contract-detail")
    public ContractDetailResponse responseDetail(
            @PathVariable Long contractId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return contractDetailService.getCheck(contractId, userId);
    }
}

