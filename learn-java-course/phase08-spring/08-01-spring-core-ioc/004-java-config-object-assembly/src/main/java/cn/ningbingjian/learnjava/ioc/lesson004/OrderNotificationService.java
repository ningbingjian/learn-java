package cn.ningbingjian.learnjava.ioc.lesson004;

import java.util.Objects;

public final class OrderNotificationService {
    private final Notifier notifier;

    public OrderNotificationService(Notifier notifier) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public void notifyAccepted(String orderId) {
        notifier.send("order=" + Objects.requireNonNull(orderId, "orderId") + " accepted");
    }
}
