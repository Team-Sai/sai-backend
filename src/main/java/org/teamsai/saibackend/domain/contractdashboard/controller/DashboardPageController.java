package org.teamsai.saibackend.domain.contractdashboard.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardPageController {

    @GetMapping("/dashboard")
    public String dashboardPage() {
        return "contractdashboard/dashboard";
    }
}