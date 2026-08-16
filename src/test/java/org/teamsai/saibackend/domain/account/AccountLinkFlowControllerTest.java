package org.teamsai.saibackend.domain.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.identity.service.IdentityValidator;
import org.teamsai.saibackend.domain.link.controller.AccountLinkFlowController;
import org.teamsai.saibackend.domain.link.service.AccountLinkService;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.client.MockBankClient;
import org.teamsai.saibackend.global.jwt.JwtAuthenticationEntryPoint;
import org.teamsai.saibackend.global.jwt.JwtAuthenticationFilter;
import org.teamsai.saibackend.global.jwt.JwtTokenProvider;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountLinkFlowController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "sai.mock-bank.base-url=http://localhost:8081",
        "sai.backend.base-url=http://localhost:8080"
})
@DisplayName("AccountLinkFlowController 단위 테스트")
class AccountLinkFlowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private LinkedBankAccountService linkedBankAccountService;
    @MockitoBean
    private AccountLinkService accountLinkService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private IdentityValidator identityValidator;
    @MockitoBean
    private MockBankClient mockBankClient;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private static final Long USER_ID = 1L;
    private static final String STATE = "valid-state-token";
    private static final String USER_KEY = "mb_rawkey12345678";

    @Test
    @DisplayName("정상 흐름 - confirm 성공 후 연동까지 성공하면 success 뷰를 반환한다")
    void linkCallback_정상흐름() throws Exception {
        given(jwtTokenProvider.getUserIdFromLinkState(STATE)).willReturn(Optional.of(USER_ID));

        mockMvc.perform(get("/accounts/link/callback")
                        .param("state", STATE)
                        .param("userKey", USER_KEY)
                        .param("accountIds", "1,2,3"))
                .andExpect(status().isOk())
                .andExpect(view().name("link/link-complete"))
                .andExpect(model().attribute("success", true));

        // 순서 검증: confirm이 completeLink보다 먼저 호출되어야 한다
        var inOrder = org.mockito.Mockito.inOrder(mockBankClient, accountLinkService);
        inOrder.verify(mockBankClient).confirmUserKey(USER_KEY);
        inOrder.verify(accountLinkService).completeLink(USER_ID, USER_KEY, List.of(1L, 2L, 3L));
    }

    @Test
    @DisplayName("confirm이 실패하면 completeLink를 호출하지 않고 에러 뷰를 반환한다")
    void linkCallback_confirm실패시_에러뷰() throws Exception {
        given(jwtTokenProvider.getUserIdFromLinkState(STATE)).willReturn(Optional.of(USER_ID));
        willThrow(new RuntimeException("mock-bank 다운"))
                .given(mockBankClient).confirmUserKey(USER_KEY);

        mockMvc.perform(get("/accounts/link/callback")
                        .param("state", STATE)
                        .param("userKey", USER_KEY)
                        .param("accountIds", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", false));

        verify(mockBankClient).confirmUserKey(USER_KEY);
        verify(accountLinkService, never()).completeLink(anyLong(), anyString(), anyList());
    }

    @Test
    @DisplayName("state가 유효하지 않으면 에러 뷰를 반환하고 confirm/연동 모두 호출되지 않는다")
    void linkCallback_state유효하지않으면_에러뷰() throws Exception {
        given(jwtTokenProvider.getUserIdFromLinkState(STATE))
                .willThrow(UserErrorCode.INVALID_LINK_STATE.toException());

        mockMvc.perform(get("/accounts/link/callback")
                        .param("state", STATE)
                        .param("userKey", USER_KEY)
                        .param("accountIds", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", false));

        verify(mockBankClient, never()).confirmUserKey(anyString());
        verify(accountLinkService, never()).completeLink(anyLong(), anyString(), anyList());
    }

    @Test
    @DisplayName("accountIds가 없으면 에러 뷰를 반환하고 confirm/연동 모두 호출되지 않는다")
    void linkCallback_accountIds없으면_에러뷰() throws Exception {
        given(jwtTokenProvider.getUserIdFromLinkState(STATE)).willReturn(Optional.of(USER_ID));

        mockMvc.perform(get("/accounts/link/callback")
                        .param("state", STATE)
                        .param("userKey", USER_KEY))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", false));

        verify(mockBankClient, never()).confirmUserKey(anyString());
        verify(accountLinkService, never()).completeLink(anyLong(), anyString(), anyList());
    }

    @Test
    @DisplayName("accountIds 형식이 잘못되면 에러 뷰를 반환하고 confirm/연동 모두 호출되지 않는다")
    void linkCallback_accountIds형식오류_에러뷰() throws Exception {
        given(jwtTokenProvider.getUserIdFromLinkState(STATE)).willReturn(Optional.of(USER_ID));

        mockMvc.perform(get("/accounts/link/callback")
                        .param("state", STATE)
                        .param("userKey", USER_KEY)
                        .param("accountIds", "1,abc,3"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", false));

        verify(mockBankClient, never()).confirmUserKey(anyString());
        verify(accountLinkService, never()).completeLink(anyLong(), anyString(), anyList());
    }

    @Test
    @DisplayName("confirm은 성공했지만 계좌 연동이 실패하면 mock-bank에 revoke를 요청하고 에러 뷰를 반환한다")
    void linkCallback_연동실패시_revoke요청후_에러뷰() throws Exception {
        given(jwtTokenProvider.getUserIdFromLinkState(STATE)).willReturn(Optional.of(USER_ID));
        willThrow(UserErrorCode.LINK_KEY_UPDATE_CONFLICT.toException())
                .given(accountLinkService).completeLink(USER_ID, USER_KEY, List.of(1L));

        mockMvc.perform(get("/accounts/link/callback")
                        .param("state", STATE)
                        .param("userKey", USER_KEY)
                        .param("accountIds", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", false));

        var inOrder = org.mockito.Mockito.inOrder(mockBankClient, accountLinkService);
        inOrder.verify(mockBankClient).confirmUserKey(USER_KEY);
        inOrder.verify(accountLinkService).completeLink(USER_ID, USER_KEY, List.of(1L));
        inOrder.verify(mockBankClient).revokeUserKey(USER_KEY);
    }

    @Test
    @DisplayName("revoke 요청마저 실패해도 에러 뷰는 정상 반환된다 (예외가 사용자에게 전파되지 않음)")
    void linkCallback_revoke도_실패해도_에러뷰는_정상반환() throws Exception {
        given(jwtTokenProvider.getUserIdFromLinkState(STATE)).willReturn(Optional.of(USER_ID));
        willThrow(UserErrorCode.LINK_KEY_UPDATE_CONFLICT.toException())
                .given(accountLinkService).completeLink(USER_ID, USER_KEY, List.of(1L));
        willThrow(new RuntimeException("mock-bank 다운"))
                .given(mockBankClient).revokeUserKey(USER_KEY);

        mockMvc.perform(get("/accounts/link/callback")
                        .param("state", STATE)
                        .param("userKey", USER_KEY)
                        .param("accountIds", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", false));

        verify(mockBankClient).revokeUserKey(USER_KEY);
    }

    @Test
    @DisplayName("completeLink에서 예상치 못한 예외가 발생하면 revoke 요청 후 예외가 전파되어 500이 반환된다")
    void linkCallback_예상치못한예외_revoke후_전파() throws Exception {
        given(jwtTokenProvider.getUserIdFromLinkState(STATE)).willReturn(Optional.of(USER_ID));
        willThrow(new RuntimeException("예상치 못한 DB 오류"))
                .given(accountLinkService).completeLink(USER_ID, USER_KEY, List.of(1L));

        mockMvc.perform(get("/accounts/link/callback")
                        .param("state", STATE)
                        .param("userKey", USER_KEY)
                        .param("accountIds", "1"))
                .andExpect(status().isInternalServerError());

        var inOrder = org.mockito.Mockito.inOrder(mockBankClient, accountLinkService);
        inOrder.verify(mockBankClient).confirmUserKey(USER_KEY);
        inOrder.verify(accountLinkService).completeLink(USER_ID, USER_KEY, List.of(1L));
        inOrder.verify(mockBankClient).revokeUserKey(USER_KEY);
    }
}