package cn.ningbingjian.learnjava.ioc.lesson004;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Isolated comparison configuration: it does not register BusinessConfig. */
@Configuration(proxyBeanMethods = false)
public class TwoChannelsConfig {
    @Bean
    public ConsoleEmailNotifier orderChannel() {
        return new ConsoleEmailNotifier();
    }

    @Bean
    public ConsoleEmailNotifier auditChannel() {
        return new ConsoleEmailNotifier();
    }
}
