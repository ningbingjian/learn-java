package cn.ningbingjian.learnjava.ioc.lesson005;

import java.util.Objects;

public final class OrderNotificationService {
    private final Notifier notifier;

    public OrderNotificationService(Notifier notifier) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public void notifyAccepted(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        if (orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        notifier.send("order=" + orderId + " accepted");
    }
}
