package com.parallax.application.shadow;

import org.springframework.stereotype.Component;

/** Production no-op {@link ShadowFaultHook}. A test bean (@Primary) replaces it to force a failure. */
@Component
public class NoopShadowFaultHook implements ShadowFaultHook {

    @Override
    public void beforeShadowInsert() {
        // no-op
    }
}
