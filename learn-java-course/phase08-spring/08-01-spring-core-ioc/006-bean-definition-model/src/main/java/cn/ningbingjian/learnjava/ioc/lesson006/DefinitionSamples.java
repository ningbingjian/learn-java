package cn.ningbingjian.learnjava.ioc.lesson006;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.RootBeanDefinition;

public final class DefinitionSamples {
    private DefinitionSamples() {
    }

    public static RootBeanDefinition notifierDefinition(LifecycleProbe probe) {
        var definition = new RootBeanDefinition(TrackedNotifier.class);
        definition.setScope(BeanDefinition.SCOPE_SINGLETON);
        definition.setLazyInit(false);
        definition.getConstructorArgumentValues().addIndexedArgumentValue(0, "email");
        definition.getConstructorArgumentValues().addIndexedArgumentValue(1, probe);
        definition.getPropertyValues().add("prefix", "[MAIL]");
        definition.setInitMethodName("open");
        definition.setDestroyMethodName("close");
        definition.setResourceDescription("lesson006:manual/notifier");
        return definition;
    }

    public static RootBeanDefinition orderServiceDefinition() {
        var definition = new RootBeanDefinition(OrderNotificationService.class);
        definition.getConstructorArgumentValues()
                .addIndexedArgumentValue(0, new RuntimeBeanReference("notifier"));
        definition.setResourceDescription("lesson006:manual/orderService");
        return definition;
    }
}
