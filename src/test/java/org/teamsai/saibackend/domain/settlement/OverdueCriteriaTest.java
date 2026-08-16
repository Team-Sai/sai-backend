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
            LocalDate dueDate = LocalDate.of(2026, 1, 10);
            SettlementDTO settlement = SettlementDTO.builder()
                    .settlementType(SettlementType.SHARED)
                    .settlementStatus(SettlementStatus.IN_PROGRESS)
                    .dueDate(dueDate)
                    .build();

            boolean result = sut.isOverdue(settlement, LocalDate.of(2026, 1, 11), dueDate);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("dueDate와 baseDate가 같으면 아직 연체가 아니다")
        void notOverdueWhenDueDateEqualsBaseDate() {
            LocalDate dueDate = LocalDate.of(2026, 1, 10);
            SettlementDTO settlement = SettlementDTO.builder()
                    .settlementType(SettlementType.SHARED)
                    .settlementStatus(SettlementStatus.IN_PROGRESS)
                    .dueDate(dueDate)
                    .build();

            boolean result = sut.isOverdue(settlement, LocalDate.of(2026, 1, 10), dueDate);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("dueDate가 baseDate보다 이후면 연체가 아니다")
        void notOverdueWhenDueDateAfterBaseDate() {
            LocalDate dueDate = LocalDate.of(2026, 1, 10);
            SettlementDTO settlement = SettlementDTO.builder()
                    .settlementType(SettlementType.SHARED)
                    .settlementStatus(SettlementStatus.IN_PROGRESS)
                    .dueDate(dueDate)
                    .build();

            boolean result = sut.isOverdue(settlement, LocalDate.of(2026, 1, 9), dueDate);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("RECURRING 타입 - cycleDate 기준")
    class RecurringType {

        @Test
        @DisplayName("cycleDate가 baseDate보다 이전이면 연체다")
        void overdueWhenCycleDateBeforeBaseDate() {
            LocalDate cycleDate = LocalDate.of(2026, 1, 31);
            SettlementDTO settlement = SettlementDTO.builder()
                    .settlementType(SettlementType.RECURRING)
                    .settlementStatus(SettlementStatus.IN_PROGRESS)
                    .cycleDate(cycleDate)
                    .build();

            boolean result = sut.isOverdue(settlement, LocalDate.of(2026, 2, 1), cycleDate);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("SHARED와 달리 dueDate가 null이어도 cycleDate로 정상 판정한다")
        void ignoresNullDueDateForRecurringType() {
            LocalDate cycleDate = LocalDate.of(2026, 1, 31);
            SettlementDTO settlement = SettlementDTO.builder()
                    .settlementType(SettlementType.RECURRING)
                    .settlementStatus(SettlementStatus.IN_PROGRESS)
                    .dueDate(null)
                    .cycleDate(cycleDate)
                    .build();

            boolean result = sut.isOverdue(settlement, LocalDate.of(2026, 2, 1), cycleDate);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("settlementStatus 필터링")
    class SettlementStatusFiltering {

        @Test
        @DisplayName("CLOSED 상태면 기한이 지났어도 연체가 아니다")
        void notOverdueWhenClosed() {
            LocalDate dueDate = LocalDate.of(2020, 1, 1);
            SettlementDTO settlement = SettlementDTO.builder()
                    .settlementType(SettlementType.SHARED)
                    .settlementStatus(SettlementStatus.CLOSED)
                    .dueDate(dueDate)
                    .build();

            boolean result = sut.isOverdue(settlement, LocalDate.of(2026, 1, 1), dueDate);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("resolveReferenceDate")
    class ResolveReferenceDate {

        @Test
        @DisplayName("SHARED 타입이면 dueDate를 반환한다")
        void returnsDueDateForShared() {
            LocalDate dueDate = LocalDate.of(2026, 1, 10);
            SettlementDTO settlement = SettlementDTO.builder()
                    .settlementType(SettlementType.SHARED)
                    .dueDate(dueDate)
                    .build();

            LocalDate result = sut.resolveReferenceDate(settlement);

            assertThat(result).isEqualTo(dueDate);
        }

        @Test
        @DisplayName("RECURRING 타입이면 cycleDate를 반환한다")
        void returnsCycleDateForRecurring() {
            LocalDate cycleDate = LocalDate.of(2026, 1, 31);
            SettlementDTO settlement = SettlementDTO.builder()
                    .settlementType(SettlementType.RECURRING)
                    .cycleDate(cycleDate)
                    .build();

            LocalDate result = sut.resolveReferenceDate(settlement);

            assertThat(result).isEqualTo(cycleDate);
        }
    }
}