package org.teamsai.saibackend.domain.contractrepaymentschedule.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.teamsai.saibackend.domain.contractrepaymentschedule.dto.response.RepaymentScheduleSummaryResponse;
import org.teamsai.saibackend.domain.contractrepaymentschedule.service.RepaymentScheduleService;

@Controller
@RequiredArgsConstructor
public class RepaymentScheduleController {

    private final RepaymentScheduleService repaymentScheduleService;

    @GetMapping("/contracts/{contractId}/schedule")
    public String schedulePage(
            @PathVariable Long contractId,
            Model model
    ) {
        model.addAttribute("contractId", contractId);
        return "contractrepaymentschedule/schedule";
    }

    @ResponseBody
    @GetMapping("/api/contracts/{contractId}/schedules")
    public RepaymentScheduleSummaryResponse getScheduleSummary(
            @PathVariable Long contractId,
            @AuthenticationPrincipal(expression = "userId") Long userID
    ) {
        return repaymentScheduleService.getScheduleSummary(contractId, userID);
    }
}