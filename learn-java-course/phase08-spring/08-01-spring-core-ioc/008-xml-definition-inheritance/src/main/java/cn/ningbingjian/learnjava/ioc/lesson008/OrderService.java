package cn.ningbingjian.learnjava.ioc.lesson008;

import java.util.Objects;

public final class OrderService {
    private final RouteNotifier notifier;

    public OrderService(RouteNotifier notifier) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public void accept(String orderId) { notifier.send(orderId); }
    public RouteNotifier notifier() { return notifier; }
}
