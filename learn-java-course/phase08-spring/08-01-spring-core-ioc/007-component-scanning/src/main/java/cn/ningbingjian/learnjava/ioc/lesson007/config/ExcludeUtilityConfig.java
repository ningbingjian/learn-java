package cn.ningbingjian.learnjava.ioc.lesson007.config;

import cn.ningbingjian.learnjava.ioc.lesson007.app.AppComponents;
import cn.ningbingjian.learnjava.ioc.lesson007.app.utility.URLCodec;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AppComponents.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = URLCodec.class))
public class ExcludeUtilityConfig {
}
