package cn.ningbingjian.learnjava.ioc.lesson008;

public final class NotifierFactory {
    public RouteNotifier create(String channel, DeliveryLog log) {
        return new RouteNotifier(channel, log);
    }
}
