package cn.ningbingjian.learnjava.ioc.lesson002.comparison;

import java.util.Objects;

import cn.ningbingjian.learnjava.ioc.lesson002.application.Notifier;

/** 仅用于演示外部写字段的装配时序；这里没有 Spring 注解或容器。 */
public final class FieldOrderNotificationService {
    private Notifier notifier;

    public void notifyAccepted(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        if (notifier == null) {
            throw new IllegalStateException("notifier has not been injected");
        }
        notifier.send("order=" + orderId + " accepted");
    }
}
