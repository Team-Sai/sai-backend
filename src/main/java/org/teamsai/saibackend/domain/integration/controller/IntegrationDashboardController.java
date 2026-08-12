package org.teamsai.saibackend.domain.integration.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.teamsai.saibackend.domain.integration.dto.response.IntegrationDashboardResponse;
import org.teamsai.saibackend.domain.integration.service.IntegrationDashboardService;
import org.teamsai.saibackend.global.security.CustomUserDetails;

import java.time.YearMonth;

@Controller
@RequiredArgsConstructor
public class IntegrationDashboardController {

    private final IntegrationDashboardService integrationDashboardService;

    @GetMapping("/integration/dashboard")
    public String dashboardPage() {
        return "integration/dashboard";
    }

    @ResponseBody
    @GetMapping("/api/integration/dashboard")
    public ResponseEntity<IntegrationDashboardResponse> getDashboard(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonth
    ) {
        YearMonth requestedMonth = yearMonth == null
                ? YearMonth.now()
                : yearMonth;

        IntegrationDashboardResponse response =
                integrationDashboardService.getDashboard(
                        userDetails.getUserId(),
                        requestedMonth
                );
        return ResponseEntity.ok(response);
    }
}
