package cn.ningbingjian.learnjava.ioc.lesson002.comparison;

import java.util.Objects;

import cn.ningbingjian.learnjava.ioc.lesson002.application.Notifier;

/** 对照 setter 装配：对象可以先存在，但必须完成配置后才能使用。 */
public final class SetterOrderNotificationService {
    private Notifier notifier;

    public void setNotifier(Notifier notifier) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public void notifyAccepted(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        if (notifier == null) {
            throw new IllegalStateException("notifier must be set before use");
        }
        notifier.send("order=" + orderId + " accepted");
    }
}
