package cn.ningbingjian.learnjava.ioc.lesson005;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConsoleChannelConfig {
    @Bean(initMethod = "open", destroyMethod = "close")
    public ManagedConsoleNotifier notifier() {
        return new ManagedConsoleNotifier();
    }
}
