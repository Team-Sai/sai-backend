package org.teamsai.saibackend.domain.matching;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.service.MatchingTransaction;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingTransactionType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MatchingTransaction 단위 테스트")
class MatchingTransactionTest {

    @Nested
    @DisplayName("생성 검증")
    class Construct {

        @Test
        @DisplayName("거래 ID가 null이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenTransactionIdIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new MatchingTransaction(
                            null,
                            AutoMatchingTransactionType.DEPOSIT,
                            new BigDecimal("10000"),
                            "HongGilDong",
                            transactionAt()
                    )
            );
        }

        @Test
        @DisplayName("거래 유형이 null이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenTransactionTypeIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new MatchingTransaction(
                            1L,
                            null,
                            new BigDecimal("10000"),
                            "HongGilDong",
                            transactionAt()
                    )
            );
        }

        @Test
        @DisplayName("금액이 null이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenAmountIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new MatchingTransaction(
                            1L,
                            AutoMatchingTransactionType.DEPOSIT,
                            null,
                            "HongGilDong",
                            transactionAt()
                    )
            );
        }

        @Test
        @DisplayName("금액이 0 이하이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenAmountIsNotPositive() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new MatchingTransaction(
                            1L,
                            AutoMatchingTransactionType.DEPOSIT,
                            BigDecimal.ZERO,
                            "HongGilDong",
                            transactionAt()
                    )
            );
        }

        @Test
        @DisplayName("거래 상대명이 null이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenCounterpartyNameIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new MatchingTransaction(
                            1L,
                            AutoMatchingTransactionType.DEPOSIT,
                            new BigDecimal("10000"),
                            null,
                            transactionAt()
                    )
            );
        }

        @Test
        @DisplayName("거래 상대명이 빈 값이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenCounterpartyNameIsBlank() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new MatchingTransaction(
                            1L,
                            AutoMatchingTransactionType.DEPOSIT,
                            new BigDecimal("10000"),
                            " ",
                            transactionAt()
                    )
            );
        }

        @Test
        @DisplayName("거래 시간이 null이면 잘못된 매칭 요청 예외가 발생한다")
        void failsWhenTransactionAtIsNull() {
            assertInvalidMatchingRequestThrownBy(
                    () -> new MatchingTransaction(
                            1L,
                            AutoMatchingTransactionType.DEPOSIT,
                            new BigDecimal("10000"),
                            "HongGilDong",
                            null
                    )
            );
        }
    }

    private LocalDateTime transactionAt() {
        return LocalDateTime.of(2026, 8, 5, 10, 0);
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
