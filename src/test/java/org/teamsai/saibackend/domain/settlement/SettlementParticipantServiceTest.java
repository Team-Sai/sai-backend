package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saibackend.domain.settlement.dto.SettlementParticipantDTO;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.service.SettlementParticipantService;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantRole;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantStatus;
import org.teamsai.saibackend.global.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementParticipantService 단위 테스트")
class SettlementParticipantServiceTest {

    private static final Long SETTLEMENT_ID = 10L;
    private static final Long USER_ID = 20L;
    private static final Long PARTICIPANT_ID = 100L;

    @Mock
    private SettlementParticipantMapper participantMapper;

    @InjectMocks
    private SettlementParticipantService participantService;


    @Test
    @DisplayName(
            "정산 ID와 사용자 ID로 ACTIVE MEMBER 참여자를 생성하고 참여자 ID를 반환한다"
    )
    void createParticipantSuccess() {

        when(
                participantMapper.insert(
                        any(SettlementParticipantDTO.class)
                )
        ).thenAnswer(invocation -> {

            SettlementParticipantDTO participant =
                    invocation.getArgument(0);

            ReflectionTestUtils.setField(
                    participant,
                    "participantId",
                    PARTICIPANT_ID
            );

            return 1;
        });


        Long result =
                participantService.createParticipant(
                        SETTLEMENT_ID,
                        USER_ID
                );


        ArgumentCaptor<SettlementParticipantDTO> captor =
                ArgumentCaptor.forClass(
                        SettlementParticipantDTO.class
                );

        verify(participantMapper)
                .insert(captor.capture());


        SettlementParticipantDTO savedParticipant =
                captor.getValue();


        assertThat(result)
                .isEqualTo(PARTICIPANT_ID);

        assertThat(savedParticipant.getSettlementId())
                .isEqualTo(SETTLEMENT_ID);

        assertThat(savedParticipant.getUserId())
                .isEqualTo(USER_ID);

        assertThat(savedParticipant.getParticipantRole())
                .isEqualTo(
                        SettlementParticipantRole.MEMBER
                );

        assertThat(savedParticipant.getParticipantStatus())
                .isEqualTo(
                        SettlementParticipantStatus.ACTIVE
                );

        assertThat(savedParticipant.getJoinedAt())
                .isNotNull();
    }


    @Test
    @DisplayName(
            "참여자 저장 결과가 1건이 아니면 예외가 발생한다"
    )
    void createParticipantFailsWhenInsertCountIsInvalid() {

        when(
                participantMapper.insert(
                        any(SettlementParticipantDTO.class)
                )
        ).thenReturn(0);


        assertThatThrownBy(
                () ->
                        participantService.createParticipant(
                                SETTLEMENT_ID,
                                USER_ID
                        )
        ).isInstanceOf(
                DomainException.class
        );


        verify(participantMapper)
                .insert(
                        any(SettlementParticipantDTO.class)
                );
    }
}