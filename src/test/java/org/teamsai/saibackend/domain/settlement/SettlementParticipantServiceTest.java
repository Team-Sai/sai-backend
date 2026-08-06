package org.teamsai.saibackend.domain.settlement;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    private static final Long INVITATION_ID = 10L;
    private static final Long PARTICIPANT_ID = 100L;

    @Mock
    private SettlementParticipantMapper participantMapper;

    @InjectMocks
    private SettlementParticipantService participantService;

    @Nested
    @DisplayName("초대 기반 참여자 생성")
    class CreateFromInvitation {

        @Test
        @DisplayName(
                "초대 ID로 활성 상태의 일반 참여자를 생성하고 참여자 ID를 반환한다"
        )
        void createFromInvitationSuccess() {
            when(participantMapper.insert(
                    any(SettlementParticipantDTO.class)
            )).thenAnswer(invocation -> {
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
                    participantService.createFromInvitation(
                            INVITATION_ID
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

            assertThat(savedParticipant.getInvitationId())
                    .isEqualTo(INVITATION_ID);

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
        void createFromInvitationFailsWhenInsertCountIsInvalid() {
            when(participantMapper.insert(
                    any(SettlementParticipantDTO.class)
            )).thenReturn(0);

            assertThatThrownBy(
                    () -> participantService
                            .createFromInvitation(
                                    INVITATION_ID
                            )
            ).isInstanceOf(DomainException.class);

            verify(participantMapper)
                    .insert(any(SettlementParticipantDTO.class));
        }
    }

    @Nested
    @DisplayName("참여자 제거")
    class RemoveByInvitationId {

        @Test
        @DisplayName(
                "초대를 거절하면 연결된 활성 참여자를 제거 상태로 변경한다"
        )
        void removeByInvitationIdSuccess() {
            when(
                    participantMapper
                            .updateStatusByInvitationId(
                                    INVITATION_ID,
                                    SettlementParticipantStatus.REMOVED
                            )
            ).thenReturn(1);

            participantService.removeByInvitationId(
                    INVITATION_ID
            );

            verify(participantMapper)
                    .updateStatusByInvitationId(
                            INVITATION_ID,
                            SettlementParticipantStatus.REMOVED
                    );
        }

        @Test
        @DisplayName(
                "연결된 활성 참여자의 상태 변경에 실패하면 예외가 발생한다"
        )
        void removeByInvitationIdFailsWhenUpdateCountIsInvalid() {
            when(
                    participantMapper
                            .updateStatusByInvitationId(
                                    INVITATION_ID,
                                    SettlementParticipantStatus.REMOVED
                            )
            ).thenReturn(0);

            assertThatThrownBy(
                    () -> participantService
                            .removeByInvitationId(
                                    INVITATION_ID
                            )
            ).isInstanceOf(DomainException.class);

            verify(participantMapper)
                    .updateStatusByInvitationId(
                            INVITATION_ID,
                            SettlementParticipantStatus.REMOVED
                    );
        }
    }
}