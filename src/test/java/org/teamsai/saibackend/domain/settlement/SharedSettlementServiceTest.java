package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSharedSettlementRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.CreateSharedSettlementResponse;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.service.SharedSettlementService;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementType;
import org.teamsai.saibackend.domain.settlement.type.SplitType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharedSettlementServiceTest {

    @Mock
    private SettlementMapper settlementMapper;

    @InjectMocks
    private SharedSettlementService sharedSettlementService;

    @Test
    @DisplayName("공동정산 생성 시 로그인 사용자를 소유자로 지정하고 정산 ID를 반환한다")
    void createSharedSettlementSuccess() {

        Long ownerId = 1L;

        CreateSharedSettlementRequest request =
                CreateSharedSettlementRequest.builder()
                        .settlementCategory("여행")
                        .title("제주도 여행비 정산")
                        .splitType(SplitType.EQUAL)
                        .dueDate(LocalDate.now().plusDays(7))
                        .build();


        when(settlementMapper.insertSettlement(any(SettlementDTO.class)))
                .thenAnswer(invocation -> {
                    SettlementDTO settlement =
                            invocation.getArgument(0);

                    settlement.setSettlementId(15L);

                    return 1;
                });


        CreateSharedSettlementResponse response =
                sharedSettlementService.create(ownerId, request);

        assertThat(response.getSettlementId()).isEqualTo(15L);
        assertThat(response.getSettlementType())
                .isEqualTo(SettlementType.SHARED);
        assertThat(response.getSettlementStatus())
                .isEqualTo(SettlementStatus.IN_PROGRESS);
        assertThat(response.getTitle())
                .isEqualTo("제주도 여행비 정산");
        assertThat(response.getCreatedAt()).isNotNull();

        ArgumentCaptor<SettlementDTO> captor =
                ArgumentCaptor.forClass(SettlementDTO.class);

        verify(settlementMapper)
                .insertSettlement(captor.capture());

        SettlementDTO savedSettlement = captor.getValue();

        assertThat(savedSettlement.getOwnerId()).isEqualTo(ownerId);
        assertThat(savedSettlement.getSettlementType())
                .isEqualTo(SettlementType.SHARED);
        assertThat(savedSettlement.getSettlementStatus())
                .isEqualTo(SettlementStatus.IN_PROGRESS);
        assertThat(savedSettlement.getSettlementCategory())
                .isEqualTo("여행");
        assertThat(savedSettlement.getTitle())
                .isEqualTo("제주도 여행비 정산");
        assertThat(savedSettlement.getSplitType())
                .isEqualTo(SplitType.EQUAL);
        assertThat(savedSettlement.getDueDate())
                .isEqualTo(request.getDueDate());
        assertThat(savedSettlement.getCreatedAt()).isNotNull();

        // 공동정산에서는 정기정산 관련 값이 저장되지 않는다.
        assertThat(savedSettlement.getCycleRule()).isNull();
        assertThat(savedSettlement.getStartDate()).isNull();
        assertThat(savedSettlement.getEndDate()).isNull();
        assertThat(savedSettlement.getCycleDate()).isNull();
        assertThat(savedSettlement.getClosedAt()).isNull();
    }

    @Test
    @DisplayName("공동정산이 저장되지 않으면 예외가 발생한다")
    void createSharedSettlementFail() {

        Long ownerId = 1L;

        CreateSharedSettlementRequest request =
                CreateSharedSettlementRequest.builder()
                        .settlementCategory("회식")
                        .title("8월 회식비 정산")
                        .splitType(SplitType.CUSTOM)
                        .dueDate(LocalDate.now().plusDays(3))
                        .build();

        when(settlementMapper.insertSettlement(any(SettlementDTO.class)))
                .thenReturn(0);

        assertThatThrownBy(
                () -> sharedSettlementService.create(ownerId, request)
        ).isInstanceOf(DomainException.class);
    }
}