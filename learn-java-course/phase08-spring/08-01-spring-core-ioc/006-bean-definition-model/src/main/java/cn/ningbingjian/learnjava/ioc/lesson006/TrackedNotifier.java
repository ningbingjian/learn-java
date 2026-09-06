package cn.ningbingjian.learnjava.ioc.lesson006;

import java.util.Objects;

/** Simulates lifecycle state without opening a real connection. */
public final class TrackedNotifier implements Notifier, AutoCloseable {
    private final String channel;
    private final LifecycleProbe probe;
    private String prefix = "";
    private boolean open;

    public TrackedNotifier(String channel, LifecycleProbe probe) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.probe = Objects.requireNonNull(probe, "probe");
        probe.record("constructed");
    }

    public void setPrefix(String prefix) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        probe.record("configured:" + prefix);
    }

    public void open() {
        open = true;
        probe.record("opened");
    }

    @Override
    public void send(String message) {
        if (!open) {
            throw new IllegalStateException("notifier is not open");
        }
        probe.recordMessage(prefix + " " + channel + ":" + message);
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
