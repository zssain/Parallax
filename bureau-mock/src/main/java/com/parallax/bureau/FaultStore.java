package com.parallax.bureau;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/** Holds the current injected fault. Dev profile only. */
@Component
@Profile("dev")
public class FaultStore {

    private final AtomicReference<FaultState> state = new AtomicReference<>(new FaultState("NONE", 0));

    public FaultState get() {
        return state.get();
    }

    public void set(FaultState newState) {
        state.set(newState);
    }
}
