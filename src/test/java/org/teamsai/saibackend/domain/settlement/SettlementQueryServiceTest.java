package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementDetailResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementListResponse;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.service.SettlementQueryService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementQueryService 단위 테스트")
class SettlementQueryServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long SETTLEMENT_ID = 15L;


    @Mock
    private SettlementMapper settlementMapper;

    @InjectMocks
    private SettlementQueryService settlementQueryService;


    @Test
    @DisplayName("사용자의 정산 목록을 조회한다")
    void getSettlementListSuccess() {

        Long userId = 1L;

        List<SettlementListResponse> expected =
                List.of(
                        mock(
                                SettlementListResponse.class
                        ),
                        mock(
                                SettlementListResponse.class
                        )
                );


        when(
                settlementMapper.findAllByUserId(
                        userId
                )
        ).thenReturn(expected);


        List<SettlementListResponse> result =
                settlementQueryService
                        .getSettlementList(
                                userId
                        );


        assertThat(result)
                .isEqualTo(expected);

        verify(settlementMapper)
                .findAllByUserId(
                        userId
                );
    }


    @Test
    @DisplayName("조회되는 정산이 없으면 빈 목록을 반환한다")
    void getSettlementListReturnsEmptyList() {

        Long userId = 1L;


        when(
                settlementMapper.findAllByUserId(
                        userId
                )
        ).thenReturn(
                List.of()
        );


        List<SettlementListResponse> result =
                settlementQueryService
                        .getSettlementList(
                                userId
                        );


        assertThat(result)
                .isEmpty();

        verify(settlementMapper)
                .findAllByUserId(
                        userId
                );
    }


    @Test
    @DisplayName("정산 생성자는 정산 상세를 조회할 수 있다")
    void getSettlementDetailByOwner() {

        SettlementDetailResponse detail =
                createDetail(
                        "OWNER"
                );


        given(
                settlementMapper.findDetailById(
                        SETTLEMENT_ID,
                        OWNER_ID
                )
        ).willReturn(
                Optional.of(detail)
        );


        SettlementDetailResponse result =
                settlementQueryService
                        .getSettlementDetail(
                                SETTLEMENT_ID,
                                OWNER_ID
                        );


        assertThat(result.settlementId())
                .isEqualTo(
                        SETTLEMENT_ID
                );

        assertThat(result.title())
                .isEqualTo(
                        "테스트 정산"
                );

        assertThat(result.role())
                .isEqualTo(
                        "OWNER"
                );
    }


    @Test
    @DisplayName("정산 참여자는 정산 상세를 조회할 수 있다")
    void getSettlementDetailByMember() {

        Long memberId = 2L;


        given(
                settlementMapper.findDetailById(
                        SETTLEMENT_ID,
                        memberId
                )
        ).willReturn(
                Optional.of(
                        createDetail(
                                "MEMBER"
                        )
                )
        );


        SettlementDetailResponse result =
                settlementQueryService
                        .getSettlementDetail(
                                SETTLEMENT_ID,
                                memberId
                        );


        assertThat(result.role())
                .isEqualTo(
                        "MEMBER"
                );
    }


    @Test
    @DisplayName(
            "정산과 관계없는 사용자는 상세를 조회할 수 없다"
    )
    void getSettlementDetailFailsWhenNotParticipant() {

        Long otherUserId = 3L;


        given(
                settlementMapper.findDetailById(
                        SETTLEMENT_ID,
                        otherUserId
                )
        ).willReturn(
                Optional.of(
                        createDetail(
                                "NONE"
                        )
                )
        );


        assertThatThrownBy(
                () ->
                        settlementQueryService
                                .getSettlementDetail(
                                        SETTLEMENT_ID,
                                        otherUserId
                                )
        ).isInstanceOf(
                DomainException.class
        );
    }


    private SettlementDetailResponse createDetail(
            String role
    ) {
        return new SettlementDetailResponse(
                SETTLEMENT_ID,
                "테스트 정산",
                "모임",
                "SHARED",
                "IN_PROGRESS",
                "EQUAL",
                LocalDate.now().plusDays(7),
                null,
                null,
                LocalDateTime.now(),
                role
        );
    }
}