package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.service.OverdueCriteria;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OverdueCriteriaTest {

    private final OverdueCriteria sut = new OverdueCriteria();

    @Nested
    @DisplayName("SHARED 타입 - dueDate 기준")
    class SharedType {

        @Test
        @DisplayName("dueDate가 baseDate보다 이전이면 연체다")
        void overdueWhenDueDateBeforeBaseDate() {
            SettlementDTO settlement = SettlementDTO.builder()
                    .settlementType(SettlementType.SHARED)
                    .settlementStatus(SettlementStatus.IN_PROGRESS)
                    .dueDate(LocalDate.of(2026, 1, 10))
                    .build();

            boolean result = sut.isOverdue(settlement, LocalDate.of(2026, 1, 11));

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("dueDate와 baseDate가 같으면 아직 연체가 아니다")
        void notOverdueWhenDueDateEqualsBaseDate() {
            SettlementDTO settlement = SettlementDTO.builder()
                    .settlementType(SettlementType.SHARED)
                    .settlementStatus(SettlementStatus.IN_PROGRESS)
                    .dueDate(LocalDate.of(2026, 1, 10))
                    .build();

            boolean result = sut.isOverdue(settlement, LocalDate.of(2026, 1, 10));

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("dueDate가 baseDate보다 이후면 연체가 아니다")
        void notOverdueWhenDueDateAfterBaseDate() {
            SettlementDTO settlement = SettlementDTO.builder()
                    .settlementType(SettlementType.SHARED)
                    .settlementStatus(SettlementStatus.IN_PROGRESS)
                    .dueDate(LocalDate.of(2026, 1, 10))
                    .build();

            boolean result = sut.isOverdue(settlement, LocalDate.of(2026, 1, 9));

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("RECURRING 타입 - cycleDate 기준")
    class RecurringType {

        @Test
        @DisplayName("cycleDate가 baseDate보다 이전이면 연체다")
        void overdueWhenCycleDateBeforeBaseDate() {
            SettlementDTO settlement = SettlementDTO.builder()
                    .settlementType(SettlementType.RECURRING)
                    .settlementStatus(SettlementStatus.IN_PROGRESS)
                    .cycleDate(LocalDate.of(2026, 1, 31))
                    .build();

            boolean result = sut.isOverdue(settlement, LocalDate.of(2026, 2, 1));

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("SHARED와 달리 dueDate가 null이어도 cycleDate로 정상 판정한다")
        void ignoresNullDueDateForRecurringType() {
            SettlementDTO settlement = SettlementDTO.builder()
                    .settlementType(SettlementType.RECURRING)
                    .settlementStatus(SettlementStatus.IN_PROGRESS)
                    .dueDate(null)
                    .cycleDate(LocalDate.of(2026, 1, 31))
                    .build();

            boolean result = sut.isOverdue(settlement, LocalDate.of(2026, 2, 1));

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("settlementStatus 필터링")
    class SettlementStatusFiltering {

        @Test
        @DisplayName("CLOSED 상태면 기한이 지났어도 연체가 아니다")
        void notOverdueWhenClosed() {
            SettlementDTO settlement = SettlementDTO.builder()
                    .settlementType(SettlementType.SHARED)
                    .settlementStatus(SettlementStatus.CLOSED)
                    .dueDate(LocalDate.of(2020, 1, 1))
                    .build();

            boolean result = sut.isOverdue(settlement, LocalDate.of(2026, 1, 1));

            assertThat(result).isFalse();
        }
    }
}