package cn.ningbingjian.learnjava.ioc.lesson008;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JavaConfig {
    @Bean
    public DeliveryLog deliveryLog() { return new DeliveryLog(); }

    @Bean(initMethod = "initialize", destroyMethod = "shutdown")
    public RouteNotifier notifier(DeliveryLog deliveryLog) {
        var notifier = new RouteNotifier("email", deliveryLog);
        notifier.setPrefix("[orders]");
        notifier.setRecipients(List.of("ops@example.test"));
        notifier.setHeaders(Map.of("region", "cn"));
        return notifier;
    }

    @Bean
    public OrderService orderService(RouteNotifier notifier) { return new OrderService(notifier); }
}
