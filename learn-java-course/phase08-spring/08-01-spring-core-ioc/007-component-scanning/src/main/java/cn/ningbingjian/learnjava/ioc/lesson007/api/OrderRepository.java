package cn.ningbingjian.learnjava.ioc.lesson007.api;

public interface OrderRepository {
    boolean exists(String orderId);
}
