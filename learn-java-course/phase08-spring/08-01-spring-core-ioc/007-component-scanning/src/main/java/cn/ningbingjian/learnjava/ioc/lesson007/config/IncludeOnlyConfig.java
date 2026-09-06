package cn.ningbingjian.learnjava.ioc.lesson007.config;

import cn.ningbingjian.learnjava.ioc.lesson007.app.AppComponents;
import cn.ningbingjian.learnjava.ioc.lesson007.app.delivery.ConsoleNotifier;
import cn.ningbingjian.learnjava.ioc.lesson007.app.utility.PlainHelper;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AppComponents.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {ConsoleNotifier.class, PlainHelper.class}))
public class IncludeOnlyConfig {
}
