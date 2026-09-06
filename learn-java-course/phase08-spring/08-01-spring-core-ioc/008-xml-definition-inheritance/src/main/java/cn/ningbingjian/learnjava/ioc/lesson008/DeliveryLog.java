package cn.ningbingjian.learnjava.ioc.lesson008;

import java.util.ArrayList;
import java.util.List;

public final class DeliveryLog {
    private final List<String> lifecycle = new ArrayList<>();
    private final List<String> deliveries = new ArrayList<>();

    public void recordLifecycle(String event) { lifecycle.add(event); }
    public void recordDelivery(String message) { deliveries.add(message); }
    public List<String> lifecycle() { return List.copyOf(lifecycle); }
    public List<String> deliveries() { return List.copyOf(deliveries); }
}
