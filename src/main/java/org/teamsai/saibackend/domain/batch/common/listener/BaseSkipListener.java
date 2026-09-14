package org.teamsai.saibackend.domain.batch.common.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BaseSkipListener<T, S> implements SkipListener<T, S> {

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("[BATCH SKIP - READ] reason={}", t.getMessage());
    }

    @Override
    public void onSkipInProcess(T item, Throwable t) {
        log.warn("[BATCH SKIP - PROCESS] item={}, reason={}", item, t.getMessage());
    }

    @Override
    public void onSkipInWrite(S item, Throwable t) {
        log.warn("[BATCH SKIP - WRITE] item={}, reason={}", item, t.getMessage());
    }
}