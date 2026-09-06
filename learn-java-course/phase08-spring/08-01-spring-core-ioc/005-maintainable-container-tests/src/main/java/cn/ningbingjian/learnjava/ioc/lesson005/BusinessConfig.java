package cn.ningbingjian.learnjava.ioc.lesson005;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BusinessConfig {
    @Bean
    public OrderNotificationService orderNotificationService(Notifier notifier) {
        return new OrderNotificationService(notifier);
    }
}
