package cn.ningbingjian.learnjava.ioc.lesson008;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

@Configuration(proxyBeanMethods = false)
@ImportResource("classpath:lesson008/xml-basics.xml")
public class ImportXmlConfig {
}
