package cn.ningbingjian.learnjava.ioc.lesson006;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JavaConfig {
    @Bean
    public LifecycleProbe lifecycleProbe() {
        return new LifecycleProbe();
    }

    @Bean(name = "factoryNotifier", initMethod = "open", destroyMethod = "close")
    public Notifier notifier(LifecycleProbe probe) {
        var notifier = new TrackedNotifier("email", probe);
        notifier.setPrefix("[JAVA]");
        return notifier;
    }
}
