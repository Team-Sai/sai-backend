package org.teamsai.saibackend.domain.contractdashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.contractdashboard.controller.DashboardController;
import org.teamsai.saibackend.domain.contractdashboard.dto.response.DashboardResponse;
import org.teamsai.saibackend.domain.contractdashboard.service.DashboardService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardController dashboardController;

    @Test
    @DisplayName("전달받은 파라미터를 그대로 Service에 넘기고 결과를 반환한다")
    void getDashboard_delegatesToServiceWithParams() {
        Long userId = 1L;
        String keyword = "생활비";
        String roleFilter = "LENT";
        String statusFilter = "ONGOING";
        String sortType = "AMOUNT_DESC";
        int page = 2;

        DashboardResponse expected = DashboardResponse.builder()
                .currentPage(2)
                .totalPages(3)
                .totalCount(12L)
                .pageSize(5)
                .build();

        when(dashboardService.getDashboard(userId, keyword, roleFilter, statusFilter, sortType, page))
                .thenReturn(expected);

        DashboardResponse actual = dashboardController.getDashboard(userId, keyword, roleFilter, statusFilter, sortType, page);

        assertThat(actual).isEqualTo(expected);
        verify(dashboardService).getDashboard(userId, keyword, roleFilter, statusFilter, sortType, page);
    }

    @Test
    @DisplayName("page 기본값은 1이다")
    void getDashboard_defaultPageIsOne() {
        Long userId = 1L;

        when(dashboardService.getDashboard(userId, null, null, null, null, 1))
                .thenReturn(DashboardResponse.builder().currentPage(1).build());

        DashboardResponse actual = dashboardController.getDashboard(userId, null, null, null, null, 1);

        assertThat(actual.getCurrentPage()).isEqualTo(1);
    }
}
