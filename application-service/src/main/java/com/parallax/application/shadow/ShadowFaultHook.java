package com.parallax.application.shadow;

/** Extension point invoked before a shadow_result insert; tests use it to force a swallowed failure. */
public interface ShadowFaultHook {

    void beforeShadowInsert();
}
