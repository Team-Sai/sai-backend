package org.teamsai.saibackend.domain.transaction.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.teamsai.saibackend.domain.transaction.dto.request.BankTransactionSearchCondition;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionDetailResponse;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionListItemResponse;
import org.teamsai.saibackend.domain.transaction.dto.response.PageResponse;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionQueryService;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;
import org.teamsai.saibackend.global.security.CustomUserDetails;

import java.time.LocalDate;

@Tag(name = "은행 거래 조회 API")
@RestController
@RequiredArgsConstructor
public class BankTransactionQueryController {

    private final BankTransactionQueryService bankTransactionQueryService;

    @Operation(summary = "연동계좌 거래 목록 조회/검색")
    @GetMapping("/api/linked-accounts/{linkedAccountId}/transactions")
    public ResponseEntity<PageResponse<BankTransactionListItemResponse>> getTransactions(
            @PathVariable Long linkedAccountId,
            @RequestParam(required = false) BankTransactionProcessingStatus processingStatus,
            @RequestParam(required = false) BankTransactionType transactionType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        BankTransactionSearchCondition condition = new BankTransactionSearchCondition(
                processingStatus, transactionType, keyword, fromDate, toDate, page, size
        );

        return ResponseEntity.ok(
                bankTransactionQueryService.getTransactions(userDetails.getUserId(), linkedAccountId, condition)
        );
    }

    @Operation(summary = "연동계좌 거래 상세 조회")
    @GetMapping("/api/linked-accounts/{linkedAccountId}/transactions/{bankTransactionId}")
    public ResponseEntity<BankTransactionDetailResponse> getTransactionDetail(
            @PathVariable Long linkedAccountId,
            @PathVariable Long bankTransactionId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                bankTransactionQueryService.getTransactionDetail(
                        userDetails.getUserId(), linkedAccountId, bankTransactionId
                )
        );
    }
}