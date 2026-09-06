package cn.ningbingjian.learnjava.ioc.lesson007.config;

import cn.ningbingjian.learnjava.ioc.lesson007.app.AppComponents;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AppComponents.class)
public class DefaultScanConfig {
}
