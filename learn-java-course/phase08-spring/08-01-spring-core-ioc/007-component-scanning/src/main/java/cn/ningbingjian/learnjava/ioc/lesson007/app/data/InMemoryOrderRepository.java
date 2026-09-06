package cn.ningbingjian.learnjava.ioc.lesson007.app.data;

import cn.ningbingjian.learnjava.ioc.lesson007.api.OrderRepository;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public final class InMemoryOrderRepository implements OrderRepository {
    private final Set<String> orderIds = Set.of("O-001");

    @Override
    public boolean exists(String orderId) {
        return orderIds.contains(orderId);
    }
}
