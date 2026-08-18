package org.teamsai.saibackend.domain.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.contract.mapper.RiskCheckMapper;
import org.teamsai.saibackend.domain.contract.service.RiskCheckService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RiskCheckService 단위 테스트")
class RiskCheckServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private RiskCheckMapper riskCheckMapper;

    @InjectMocks
    private RiskCheckService riskCheckService;

    @Test
    @DisplayName("매퍼가 반환한 이전 차용금 합계를 그대로 반환한다")
    void getPreviousTotalAmountReturnsSumFromMapper() {
        given(riskCheckMapper.sumCompletedPrincipalByCreditor(USER_ID)).willReturn(30_000_000L);

        Long result = riskCheckService.getPreviousTotalAmount(USER_ID);

        assertThat(result).isEqualTo(30_000_000L);
        verify(riskCheckMapper).sumCompletedPrincipalByCreditor(USER_ID);
    }

    @Test
    @DisplayName("이전에 완료된 가족 간 계약이 없으면 0을 반환한다")
    void getPreviousTotalAmountReturnsZeroWhenNoCompletedFamilyContracts() {
        given(riskCheckMapper.sumCompletedPrincipalByCreditor(USER_ID)).willReturn(0L);

        Long result = riskCheckService.getPreviousTotalAmount(USER_ID);

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("조회 대상 사용자 ID를 그대로 매퍼에 전달한다")
    void getPreviousTotalAmountPassesUserIdToMapper() {
        Long otherUserId = 42L;
        given(riskCheckMapper.sumCompletedPrincipalByCreditor(otherUserId)).willReturn(5_000_000L);

        riskCheckService.getPreviousTotalAmount(otherUserId);

        verify(riskCheckMapper).sumCompletedPrincipalByCreditor(otherUserId);
    }
}
