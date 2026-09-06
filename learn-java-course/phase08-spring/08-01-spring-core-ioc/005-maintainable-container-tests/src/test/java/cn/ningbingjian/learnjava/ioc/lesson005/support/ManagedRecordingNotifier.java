package cn.ningbingjian.learnjava.ioc.lesson005.support;

import cn.ningbingjian.learnjava.ioc.lesson005.Notifier;
import java.util.ArrayList;
import java.util.List;

/** Test-only, single-threaded substitute that records messages and lifecycle events. */
public final class ManagedRecordingNotifier implements Notifier, AutoCloseable {
    private final LifecycleProbe probe;
    private final List<String> messages = new ArrayList<>();
    private boolean open;

    public ManagedRecordingNotifier(LifecycleProbe probe) {
        this.probe = probe;
        probe.record("created");
    }

    public void open() {
        open = true;
        probe.record("opened");
    }

    @Override
    public void send(String message) {
        if (!open) {
            throw new IllegalStateException("recording notifier is not open");
        }
        messages.add(message);
    }

    public List<String> messages() {
        return List.copyOf(messages);
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        open = false;
        probe.record("closed");
    }
}
