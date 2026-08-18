package org.teamsai.saibackend.domain.transaction.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.transaction.dto.response.TransactionSyncAllResponse;
import org.teamsai.saibackend.domain.transaction.service.TransactionSyncFacade;
import org.teamsai.saibackend.global.security.CustomUserDetails;

@Tag(name = "거래 동기화 API", description = "사이은행 거래내역 동기화 및 자동매칭 실행")
@RestController
@RequiredArgsConstructor
public class TransactionSyncController {

    private final TransactionSyncFacade transactionSyncFacade;

    @Operation(summary = "연동계좌 거래 동기화")
    @PostMapping("/api/linked-accounts/{linkedAccountId}/sync")
    public ResponseEntity<AutoMatchingExecutionResult> sync(
            @PathVariable Long linkedAccountId,
            @RequestParam(required = false) MatchingTargetType targetType,
            @RequestParam(required = false) Long aggregateId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                transactionSyncFacade.syncAndMatch(
                        userDetails.getUserId(),
                        linkedAccountId,
                        targetType,
                        aggregateId
                )
        );
    }

    @Operation(summary = "전체 연동계좌 거래 동기화")
    @PostMapping("/api/transactions/sync")
    public ResponseEntity<TransactionSyncAllResponse> syncAll(
            @AuthenticationPrincipal
            CustomUserDetails userDetails
    ){
        return ResponseEntity.ok(
                transactionSyncFacade.syncAll(userDetails.getUserId())
        );
    }
}
