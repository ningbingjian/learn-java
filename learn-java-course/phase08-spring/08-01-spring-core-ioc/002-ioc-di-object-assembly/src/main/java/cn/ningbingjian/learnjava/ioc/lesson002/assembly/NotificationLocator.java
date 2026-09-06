package cn.ningbingjian.learnjava.ioc.lesson002.assembly;

import java.util.Objects;

import cn.ningbingjian.learnjava.ioc.lesson002.application.Notifier;

/** 单一能力的定位器，使用实例状态，避免用静态全局状态混淆定位模式本身。 */
public final class NotificationLocator {
    private Notifier notifier;

    public void register(Notifier notifier) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public Notifier getNotifier() {
        if (notifier == null) {
            throw new IllegalStateException("notifier is not registered");
        }
        return notifier;
    }
}
