package org.teamsai.saibackend.domain.matching.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.teamsai.saibackend.domain.matching.dto.request.MatchingReviewApplyRequest;
import org.teamsai.saibackend.domain.matching.dto.response.MatchingReviewProcessResponse;
import org.teamsai.saibackend.domain.matching.service.BankTransactionMatchingReviewService;
import org.teamsai.saibackend.global.security.CustomUserDetails;

@Tag(
        name = "은행 거래 매칭 검토 API",
        description = "확인이 필요한 은행 거래의 납부 대상을 선택하거나 미매칭으로 처리하는 API"
)
@RestController
@RequiredArgsConstructor
public class BankTransactionMatchingReviewController {

    private final BankTransactionMatchingReviewService matchingReviewService;

    @Operation(
            summary = "은행 거래 매칭 후보 선택 반영",
            description = "확인이 필요한 은행 거래의 후보 하나를 선택하여 "
                    + "정산 납부 또는 차용증 상환으로 반영합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "매칭 검토 처리 완료"),
            @ApiResponse(responseCode = "400", description = "잘못된 후보 선택 요청"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "403", description = "연결 계좌에 대한 접근 권한 없음"),
            @ApiResponse(responseCode = "404", description = "은행 거래 또는 매칭 후보를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "확인 필요 상태가 아니거나 이미 처리된 거래")
    })
    @PostMapping(
            "/api/linked-accounts/{linkedAccountId}"
                    + "/transactions/{bankTransactionId}"
                    + "/matching-review/apply"
    )
    public ResponseEntity<MatchingReviewProcessResponse> applyCandidate(
            @PathVariable Long linkedAccountId,
            @PathVariable Long bankTransactionId,
            @Valid @RequestBody MatchingReviewApplyRequest request,
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                matchingReviewService.applyCandidate(
                        userDetails.getUserId(),
                        linkedAccountId,
                        bankTransactionId,
                        request.matchCandidateId()
                )
        );
    }

    @Operation(
            summary = "은행 거래 미매칭 처리",
            description = "확인이 필요한 은행 거래에서 어느 후보도 선택하지 않고 "
                    + "미매칭 거래로 처리합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "미매칭 처리 완료"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "403", description = "연결 계좌에 대한 접근 권한 없음"),
            @ApiResponse(responseCode = "404", description = "은행 거래를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "확인 필요 상태가 아니거나 이미 처리된 거래")
    })
    @PostMapping(
            "/api/linked-accounts/{linkedAccountId}"
                    + "/transactions/{bankTransactionId}"
                    + "/matching-review/reject"
    )
    public ResponseEntity<MatchingReviewProcessResponse> rejectCandidates(
            @PathVariable Long linkedAccountId,
            @PathVariable Long bankTransactionId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                matchingReviewService.rejectCandidates(
                        userDetails.getUserId(),
                        linkedAccountId,
                        bankTransactionId
                )
        );
    }
}
