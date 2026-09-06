package cn.ningbingjian.learnjava.ioc.lesson004;

/** Single-threaded observation counter; no SMS is actually sent. */
public final class ConsoleSmsNotifier implements Notifier {
    private int sentCount;

    @Override
    public void send(String message) {
        sentCount++;
        System.out.println("SMS " + message);
    }

    public int sentCount() {
        return sentCount;
    }
}
