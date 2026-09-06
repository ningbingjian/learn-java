package cn.ningbingjian.learnjava.ioc.lesson001;

import java.util.Objects;

/** 对照版本：业务对象内部决定依赖的具体实现和实例数量。 */
public final class HardcodedOrderService {
    private final Inventory inventory = new InMemoryInventory(1);
    private final Notifier notifier = new ConsoleEmailNotifier();

    public boolean place(Order order) {
        Objects.requireNonNull(order, "order");
        if (!inventory.reserve(order.getQuantity())) {
            return false;
        }
        notifier.orderAccepted(order);
        return true;
    }
}
