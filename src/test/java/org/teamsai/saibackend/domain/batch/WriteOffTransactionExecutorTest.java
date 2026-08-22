package org.teamsai.saibackend.domain.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.batch.service.WriteOffTransactionExecutor;
import org.teamsai.saibackend.domain.contractrepaymentschedule.mapper.RepaymentScheduleMapper;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WriteOffTransactionExecutorTest {

    @Mock
    private PaymentObligationMapper paymentObligationMapper;
    @Mock
    private RepaymentScheduleMapper repaymentScheduleMapper;
    @InjectMocks
    private WriteOffTransactionExecutor writeOffTransactionExecutor;

    @Test
    void 건수500_초과시_500건_단위로_청크를_나누어_호출한다() {
        // given: 1200건 -> 500 / 500 / 200 세 번 호출되어야 함
        List<Long> ids = LongStream.rangeClosed(1, 1200).boxed().collect(Collectors.toList());
        when(paymentObligationMapper.writeOffBulk(anyList()))
                .thenReturn(500, 500, 200); // 호출 순서대로 500 -> 500 -> 200 반환
        // when
        int total = writeOffTransactionExecutor.writeOffOneBatch(ids);
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
        int total = writeOffTransactionExecutor.writeOffOneBatch(ids);
        assertThat(total).isEqualTo(500);
        verify(paymentObligationMapper, times(1)).writeOffBulk(anyList());
    }

    @Test
    void 상환스케줄_500건_초과시_청크로_나누어_호출된다() {
        List<Long> ids = LongStream.rangeClosed(1, 700).boxed().collect(Collectors.toList());
        when(repaymentScheduleMapper.writeOffBulk(anyList())).thenReturn(500, 200);
        int total = writeOffTransactionExecutor.writeOffSchedulesInNewTransaction(ids);
        assertThat(total).isEqualTo(700);
        verify(repaymentScheduleMapper, times(2)).writeOffBulk(anyList());
    }
}