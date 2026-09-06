package cn.ningbingjian.learnjava.ioc.lesson002;

import java.util.List;

import cn.ningbingjian.learnjava.ioc.lesson002.adapter.ConsoleEmailNotifier;
import cn.ningbingjian.learnjava.ioc.lesson002.adapter.ConsoleSmsNotifier;
import cn.ningbingjian.learnjava.ioc.lesson002.application.OrderNotificationService;
import cn.ningbingjian.learnjava.ioc.lesson002.assembly.ManualFieldInjector;
import cn.ningbingjian.learnjava.ioc.lesson002.assembly.NotificationLocator;
import cn.ningbingjian.learnjava.ioc.lesson002.comparison.ConcreteDependencyService;
import cn.ningbingjian.learnjava.ioc.lesson002.comparison.FieldOrderNotificationService;
import cn.ningbingjian.learnjava.ioc.lesson002.comparison.LocatorOrderNotificationService;
import cn.ningbingjian.learnjava.ioc.lesson002.comparison.SetterOrderNotificationService;
import cn.ningbingjian.learnjava.ioc.lesson002.runtime.OrderEventDispatcher;

public final class DemoApplication {
    private DemoApplication() {
    }

    public static void main(String[] args) {
        String mode = args.length == 0 ? "constructor-email" : args[0];
        switch (mode) {
            case "constructor-email" -> new OrderNotificationService(new ConsoleEmailNotifier())
                    .notifyAccepted("O-001");
            case "constructor-sms" -> new OrderNotificationService(new ConsoleSmsNotifier())
                    .notifyAccepted("O-001");
            case "setter" -> runSetter();
            case "field" -> runField();
            case "locator" -> runLocator();
            case "concrete" -> new ConcreteDependencyService(new ConsoleEmailNotifier())
                    .notifyAccepted("O-001");
            case "callback" -> runCallback();
            default -> throw new IllegalArgumentException(
                    "mode must be constructor-email, constructor-sms, setter, field, locator, concrete or callback");
        }
    }

    private static void runSetter() {
        SetterOrderNotificationService service = new SetterOrderNotificationService();
        try {
            service.notifyAccepted("O-001");
        } catch (IllegalStateException error) {
            System.out.println("before-config=" + error.getMessage());
        }
        service.setNotifier(new ConsoleEmailNotifier());
        service.notifyAccepted("O-001");
        service.setNotifier(new ConsoleSmsNotifier());
        service.notifyAccepted("O-002");
    }

    private static void runField() {
        FieldOrderNotificationService service = new FieldOrderNotificationService();
        try {
            service.notifyAccepted("O-001");
        } catch (IllegalStateException error) {
            System.out.println("before-injection=" + error.getMessage());
        }
        ManualFieldInjector.inject(service, new ConsoleEmailNotifier());
        service.notifyAccepted("O-001");
    }

    private static void runLocator() {
        NotificationLocator locator = new NotificationLocator();
        LocatorOrderNotificationService service = new LocatorOrderNotificationService(locator);
        try {
            service.notifyAccepted("O-001");
        } catch (IllegalStateException error) {
            System.out.println("before-register=" + error.getMessage());
        }
        locator.register(new ConsoleEmailNotifier());
        service.notifyAccepted("O-001");
        locator.register(new ConsoleSmsNotifier());
        service.notifyAccepted("O-002");
    }

    private static void runCallback() {
        OrderNotificationService service = new OrderNotificationService(new ConsoleEmailNotifier());
        OrderEventDispatcher dispatcher = new OrderEventDispatcher();
        dispatcher.dispatch(List.of("O-001", "O-002"), service::notifyAccepted);
    }
}
