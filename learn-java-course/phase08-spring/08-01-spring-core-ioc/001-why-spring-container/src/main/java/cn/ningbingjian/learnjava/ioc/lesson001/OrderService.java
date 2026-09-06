package cn.ningbingjian.learnjava.ioc.lesson001;

import java.util.Objects;

/** 业务版本：声明需要哪些能力，由调用者传入实际对象。 */
public final class OrderService {
    private final Inventory inventory;
    private final Notifier notifier;

    public OrderService(Inventory inventory, Notifier notifier) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public boolean place(Order order) {
        Objects.requireNonNull(order, "order");
        if (!inventory.reserve(order.getQuantity())) {
            return false;
        }
        notifier.orderAccepted(order);
        return true;
    }
}
