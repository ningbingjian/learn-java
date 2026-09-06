package cn.ningbingjian.learnjava.ioc.lesson008;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;

public final class XmlContexts {
    private XmlContexts() { }

    public static GenericApplicationContext load(String resource) {
        var context = new GenericApplicationContext();
        new XmlBeanDefinitionReader(context).loadBeanDefinitions("classpath:lesson008/" + resource);
        return context;
    }
}
