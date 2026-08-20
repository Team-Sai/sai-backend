package org.teamsai.saibackend.domain.batch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.batch.service.WriteOffBatchService;
import org.teamsai.saibackend.domain.batch.service.WriteOffResult;
import org.teamsai.saibackend.domain.contractrepaymentschedule.mapper.RepaymentScheduleMapper;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.settlement.service.SettlementCloseService;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WriteOffBatchServiceTest {

    @Mock
    private PaymentObligationMapper paymentObligationMapper;

    @Mock
    private RepaymentScheduleMapper repaymentScheduleMapper;

    @Mock
    private SettlementCloseService settlementCloseService;

    @InjectMocks
    private WriteOffBatchService writeOffBatchService;

    private final LocalDate baseDate = LocalDate.of(2026, 8, 19);

    @Nested
    class WriteOffSettlementObligations {

        @Test
        void 상각_대상이_없으면_0_0을_반환하고_후속로직을_호출하지_않는다() {
            // given
            when(paymentObligationMapper.findWriteOffCandidateIds(any()))
                    .thenReturn(Collections.emptyList());

            // when
            WriteOffResult result = writeOffBatchService.writeOffSettlementObligations(baseDate);

            // then
            assertThat(result.obligationCount()).isZero();
            assertThat(result.closedSettlementCount()).isZero();
            verify(paymentObligationMapper, never()).writeOffBulk(anyList());
            verify(paymentObligationMapper, never()).findSettlementIdsByObligationIds(anyList());
            verifyNoInteractions(settlementCloseService);
        }

        @Test
        void 상각_대상이_있으면_실제_상각건수와_자동종결건수를_반환한다() {
            // given
            List<Long> candidateIds = List.of(1L, 2L, 3L);
            List<Long> settlementIds = List.of(100L, 200L);

            when(paymentObligationMapper.findWriteOffCandidateIds(any()))
                    .thenReturn(candidateIds);
            when(paymentObligationMapper.writeOffBulk(candidateIds)).thenReturn(3);
            when(paymentObligationMapper.findSettlementIdsByObligationIds(candidateIds))
                    .thenReturn(settlementIds);
            when(settlementCloseService.autoCloseIfAllResolved(100L)).thenReturn(true);
            when(settlementCloseService.autoCloseIfAllResolved(200L)).thenReturn(false);

            // when
            WriteOffResult result = writeOffBatchService.writeOffSettlementObligations(baseDate);

            // then
            assertThat(result.obligationCount()).isEqualTo(3);
            assertThat(result.closedSettlementCount()).isEqualTo(1);
            verify(settlementCloseService).autoCloseIfAllResolved(100L);
            verify(settlementCloseService).autoCloseIfAllResolved(200L);
        }

        @Test
        void 실제_상각건수가_0이면_정산_조회_자체를_스킵한다() {
            // given: 후보는 있었지만 동시성 등으로 실제 UPDATE된 row가 0건인 경우
            List<Long> candidateIds = List.of(1L, 2L);
            when(paymentObligationMapper.findWriteOffCandidateIds(any()))
                    .thenReturn(candidateIds);
            when(paymentObligationMapper.writeOffBulk(candidateIds)).thenReturn(0);

            // when
            WriteOffResult result = writeOffBatchService.writeOffSettlementObligations(baseDate);

            // then
            assertThat(result.obligationCount()).isZero();
            assertThat(result.closedSettlementCount()).isZero();
            verify(paymentObligationMapper, never()).findSettlementIdsByObligationIds(anyList());
            verifyNoInteractions(settlementCloseService);
        }

        @Test
        void 특정_정산의_자동종결이_실패해도_나머지_정산은_계속_처리된다() {
            // given
            List<Long> candidateIds = List.of(1L);
            List<Long> settlementIds = List.of(100L, 200L, 300L);

            when(paymentObligationMapper.findWriteOffCandidateIds(any()))
                    .thenReturn(candidateIds);
            when(paymentObligationMapper.writeOffBulk(candidateIds)).thenReturn(1);
            when(paymentObligationMapper.findSettlementIdsByObligationIds(candidateIds))
                    .thenReturn(settlementIds);

            when(settlementCloseService.autoCloseIfAllResolved(100L)).thenReturn(true);
            when(settlementCloseService.autoCloseIfAllResolved(200L))
                    .thenThrow(new RuntimeException("동시성 충돌"));
            when(settlementCloseService.autoCloseIfAllResolved(300L)).thenReturn(true);

            // when
            WriteOffResult result = writeOffBatchService.writeOffSettlementObligations(baseDate);

            // then: 200L에서 예외가 나도 300L까지 처리되어 closedCount는 2
            assertThat(result.closedSettlementCount()).isEqualTo(2);
            verify(settlementCloseService).autoCloseIfAllResolved(100L);
            verify(settlementCloseService).autoCloseIfAllResolved(200L);
            verify(settlementCloseService).autoCloseIfAllResolved(300L);
        }
    }

    @Nested
    class WriteOffOneBatch {

        @Test
        void 건수500_초과시_500건_단위로_청크를_나누어_호출한다() {
            // given: 1200건 -> 500 / 500 / 200 세 번 호출되어야 함
            List<Long> ids = LongStream.rangeClosed(1, 1200).boxed().collect(Collectors.toList());

            when(paymentObligationMapper.writeOffBulk(anyList()))
                    .thenReturn(500, 500, 200); // 호출 순서대로 500 -> 500 -> 200 반환

            // when
            int total = writeOffBatchService.writeOffOneBatch(ids);

            // then
            assertThat(total).isEqualTo(1200);

            ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
            verify(paymentObligationMapper, times(3)).writeOffBulk(captor.capture());

            List<List<Long>> chunks = captor.getAllValues();
            assertThat(chunks).hasSize(3);
            assertThat(chunks.get(0)).hasSize(500);
            assertThat(chunks.get(1)).hasSize(500);
            assertThat(chunks.get(2)).hasSize(200);
        }

        @Test
        void 정확히_500건이면_한_번만_호출된다() {
            List<Long> ids = LongStream.rangeClosed(1, 500).boxed().collect(Collectors.toList());
            when(paymentObligationMapper.writeOffBulk(anyList())).thenReturn(500);

            int total = writeOffBatchService.writeOffOneBatch(ids);

            assertThat(total).isEqualTo(500);
            verify(paymentObligationMapper, times(1)).writeOffBulk(anyList());
        }
    }

    @Nested
    class WriteOffRepaymentSchedules {

        @Test
        void 대상이_없으면_0을_반환한다() {
            when(repaymentScheduleMapper.findWriteOffCandidateIds(any()))
                    .thenReturn(Collections.emptyList());

            int result = writeOffBatchService.writeOffRepaymentSchedules(baseDate);

            assertThat(result).isZero();
            verify(repaymentScheduleMapper, never()).writeOffBulk(anyList());
        }

        @Test
        void 대상이_있으면_상각건수를_반환한다() {
            List<Long> candidateIds = List.of(10L, 20L, 30L);
            when(repaymentScheduleMapper.findWriteOffCandidateIds(any()))
                    .thenReturn(candidateIds);
            when(repaymentScheduleMapper.writeOffBulk(candidateIds)).thenReturn(3);

            int result = writeOffBatchService.writeOffRepaymentSchedules(baseDate);

            assertThat(result).isEqualTo(3);
        }

        @Test
        void cutoffDate가_baseDate에서_30일_전으로_계산되어_전달된다() {
            when(repaymentScheduleMapper.findWriteOffCandidateIds(any()))
                    .thenReturn(Collections.emptyList());

            writeOffBatchService.writeOffRepaymentSchedules(baseDate);

            verify(repaymentScheduleMapper).findWriteOffCandidateIds(baseDate.minusDays(30));
        }
    }
}