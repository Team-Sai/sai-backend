package org.teamsai.saibackend.domain.contractdashboard.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.teamsai.saibackend.domain.contractdashboard.dto.response.DashboardResponse;
import org.teamsai.saibackend.domain.contractdashboard.service.DashboardService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardController  {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardResponse getDashboard(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleFilter,
            @RequestParam(required = false) String sortType,
            @RequestParam(defaultValue = "1") int page
    ) {
        return dashboardService.getDashboard(userId, keyword, roleFilter,sortType, page);
    }
}
