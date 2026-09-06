package cn.ningbingjian.learnjava.ioc.lesson002.application;

import java.util.Objects;

/** 必需依赖通过构造器传入，业务代码只依赖应用层的通知契约。 */
public final class OrderNotificationService {
    private final Notifier notifier;

    public OrderNotificationService(Notifier notifier) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public void notifyAccepted(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        notifier.send("order=" + orderId + " accepted");
    }
}
