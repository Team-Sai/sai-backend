package org.teamsai.saibackend.domain.calendar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.teamsai.saibackend.domain.calendar.response.DashboardCalendarItemResponse;
import org.teamsai.saibackend.domain.integration.service.IntegrationDashboardService;

import java.time.LocalDate;
import java.util.List;

@Tag(
        name = "캘린더 API",
        description = "통합대시보드에서 전체보기 클릭 시 정산, 금전소비대차 목록을 캘린더에서 확인 가능"
)
@Controller
@RequiredArgsConstructor
public class CalendarController {

    private final IntegrationDashboardService integrationDashboardService;

    @Operation(
            summary = "날짜별 캘린더 상세 조회",
            description = "지정한 날짜에 해당하는 대여 상환 일정과 정산 일정을 함께 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @ResponseBody
    @GetMapping("/api/dashboard/calendar/{date}")
    public List<DashboardCalendarItemResponse> getCalendarDayDetail(
            @Parameter(description = "조회할 날짜 (yyyy-MM-dd)", example = "2026-08-14")
            @PathVariable LocalDate date,

            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return integrationDashboardService.getCalendarDayDetail(userId, date);
    }

    @Operation(hidden = true)
    @GetMapping("/calendar")
    public String calendarPage(){
        return "calendar/calendar";
    }
}