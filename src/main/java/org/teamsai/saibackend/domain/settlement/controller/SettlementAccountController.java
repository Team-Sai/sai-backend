package org.teamsai.saibackend.domain.settlement.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.teamsai.saibackend.domain.settlement.dto.request.SelectSettlementAccountRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementAccountResponse;
import org.teamsai.saibackend.domain.settlement.service.SettlementAccountService;

@Tag(
        name = "정산 계좌 API",
        description = "정산 수취 계좌 설정 및 조회 API"
)
@Controller
@RequiredArgsConstructor
public class SettlementAccountController {

    private final SettlementAccountService
            settlementAccountService;

    @Operation(
            summary = "정산 수취 계좌 설정",
            description = """
                    사용자가 연동한 계좌 중 하나를
                    해당 정산의 수취 계좌로 설정합니다.
                    기존 수취 계좌가 있으면 새 계좌로 변경합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "정산 수취 계좌 설정 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "사용할 수 없는 연동 계좌"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "정산 계좌 설정 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "정산을 찾을 수 없음"
            )
    })
    @ResponseBody
    @PutMapping(
            "/api/settlements/{settlementId}/account"
    )
    public ResponseEntity<SettlementAccountResponse>
    selectAccount(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(
                    expression = "userId"
            )
            Long userId,

            @PathVariable("settlementId")
            Long settlementId,

            @Valid
            @RequestBody
            SelectSettlementAccountRequest request
    ) {
        SettlementAccountResponse response =
                settlementAccountService
                        .selectAccount(
                                userId,
                                settlementId,
                                request.getLinkedAccountId()
                        );

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "현재 정산 수취 계좌 조회"
    )
    @ResponseBody
    @GetMapping(
            "/api/settlements/{settlementId}/account"
    )
    public ResponseEntity<SettlementAccountResponse>
    findCurrentAccount(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(
                    expression = "userId"
            )
            Long userId,

            @PathVariable("settlementId")
            Long settlementId
    ) {
        SettlementAccountResponse response =
                settlementAccountService
                        .findCurrentAccount(
                                userId,
                                settlementId
                        );

        return ResponseEntity.ok(response);
    }
}