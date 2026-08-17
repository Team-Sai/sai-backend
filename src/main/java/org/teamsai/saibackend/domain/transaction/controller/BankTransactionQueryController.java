package org.teamsai.saibackend.domain.transaction.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.teamsai.saibackend.domain.matching.dto.response.BankTransactionMatchingReviewResponse;
import org.teamsai.saibackend.domain.matching.service.BankTransactionMatchingReviewQueryService;
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
    private final BankTransactionMatchingReviewQueryService
            matchingReviewQueryService;

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

    @Operation(
            summary = "은행 거래 매칭 후보 조회",
            description = "확인이 필요한 은행 거래와 저장된 정산·차용증 매칭 후보를 조회합니다. "
                    + "정산과 차용증 후보가 모두 있으면 알림 검토 대상으로, "
                    + "한 도메인의 후보만 있으면 거래내역 검토 대상으로 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "은행 거래 매칭 후보 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "연결 계좌에 대한 접근 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "연결 계좌 또는 은행 거래를 찾을 수 없음"
            )
    })
    @GetMapping(
            "/api/linked-accounts/{linkedAccountId}"
                    + "/transactions/{bankTransactionId}/match-candidates"
    )
    public ResponseEntity<BankTransactionMatchingReviewResponse>
    getMatchCandidates(
            @PathVariable Long linkedAccountId,
            @PathVariable Long bankTransactionId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                matchingReviewQueryService.getReview(
                        userDetails.getUserId(),
                        linkedAccountId,
                        bankTransactionId
                )
        );
    }
}
