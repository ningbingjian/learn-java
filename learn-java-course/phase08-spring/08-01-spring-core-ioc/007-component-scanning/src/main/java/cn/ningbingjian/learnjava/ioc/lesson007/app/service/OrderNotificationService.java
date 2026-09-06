package cn.ningbingjian.learnjava.ioc.lesson007.app.service;

import cn.ningbingjian.learnjava.ioc.lesson007.api.Notifier;
import cn.ningbingjian.learnjava.ioc.lesson007.api.OrderRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class OrderNotificationService {
    private final OrderRepository repository;
    private final Notifier notifier;

    public OrderNotificationService(OrderRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public boolean notifyAccepted(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        if (!repository.exists(orderId)) {
            return false;
        }
        notifier.send("order=" + orderId + " accepted");
        return true;
    }
}
