package cn.ningbingjian.learnjava.ioc.lesson002.assembly;

import java.lang.reflect.Field;
import java.util.Objects;

import cn.ningbingjian.learnjava.ioc.lesson002.application.Notifier;
import cn.ningbingjian.learnjava.ioc.lesson002.comparison.FieldOrderNotificationService;

/** 固定目标类与字段的教学工具，不扫描注解，也不实现容器。 */
public final class ManualFieldInjector {
    private ManualFieldInjector() {
    }

    public static void inject(FieldOrderNotificationService target, Notifier notifier) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(notifier, "notifier");
        try {
            Field field = FieldOrderNotificationService.class.getDeclaredField("notifier");
            field.setAccessible(true);
            field.set(target, notifier);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("cannot write the demonstration field", error);
        }
    }
}
