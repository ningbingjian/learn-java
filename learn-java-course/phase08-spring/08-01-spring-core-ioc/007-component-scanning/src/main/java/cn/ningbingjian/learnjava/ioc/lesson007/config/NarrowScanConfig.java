package cn.ningbingjian.learnjava.ioc.lesson007.config;

import cn.ningbingjian.learnjava.ioc.lesson007.app.service.OrderNotificationService;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = OrderNotificationService.class)
public class NarrowScanConfig {
}
