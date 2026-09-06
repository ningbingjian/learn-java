package cn.ningbingjian.learnjava.ioc.lesson005;

/** Single-threaded lifecycle example; no real connection is opened. */
public final class ManagedConsoleNotifier implements Notifier, AutoCloseable {
    private boolean open;
    private int closeCount;

    public void open() {
        open = true;
        System.out.println("console.opened");
    }

    @Override
    public void send(String message) {
        if (!open) {
            throw new IllegalStateException("console notifier is not open");
        }
        System.out.println("EMAIL " + message);
    }

    public boolean isOpen() {
        return open;
    }

    public int closeCount() {
        return closeCount;
    }

    @Override
    public void close() {
        open = false;
        closeCount++;
        System.out.println("console.closed");
    }
}
