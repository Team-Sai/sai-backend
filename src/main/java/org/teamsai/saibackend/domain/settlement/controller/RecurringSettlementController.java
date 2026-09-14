package org.teamsai.saibackend.domain.settlement.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateRecurringSettlementRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.CreateRecurringSettlementResponse;
import org.teamsai.saibackend.domain.settlement.service.RecurringSettlementService;
import org.teamsai.saibackend.global.security.CustomUserDetails;

@Tag(
        name = "정기정산 API",
        description = "정기정산 설정 및 관리 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/settlements/recurring")
public class RecurringSettlementController {

    private final RecurringSettlementService recurringSettlementService;

    @Operation(
            summary = "정기정산 생성",
            description = "정기정산을 등록하고 최초 정산 회차를 생성합니다."
    )
    @PostMapping
    public ResponseEntity<CreateRecurringSettlementResponse>
    createRecurringSettlement(
            @AuthenticationPrincipal CustomUserDetails userDetails,

            @Valid
            @RequestBody CreateRecurringSettlementRequest request
    ) {

        CreateRecurringSettlementResponse response =
                recurringSettlementService.create(
                        userDetails.getUserId(),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
