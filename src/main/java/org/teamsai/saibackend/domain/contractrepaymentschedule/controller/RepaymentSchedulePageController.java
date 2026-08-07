package org.teamsai.saibackend.domain.contractrepaymentschedule.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class RepaymentSchedulePageController {

    @GetMapping("/contracts/{contractId}/schedule")
    public String schedulePage(@PathVariable Long contractId, Model model) {
        model.addAttribute("contractId", contractId);
        return "contractrepaymentschedule/schedule";
    }
}