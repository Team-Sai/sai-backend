package org.teamsai.saibackend.domain.integration.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(
        name = "통합 대시보드 API",
        description = "월별 통합 대시보드 조회 API"
)
@Controller
@RequiredArgsConstructor
public class IntegrationDashboardController {

    private final IntegrationDashboardService integrationDashboardService;

    @Operation(
            summary = "통합 대시보드 조회",
            description = "현재 로그인한 사용자의 지정 월 통합 대시보드 정보를 조회합니다. 조회 월을 생략하면 현재 월을 기준으로 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "통합 대시보드 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            )
    })
    @ResponseBody
    @GetMapping("/api/integration/dashboard")
    public ResponseEntity<IntegrationDashboardResponse> getDashboard(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "조회 월 (yyyy-MM)", example = "2026-08")
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

    @Operation(hidden = true)
    @GetMapping("/integration/dashboard")
    public String dashboardPage() {
        return "integration/dashboard";
    }
}
