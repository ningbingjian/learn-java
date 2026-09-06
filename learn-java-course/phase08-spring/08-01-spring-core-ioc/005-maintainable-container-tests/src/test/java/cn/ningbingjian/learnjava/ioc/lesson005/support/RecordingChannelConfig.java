package cn.ningbingjian.learnjava.ioc.lesson005.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RecordingChannelConfig {
    @Bean
    public LifecycleProbe lifecycleProbe() {
        return new LifecycleProbe();
    }

    @Bean(initMethod = "open", destroyMethod = "close")
    public ManagedRecordingNotifier notifier(LifecycleProbe probe) {
        return new ManagedRecordingNotifier(probe);
    }
}
