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
import org.springframework.web.bind.annotation.*;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSharedSettlementRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.CreateSharedSettlementResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementCloseResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentStatusResponse;
import org.teamsai.saibackend.domain.settlement.service.SettlementCloseService;
import org.teamsai.saibackend.domain.settlement.service.SettlementPaymentStatusService;
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
    private final SettlementPaymentStatusService settlementPaymentStatusService;
    private final SettlementCloseService settlementCloseService;

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

    @Operation(
            summary = "정산 납부 현황 조회",
            description = "정산별 납부의무, 납부금액, 잔여금액, 진행률을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "정산 납부 현황 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "정산을 찾을 수 없음"
            )
    })
    @GetMapping("/api/settlements/{settlementId}/payment-status")
    @ResponseBody
    public ResponseEntity<SettlementPaymentStatusResponse>
    getPaymentStatus(
            @PathVariable Long settlementId
    ){
        SettlementPaymentStatusResponse response =
                settlementPaymentStatusService.getPaymentStatus(
                        settlementId
                );
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "정산 마감",
            description = "정산 owner가 모든 납부의무 완료 후 정산을 마감합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "정산 마감 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "이미 마감된 정산"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "정산 owner가 아님"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "정산을 찾을 수 없음"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "정산 마감 조건을 만족하지 않음"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "정산 마감 처리 실패"
            )
    })
    @PostMapping("/api/settlements/{settlementId}/close")
    @ResponseBody
    public ResponseEntity<SettlementCloseResponse> closeSettlement(
            @AuthenticationPrincipal
            CustomUserDetails userDetails,

            @PathVariable Long settlementId
    ){
        SettlementCloseResponse response = settlementCloseService.close(
                settlementId,
                userDetails.getUserId()
        );

        return ResponseEntity.ok(response);
    }


}
