package cn.ningbingjian.learnjava.ioc.lesson004;

import java.util.Objects;

public final class ReceiptNotificationService {
    private final Notifier notifier;

    public ReceiptNotificationService(Notifier notifier) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public void notifyReady(String receiptId) {
        notifier.send("receipt=" + Objects.requireNonNull(receiptId, "receiptId") + " ready");
    }
}
