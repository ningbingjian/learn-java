package cn.ningbingjian.learnjava.ioc.lesson004;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BusinessConfig {
    @Bean
    public OrderNotificationService orderNotificationService(Notifier deliveryChannel) {
        return new OrderNotificationService(deliveryChannel);
    }

    @Bean
    public ReceiptNotificationService receiptNotificationService(Notifier deliveryChannel) {
        return new ReceiptNotificationService(deliveryChannel);
    }
}
