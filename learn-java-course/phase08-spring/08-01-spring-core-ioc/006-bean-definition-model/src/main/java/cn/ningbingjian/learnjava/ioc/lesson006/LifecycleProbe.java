package cn.ningbingjian.learnjava.ioc.lesson006;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Single-threaded observer for this lesson; no global state. */
public final class LifecycleProbe {
    private final List<String> events = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();

    public void record(String event) {
        events.add(event);
    }

    public void recordMessage(String message) {
        messages.add(message);
    }

    public int count(String event) {
        return Collections.frequency(events, event);
    }

    public List<String> events() {
        return List.copyOf(events);
    }

    public List<String> messages() {
        return List.copyOf(messages);
    }
}
