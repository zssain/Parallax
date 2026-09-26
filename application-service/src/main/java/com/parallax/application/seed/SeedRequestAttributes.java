package com.parallax.application.seed;

import org.springframework.web.context.request.RequestAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * A minimal in-memory {@link RequestAttributes} so the demo seeder can drive the request-scoped
 * intake pipeline (SPEC §3) without an HTTP request. Only request scope is backed by a map; session
 * scope is unused. One instance per demo application gives each its own scoped beans.
 */
final class SeedRequestAttributes implements RequestAttributes {

    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<String, Runnable> destructionCallbacks = new HashMap<>();

    @Override
    public Object getAttribute(String name, int scope) {
        return attributes.get(name);
    }

    @Override
    public void setAttribute(String name, Object value, int scope) {
        attributes.put(name, value);
    }

    @Override
    public void removeAttribute(String name, int scope) {
        attributes.remove(name);
        destructionCallbacks.remove(name);
    }

    @Override
    public String[] getAttributeNames(int scope) {
        return attributes.keySet().toArray(new String[0]);
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback, int scope) {
        destructionCallbacks.put(name, callback);
    }

    @Override
    public Object resolveReference(String key) {
        return null;
    }

    @Override
    public String getSessionId() {
        return "seed";
    }

    @Override
    public Object getSessionMutex() {
        return this;
    }

    /** Runs and clears any registered destruction callbacks (request-scoped bean cleanup). */
    void complete() {
        destructionCallbacks.values().forEach(Runnable::run);
        destructionCallbacks.clear();
        attributes.clear();
    }
}
