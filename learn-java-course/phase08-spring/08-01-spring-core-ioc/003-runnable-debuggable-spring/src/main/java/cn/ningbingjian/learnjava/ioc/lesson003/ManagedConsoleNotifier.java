package cn.ningbingjian.learnjava.ioc.lesson003;

/** Single-threaded lifecycle probe; no external connection is opened. */
public final class ManagedConsoleNotifier implements Notifier, AutoCloseable {
    private boolean open;

    public void open() {
        open = true;
        System.out.println("notifier.open");
    }

    @Override
    public void send(String message) {
        if (!open) {
            throw new IllegalStateException("notifier is not open");
        }
        System.out.println("EMAIL " + message);
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        open = false;
        System.out.println("notifier.close");
    }
}
