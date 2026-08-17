package org.teamsai.saibackend.domain.batch.common.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BaseSkipListener<T, S> implements SkipListener<T, S> {

    @Override
    public void onSkipInRead(Throwable t) {
        // 데이터를 읽는(Reader) 단계에서 실패한 경우 — 어떤 아이템인지는 특정 못 함
        log.warn("[BATCH SKIP - READ] reason={}", t.getMessage());
    }

    @Override
    public void onSkipInProcess(T item, Throwable t) {
        // 읽은 데이터를 가공(Processor)하다가 실패한 경우 — 어떤 아이템인지 알 수 있음
        log.warn("[BATCH SKIP - PROCESS] item={}, reason={}", item, t.getMessage());
    }

    @Override
    public void onSkipInWrite(S item, Throwable t) {
        // 가공된 데이터를 저장(Writer)하다가 실패한 경우
        log.warn("[BATCH SKIP - WRITE] item={}, reason={}", item, t.getMessage());
    }
}