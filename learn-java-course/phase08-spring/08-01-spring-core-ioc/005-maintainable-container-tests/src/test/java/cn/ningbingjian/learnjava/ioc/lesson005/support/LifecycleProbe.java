package cn.ningbingjian.learnjava.ioc.lesson005.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One probe per context: no static mutable state. */
public final class LifecycleProbe {
    private final List<String> events = new ArrayList<>();

    public void record(String event) {
        events.add(event);
    }

    public int count(String event) {
        return Collections.frequency(events, event);
    }

    public List<String> events() {
        return List.copyOf(events);
    }
}
