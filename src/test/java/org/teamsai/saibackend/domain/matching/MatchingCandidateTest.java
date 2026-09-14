package org.teamsai.saibackend.domain.matching;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.service.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MatchingCandidate 단위 테스트")
class MatchingCandidateTest {

    @Nested
    @DisplayName("생성 검증")
    class Construct {

        @Test
        @DisplayName("매칭 대상 유형이 null이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenTargetTypeIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new MatchingCandidate(
                            null,
                            1L,
                            1L,
                            "HongGilDong",
                            new BigDecimal("10000")
                    )
            );
        }

        @Test
        @DisplayName("납부의무 ID가 null이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenObligationIdIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new MatchingCandidate(
                            MatchingTargetType.SETTLEMENT,
                            null,
                            1L,
                            "HongGilDong",
                            new BigDecimal("10000")
                    )
            );
        }

        @Test
        @DisplayName("참여자 ID가 null이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenParticipantIdIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new MatchingCandidate(
                            MatchingTargetType.SETTLEMENT,
                            1L,
                            null,
                            "HongGilDong",
                            new BigDecimal("10000")
                    )
            );
        }

        @Test
        @DisplayName("참여자명이 null이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenParticipantNameIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new MatchingCandidate(
                            MatchingTargetType.SETTLEMENT,
                            1L,
                            1L,
                            null,
                            new BigDecimal("10000")
                    )
            );
        }

        @Test
        @DisplayName("참여자명이 빈 값이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenParticipantNameIsBlank() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new MatchingCandidate(
                            MatchingTargetType.SETTLEMENT,
                            1L,
                            1L,
                            " ",
                            new BigDecimal("10000")
                    )
            );
        }

        @Test
        @DisplayName("남은 금액이 null이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenRemainingAmountIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new MatchingCandidate(
                            MatchingTargetType.SETTLEMENT,
                            1L,
                            1L,
                            "HongGilDong",
                            null
                    )
            );
        }

        @Test
        @DisplayName("남은 금액이 0 이하이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenRemainingAmountIsNotPositive() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new MatchingCandidate(
                            MatchingTargetType.SETTLEMENT,
                            1L,
                            1L,
                            "HongGilDong",
                            BigDecimal.ZERO
                    )
            );
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
