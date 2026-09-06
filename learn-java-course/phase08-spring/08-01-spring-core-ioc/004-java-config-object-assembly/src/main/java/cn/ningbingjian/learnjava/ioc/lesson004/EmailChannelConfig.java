package cn.ningbingjian.learnjava.ioc.lesson004;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EmailChannelConfig {
    @Bean(name = {"orderNotifier", "notificationChannel"})
    public Notifier emailNotifier() {
        return new ConsoleEmailNotifier();
    }
}
