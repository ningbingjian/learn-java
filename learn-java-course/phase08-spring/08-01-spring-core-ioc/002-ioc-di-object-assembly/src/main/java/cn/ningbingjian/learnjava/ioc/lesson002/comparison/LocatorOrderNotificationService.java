package cn.ningbingjian.learnjava.ioc.lesson002.comparison;

import java.util.Objects;

import cn.ningbingjian.learnjava.ioc.lesson002.assembly.NotificationLocator;

/** 定位器本身通过构造器注入，但通知能力由业务方法在调用时主动查找。 */
public final class LocatorOrderNotificationService {
    private final NotificationLocator locator;

    public LocatorOrderNotificationService(NotificationLocator locator) {
        this.locator = Objects.requireNonNull(locator, "locator");
    }

    public void notifyAccepted(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        locator.getNotifier().send("order=" + orderId + " accepted");
    }
}
