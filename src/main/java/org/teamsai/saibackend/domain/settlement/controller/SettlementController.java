package org.teamsai.saibackend.domain.settlement.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSharedSettlementRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.CreateSharedSettlementResponse;
import org.teamsai.saibackend.domain.settlement.service.SharedSettlementService;
import org.teamsai.saibackend.global.security.CustomUserDetails;

@Tag(
        name = "정산 API",
        description = "공동정산 생성 및 관리 API"
)
@Controller
@RequiredArgsConstructor
public class SettlementController {

    private final SharedSettlementService sharedSettlementService;

    @GetMapping("/settlements")
    public String settlementListPage() {
        return "settlement/settlement-list";
    }

    @GetMapping("/settlements/new")
    public String settlementCreatePage() {
        return "settlement/settlement-create";
    }

    @Operation(
            summary = "공동정산 생성",
            description = "현재 로그인한 회원을 소유자로 하여 공동정산을 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "공동정산 생성 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 정산 생성 요청"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "공동정산 생성 실패"
            )
    })
    @ResponseBody
    @PostMapping("/api/settlements/shared")
    public ResponseEntity<CreateSharedSettlementResponse>
    createSharedSettlement(
            @AuthenticationPrincipal
            CustomUserDetails userDetails,

            @Valid
            @RequestBody
            CreateSharedSettlementRequest request
    ) {
        CreateSharedSettlementResponse response =
                sharedSettlementService.create(
                        userDetails.getUserId(),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}