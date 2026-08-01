package org.teamsai.saibackend.domain.matching;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingResult;
import org.teamsai.saibackend.domain.matching.model.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingDecisionType;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AutoMatchingResult 단위 테스트")
class AutoMatchingResultTest {

    @Nested
    @DisplayName("생성 검증")
    class Construct {

        @Test
        @DisplayName("매칭 후보 목록이 null이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenMatchedCandidatesIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new AutoMatchingResult(null)
            );
        }

        @Test
        @DisplayName("매칭 후보가 없으면 미매칭으로 판정한다")
        void emptyCandidatesIsUnmatched() {
            AutoMatchingResult result = new AutoMatchingResult(List.of());

            assertThat(result.decisionType())
                    .isEqualTo(AutoMatchingDecisionType.UNMATCHED);
            assertThat(result.matchedCandidates()).isEmpty();
        }

        @Test
        @DisplayName("매칭 후보가 하나이면 매칭 가능으로 판정한다")
        void oneCandidateIsMatchable() {
            MatchingCandidate candidate = createCandidate(1L);

            AutoMatchingResult result =
                    new AutoMatchingResult(List.of(candidate));

            assertThat(result.decisionType())
                    .isEqualTo(AutoMatchingDecisionType.MATCHABLE);
            assertThat(result.matchedCandidates()).containsExactly(candidate);
        }

        @Test
        @DisplayName("매칭 후보가 여러 개이면 확인 필요로 판정한다")
        void multipleCandidatesNeedsCheck() {
            MatchingCandidate first = createCandidate(1L);
            MatchingCandidate second = createCandidate(2L);

            AutoMatchingResult result =
                    new AutoMatchingResult(List.of(first, second));

            assertThat(result.decisionType())
                    .isEqualTo(AutoMatchingDecisionType.NEEDS_CHECK);
            assertThat(result.matchedCandidates())
                    .containsExactly(first, second);
        }

        @Test
        @DisplayName("매칭 후보 목록을 방어적으로 복사한다")
        void copiesMatchedCandidatesDefensively() {
            MatchingCandidate candidate = createCandidate(1L);
            List<MatchingCandidate> candidates = new ArrayList<>();
            candidates.add(candidate);

            AutoMatchingResult result = new AutoMatchingResult(candidates);

            candidates.clear();

            assertThat(result.matchedCandidates()).containsExactly(candidate);
        }
    }

    private MatchingCandidate createCandidate(Long obligationId) {
        return new MatchingCandidate(
                MatchingTargetType.SETTLEMENT,
                obligationId,
                obligationId,
                "HongGilDong",
                new BigDecimal("10000")
        );
    }

    private void assertInvalidMatchingRequestThrownBy(
            ThrowingCallable callable
    ) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        DomainException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        MatchingErrorCode.INVALID_MATCHING_REQUEST
                                )
                );
    }
}
