package cn.ningbingjian.learnjava.ioc.lesson004;

/** Single-threaded observation counter; no email is actually sent. */
public final class ConsoleEmailNotifier implements Notifier {
    private int sentCount;

    @Override
    public void send(String message) {
        sentCount++;
        System.out.println("EMAIL " + message);
    }

    public int sentCount() {
        return sentCount;
    }
}
