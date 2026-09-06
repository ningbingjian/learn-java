package cn.ningbingjian.learnjava.ioc.lesson002.runtime;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 最小回调驱动示例：分发器决定调用时机，不承担对象注册与注入。 */
public final class OrderEventDispatcher {
    public void dispatch(List<String> orderIds, Consumer<String> handler) {
        Objects.requireNonNull(orderIds, "orderIds");
        Objects.requireNonNull(handler, "handler");
        for (String orderId : orderIds) {
            System.out.println("DISPATCH " + orderId);
            handler.accept(orderId);
        }
    }
}
