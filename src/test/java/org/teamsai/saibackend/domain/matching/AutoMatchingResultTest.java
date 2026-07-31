package org.teamsai.saibackend.domain.matching;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.global.exception.DomainException;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AutoMatchingResult 단위 테스트")
class AutoMatchingResultTest {

    @Nested
    @DisplayName("생성 검증")
    class Construct {

        @Test
        @DisplayName("판정 유형이 null이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenDecisionTypeIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new AutoMatchingResult(
                            null,
                            List.of()
                    )
            );
        }

        @Test
        @DisplayName("매칭 후보 목록이 null이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenMatchedCandidatesIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new AutoMatchingResult(
                            AutoMatchingDecisionType.UNMATCHED,
                            null
                    )
            );
        }

        @Test
        @DisplayName("매칭 후보 목록을 방어적으로 복사한다")
        void copiesMatchedCandidatesDefensively() {
            MatchingCandidate candidate = new MatchingCandidate(
                    1L,
                    1L,
                    "HongGilDong",
                    new BigDecimal("10000")
            );
            List<MatchingCandidate> candidates = new ArrayList<>();
            candidates.add(candidate);

            AutoMatchingResult result = new AutoMatchingResult(
                    AutoMatchingDecisionType.MATCHABLE,
                    candidates
            );

            candidates.clear();

            assertThat(result.matchedCandidates()).containsExactly(candidate);
        }
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
