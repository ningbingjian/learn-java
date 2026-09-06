package cn.ningbingjian.learnjava.ioc.lesson003;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AppConfig {
    @Bean(initMethod = "open", destroyMethod = "close")
    public ManagedConsoleNotifier notifier() {
        return new ManagedConsoleNotifier();
    }

    @Bean
    public OrderNotificationService orderNotificationService(Notifier notifier) {
        return new OrderNotificationService(notifier);
    }
}
