package org.teamsai.saibackend.domain.contractrepaymentschedule.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.response.RepaymentScheduleSummaryResponse;
import org.teamsai.saibackend.domain.contractrepaymentschedule.service.RepaymentScheduleService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contracts/{contractId}/schedules")
public class RepaymentScheduleController {

    private final RepaymentScheduleService repaymentScheduleService;

    @GetMapping
    public RepaymentScheduleSummaryResponse getScheduleSummary(
            @PathVariable Long contractId,
            @AuthenticationPrincipal(expression = "userId") Long userID
    ) {
        return repaymentScheduleService.getScheduleSummary(contractId, userID);
    }
}