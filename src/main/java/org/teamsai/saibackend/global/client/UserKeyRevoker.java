package org.teamsai.saibackend.global.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * mock-bank에 이미 confirm(또는 발급)된 userKey를 best-effort로 revoke한다.
 * <p>
 * userKey 발급/교체 흐름의 여러 실패 지점(로컬 저장 실패, 동시성 충돌, 계좌 연동 실패 등)에서
 * 공통으로 필요한 "revoke 시도 → 실패해도 흐름은 막지 않고 로그만 남김" 패턴을 한 곳에 모은 것이다.
 * revoke 자체가 실패해도 예외를 던지지 않는다 — 호출부는 이미 자신만의 실패 처리 중이므로
 * 이 메서드가 그 흐름을 방해해서는 안 된다. 실패 시 남는 ERROR 로그를 보고 수동으로
 * mock-bank의 userKey 상태를 확인해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserKeyRevoker {

    private final MockBankClient mockBankClient;

    /**
     * @param caller 로그에 남길 호출부 식별자 (예: "AccountService")
     * @param userId 대상 회원 ID
     * @param userKey revoke할 userKey
     */
    public void revokeBestEffort(String caller, Long userId, String userKey) {
        try {
            mockBankClient.revokeUserKey(userKey);
            log.info("[{}] userKey revoke 완료 - userId: {}", caller, userId);
        } catch (Exception e) {
            log.error(
                    "[{}] userKey revoke 실패 - userId: {}, userKey 앞 8자: {}. "
                            + "mock-bank에 이 userKey가 ACTIVE 상태로 남아있을 수 있어 수동 확인이 필요합니다.",
                    caller, userId, mask(userKey), e
            );
        }
    }

    private String mask(String userKey) {
        if (userKey == null) {
            return "null";
        }
        return userKey.substring(0, Math.min(8, userKey.length()));
    }
}
