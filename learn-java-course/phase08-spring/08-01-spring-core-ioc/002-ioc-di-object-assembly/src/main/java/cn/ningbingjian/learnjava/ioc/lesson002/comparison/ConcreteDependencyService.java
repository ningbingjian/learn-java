package cn.ningbingjian.learnjava.ioc.lesson002.comparison;

import java.util.Objects;

import cn.ningbingjian.learnjava.ioc.lesson002.adapter.ConsoleEmailNotifier;

/** 有构造器注入，但业务类仍然依赖邮件实现：用于区分 DI 与 DIP。 */
public final class ConcreteDependencyService {
    private final ConsoleEmailNotifier notifier;

    public ConcreteDependencyService(ConsoleEmailNotifier notifier) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public void notifyAccepted(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        notifier.send("order=" + orderId + " accepted");
    }
}
