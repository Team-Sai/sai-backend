package org.teamsai.saibackend.domain.matching.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.teamsai.saibackend.domain.matching.dto.request.MatchingReviewSearchCondition;
import org.teamsai.saibackend.domain.matching.dto.response.BankTransactionMatchingReviewResponse;
import org.teamsai.saibackend.domain.matching.service.BankTransactionMatchingReviewListQueryService;
import org.teamsai.saibackend.domain.matching.type.MatchingReviewChannel;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.transaction.dto.response.PageResponse;
import org.teamsai.saibackend.global.security.CustomUserDetails;

@Tag(
        name = "은행 거래 매칭 검토 조회 API",
        description = "확인이 필요한 입금 거래와 매칭 후보를 화면 범위별로 조회합니다."
)
@RestController
@RequiredArgsConstructor
public class BankTransactionMatchingReviewQueryController {

    private final BankTransactionMatchingReviewListQueryService queryService;

    @Operation(
            summary = "매칭 검토 목록 조회",
            description = "거래내역 또는 알림 검토 채널에 해당하는 NEEDS_CHECK 입금 거래를 페이지로 조회합니다."
    )
    @GetMapping("/api/matching-reviews")
    public ResponseEntity<PageResponse<BankTransactionMatchingReviewResponse>>
    getMatchingReviews(
            @RequestParam MatchingReviewChannel reviewChannel,
            @RequestParam(required = false) MatchingTargetType targetType,
            @RequestParam(required = false) Long aggregateId,
            @RequestParam(defaultValue = "0") long page,
            @RequestParam(defaultValue = "20") long size,
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        MatchingReviewSearchCondition condition =
                new MatchingReviewSearchCondition(
                        reviewChannel,
                        targetType,
                        aggregateId,
                        page,
                        size
                );

        return ResponseEntity.ok(
                queryService.getReviews(userDetails.getUserId(), condition)
        );
    }
}
