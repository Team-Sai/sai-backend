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
    @DisplayName("정상 흐름 - 연동 성공 + confirm 성공 시 success 뷰를 반환한다")
    void linkCallback_정상흐름() throws Exception {
        given(jwtTokenProvider.getUserIdFromLinkState(STATE)).willReturn(Optional.of(USER_ID));

        mockMvc.perform(get("/accounts/link/callback")
                        .param("state", STATE)
                        .param("userKey", USER_KEY)
                        .param("accountIds", "1,2,3"))
                .andExpect(status().isOk())
                .andExpect(view().name("link/link-complete"))
                .andExpect(model().attribute("success", true));

        verify(accountLinkService).completeLink(USER_ID, USER_KEY, java.util.List.of(1L, 2L, 3L));
        verify(mockBankClient).confirmUserKey(USER_KEY);
    }

    @Test
    @DisplayName("confirm 호출이 실패해도 로컬 연동이 끝났으면 success 뷰를 반환한다")
    void linkCallback_confirm실패해도_성공처리() throws Exception {
        given(jwtTokenProvider.getUserIdFromLinkState(STATE)).willReturn(Optional.of(USER_ID));
        willThrow(new RuntimeException("mock-bank 다운"))
                .given(mockBankClient).confirmUserKey(USER_KEY);

        mockMvc.perform(get("/accounts/link/callback")
                        .param("state", STATE)
                        .param("userKey", USER_KEY)
                        .param("accountIds", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", true));

        verify(accountLinkService).completeLink(USER_ID, USER_KEY, java.util.List.of(1L));
        verify(mockBankClient).confirmUserKey(USER_KEY);
    }

    @Test
    @DisplayName("state가 유효하지 않으면 에러 뷰를 반환하고 이후 로직은 실행되지 않는다")
    void linkCallback_state유효하지않으면_에러뷰() throws Exception {
        given(jwtTokenProvider.getUserIdFromLinkState(STATE))
                .willThrow(UserErrorCode.INVALID_LINK_STATE.toException());

        mockMvc.perform(get("/accounts/link/callback")
                        .param("state", STATE)
                        .param("userKey", USER_KEY)
                        .param("accountIds", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", false));

        verify(accountLinkService, never()).completeLink(anyString() == null ? null : USER_ID, anyString(), anyList());
        verify(mockBankClient, never()).confirmUserKey(anyString());
    }

    @Test
    @DisplayName("accountIds가 없으면 에러 뷰를 반환한다")
    void linkCallback_accountIds없으면_에러뷰() throws Exception {
        given(jwtTokenProvider.getUserIdFromLinkState(STATE)).willReturn(Optional.of(USER_ID));

        mockMvc.perform(get("/accounts/link/callback")
                        .param("state", STATE)
                        .param("userKey", USER_KEY))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", false));

        verify(accountLinkService, never()).completeLink(anyLong(), anyString(), anyList());verify(mockBankClient, never()).confirmUserKey(anyString());
    }

    @Test
    @DisplayName("accountIds 형식이 잘못되면 에러 뷰를 반환한다")
    void linkCallback_accountIds형식오류_에러뷰() throws Exception {
        given(jwtTokenProvider.getUserIdFromLinkState(STATE)).willReturn(Optional.of(USER_ID));

        mockMvc.perform(get("/accounts/link/callback")
                        .param("state", STATE)
                        .param("userKey", USER_KEY)
                        .param("accountIds", "1,abc,3"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", false));

        verify(mockBankClient, never()).confirmUserKey(anyString());
    }

    @Test
    @DisplayName("계좌 연동 자체가 실패하면 confirm은 호출되지 않는다")
    void linkCallback_연동실패시_confirm호출안함() throws Exception {
        given(jwtTokenProvider.getUserIdFromLinkState(STATE)).willReturn(Optional.of(USER_ID));
        willThrow(org.teamsai.saibackend.domain.user.exception.UserErrorCode.LINK_KEY_UPDATE_CONFLICT.toException())
                .given(accountLinkService).completeLink(USER_ID, USER_KEY, java.util.List.of(1L));

        mockMvc.perform(get("/accounts/link/callback")
                        .param("state", STATE)
                        .param("userKey", USER_KEY)
                        .param("accountIds", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", false));

        verify(mockBankClient, never()).confirmUserKey(anyString());
    }
}