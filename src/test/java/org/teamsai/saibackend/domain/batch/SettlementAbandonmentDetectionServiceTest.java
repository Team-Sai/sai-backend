package org.teamsai.saibackend.domain.batch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.batch.common.notification.SlackNotifier;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementAbandonmentAlertMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.service.OverdueCriteria;
import org.teamsai.saibackend.domain.settlement.service.SettlementAbandonmentDetectionService;
import org.teamsai.saibackend.domain.settlement.service.SettlementAbandonmentResult;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementAbandonmentDetectionServiceTest {

    @Mock
    private OverdueCriteria overdueCriteria;
    @Mock
    private SettlementMapper settlementMapper;
    @Mock
    private SettlementAbandonmentAlertMapper alertMapper;
    @Mock
    private SlackNotifier slackNotifier;

    @InjectMocks
    private SettlementAbandonmentDetectionService service;

    private final LocalDate baseDate = LocalDate.of(2026, 8, 19);

    private SettlementDTO settlement(Long id, String title) {
        SettlementDTO dto = mock(SettlementDTO.class);
        lenient().when(dto.getSettlementId()).thenReturn(id);
        lenient().when(dto.getTitle()).thenReturn(title);
        return dto;
    }

    @Nested
    class NoTargets {

        @Test
        void 대상_정산이_없으면_0건을_반환한다() {
            when(settlementMapper.countInProgressSettlements()).thenReturn(0);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(Collections.emptyList());

            SettlementAbandonmentResult result = service.detectAbandoned(baseDate);

            assertThat(result.detectedCount()).isZero();
            verifyNoInteractions(alertMapper, slackNotifier);
        }
    }

    @Nested
    class AbandonmentJudgement {

        @Test
        void referenceDate가_null이면_방치로_판단하지_않는다() {
            SettlementDTO s = settlement(1L, "정산A");
            when(settlementMapper.countInProgressSettlements()).thenReturn(1);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(List.of(s));
            when(overdueCriteria.resolveReferenceDate(s)).thenReturn(null);

            SettlementAbandonmentResult result = service.detectAbandoned(baseDate);

            assertThat(result.detectedCount()).isZero();
            verifyNoInteractions(alertMapper, slackNotifier);
        }

        @Test
        void 기준일로부터_3일_경과전이면_방치로_판단하지_않는다() {
            SettlementDTO s = settlement(1L, "정산A");
            LocalDate referenceDate = baseDate.minusDays(2); // 2일 경과, 3일 미만
            when(settlementMapper.countInProgressSettlements()).thenReturn(1);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(List.of(s));
            when(overdueCriteria.resolveReferenceDate(s)).thenReturn(referenceDate);

            SettlementAbandonmentResult result = service.detectAbandoned(baseDate);

            assertThat(result.detectedCount()).isZero();
            verify(alertMapper, never()).insertIfAbsent(any(), any());
            verifyNoInteractions(slackNotifier);
        }

        @Test
        void 정확히_3일_경과한_경계값은_방치로_판단한다() {
            SettlementDTO s = settlement(1L, "정산A");
            LocalDate referenceDate = baseDate.minusDays(3); // 정확히 3일 경과
            when(settlementMapper.countInProgressSettlements()).thenReturn(1);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(List.of(s));
            when(overdueCriteria.resolveReferenceDate(s)).thenReturn(referenceDate);
            when(alertMapper.existsBySettlementIdAndReferenceDate(1L, referenceDate)).thenReturn(false);

            SettlementAbandonmentResult result = service.detectAbandoned(baseDate);

            assertThat(result.detectedCount()).isEqualTo(1);
            verify(alertMapper).insertIfAbsent(1L, referenceDate);
            verify(slackNotifier).send(any());
        }

        @Test
        void 이미_알림이_존재하면_중복_삽입도_재알림도_하지_않는다() {
            SettlementDTO s = settlement(1L, "정산A");
            LocalDate referenceDate = baseDate.minusDays(5);
            when(settlementMapper.countInProgressSettlements()).thenReturn(1);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(List.of(s));
            when(overdueCriteria.resolveReferenceDate(s)).thenReturn(referenceDate);
            when(alertMapper.existsBySettlementIdAndReferenceDate(1L, referenceDate)).thenReturn(true);

            SettlementAbandonmentResult result = service.detectAbandoned(baseDate);

            assertThat(result.detectedCount()).isZero();
            verify(alertMapper, never()).insertIfAbsent(any(), any());
            verifyNoInteractions(slackNotifier);
        }
    }

    @Nested
    class Pagination {

        @Test
        void 페이지가_가득차면_다음_페이지를_계속_조회한다() {
            List<SettlementDTO> fullPage = Collections.nCopies(100, settlement(1L, "정산")).stream()
                    .map(x -> settlement(1L, "정산")) // 매 원소 별도 mock 필요없이 동일 stub이면 충분
                    .toList();

            when(settlementMapper.countInProgressSettlements()).thenReturn(150);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(fullPage);
            when(settlementMapper.findInProgressSettlements(100, 100)).thenReturn(Collections.emptyList());
            // referenceDate를 null로 둬서 판정 로직은 통과, 페이지네이션 흐름만 검증
            when(overdueCriteria.resolveReferenceDate(any())).thenReturn(null);

            service.detectAbandoned(baseDate);

            verify(settlementMapper).findInProgressSettlements(0, 100);
            verify(settlementMapper).findInProgressSettlements(100, 100);
        }

        @Test
        void 마지막_페이지가_PAGE_SIZE보다_작으면_추가조회하지_않는다() {
            SettlementDTO s = settlement(1L, "정산A");
            when(settlementMapper.countInProgressSettlements()).thenReturn(1);
            when(settlementMapper.findInProgressSettlements(0, 100)).thenReturn(List.of(s));
            when(overdueCriteria.resolveReferenceDate(s)).thenReturn(null);

            service.detectAbandoned(baseDate);

            verify(settlementMapper, times(1)).findInProgressSettlements(anyInt(), anyInt());
        }
    }

    @Nested
    class MultipleSettlementsInOnePage {

        @Test
        void 여러건_중_방치건만_선별해서_카운트한다() {
            SettlementDTO abandoned = settlement(1L, "방치됨");
            SettlementDTO notAbandoned = settlement(2L, "정상");
            SettlementDTO nullRef = settlement(3L, "기준일없음");

            when(settlementMapper.countInProgressSettlements()).thenReturn(3);
            when(settlementMapper.findInProgressSettlements(0, 100))
                    .thenReturn(List.of(abandoned, notAbandoned, nullRef));

            when(overdueCriteria.resolveReferenceDate(abandoned)).thenReturn(baseDate.minusDays(10));
            when(overdueCriteria.resolveReferenceDate(notAbandoned)).thenReturn(baseDate.minusDays(1));
            when(overdueCriteria.resolveReferenceDate(nullRef)).thenReturn(null);

            when(alertMapper.existsBySettlementIdAndReferenceDate(eq(1L), any())).thenReturn(false);

            SettlementAbandonmentResult result = service.detectAbandoned(baseDate);

            assertThat(result.detectedCount()).isEqualTo(1);
            verify(alertMapper).insertIfAbsent(eq(1L), any());
            verify(alertMapper, never()).insertIfAbsent(eq(2L), any());
            verify(alertMapper, never()).insertIfAbsent(eq(3L), any());
            verify(slackNotifier, times(1)).send(any());
        }
    }
}
