package cn.ningbingjian.learnjava.ioc.lesson002;

import java.util.ArrayList;
import java.util.List;

import cn.ningbingjian.learnjava.ioc.lesson002.application.Notifier;
import cn.ningbingjian.learnjava.ioc.lesson002.application.OrderNotificationService;
import cn.ningbingjian.learnjava.ioc.lesson002.assembly.ManualFieldInjector;
import cn.ningbingjian.learnjava.ioc.lesson002.assembly.NotificationLocator;
import cn.ningbingjian.learnjava.ioc.lesson002.comparison.FieldOrderNotificationService;
import cn.ningbingjian.learnjava.ioc.lesson002.comparison.LocatorOrderNotificationService;
import cn.ningbingjian.learnjava.ioc.lesson002.comparison.SetterOrderNotificationService;
import cn.ningbingjian.learnjava.ioc.lesson002.runtime.OrderEventDispatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyAssemblyTest {
    @Test
    void constructorRejectsMissingMandatoryDependencyImmediately() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> new OrderNotificationService(null));
        assertEquals("notifier", error.getMessage());
    }

    @Test
    void constructorUsesTheProvidedCollaboratorWithoutAContainer() {
        RecordingNotifier notifier = new RecordingNotifier();
        OrderNotificationService service = new OrderNotificationService(notifier);

        service.notifyAccepted("O-001");

        assertEquals(List.of("order=O-001 accepted"), notifier.messages);
    }

    @Test
    void setterObjectExistsBeforeItIsReadyAndWorksAfterConfiguration() {
        SetterOrderNotificationService service = new SetterOrderNotificationService();
        RecordingNotifier notifier = new RecordingNotifier();

        assertThrows(IllegalStateException.class, () -> service.notifyAccepted("O-001"));
        assertTrue(notifier.messages.isEmpty());
        service.setNotifier(notifier);
        service.notifyAccepted("O-001");
        assertEquals(List.of("order=O-001 accepted"), notifier.messages);
    }

    @Test
    void setterReplacementAffectsSubsequentCallsOnly() {
        SetterOrderNotificationService service = new SetterOrderNotificationService();
        RecordingNotifier first = new RecordingNotifier();
        RecordingNotifier second = new RecordingNotifier();
        service.setNotifier(first);
        service.notifyAccepted("O-001");
        service.setNotifier(second);
        service.notifyAccepted("O-002");

        assertEquals(List.of("order=O-001 accepted"), first.messages);
        assertEquals(List.of("order=O-002 accepted"), second.messages);
    }

    @Test
    void fieldDependencyNeedsAnExternalWriterBeforeUse() {
        FieldOrderNotificationService service = new FieldOrderNotificationService();
        RecordingNotifier notifier = new RecordingNotifier();
        assertThrows(IllegalStateException.class, () -> service.notifyAccepted("O-001"));

        ManualFieldInjector.inject(service, notifier);
        service.notifyAccepted("O-001");

        assertEquals(List.of("order=O-001 accepted"), notifier.messages);
    }

    @Test
    void injectedLocatorDoesNotGuaranteeThatTheActualServiceIsRegistered() {
        NotificationLocator locator = new NotificationLocator();
        LocatorOrderNotificationService service = new LocatorOrderNotificationService(locator);
        assertThrows(IllegalStateException.class, () -> service.notifyAccepted("O-001"));

        RecordingNotifier notifier = new RecordingNotifier();
        locator.register(notifier);
        service.notifyAccepted("O-001");
        assertEquals(List.of("order=O-001 accepted"), notifier.messages);
    }

    @Test
    void rebindingLocatorChangesLookupButNotAnAlreadyInjectedReference() {
        RecordingNotifier first = new RecordingNotifier();
        RecordingNotifier second = new RecordingNotifier();
        NotificationLocator locator = new NotificationLocator();
        locator.register(first);
        LocatorOrderNotificationService lookupService = new LocatorOrderNotificationService(locator);
        OrderNotificationService injectedService = new OrderNotificationService(locator.getNotifier());
        lookupService.notifyAccepted("O-001");
        locator.register(second);
        lookupService.notifyAccepted("O-002");
        injectedService.notifyAccepted("O-003");

        assertEquals(List.of("order=O-001 accepted", "order=O-003 accepted"), first.messages);
        assertEquals(List.of("order=O-002 accepted"), second.messages);
    }

    @Test
    void separateLocatorInstancesDoNotShareRegistrations() {
        NotificationLocator first = new NotificationLocator();
        NotificationLocator second = new NotificationLocator();
        RecordingNotifier notifier = new RecordingNotifier();
        first.register(notifier);
        new LocatorOrderNotificationService(first).notifyAccepted("O-001");

        assertThrows(IllegalStateException.class,
                () -> new LocatorOrderNotificationService(second).notifyAccepted("O-002"));
        assertEquals(List.of("order=O-001 accepted"), notifier.messages);
    }

    @Test
    void dispatcherCallsBusinessCallbackInEventOrder() {
        RecordingNotifier notifier = new RecordingNotifier();
        OrderNotificationService service = new OrderNotificationService(notifier);

        new OrderEventDispatcher().dispatch(List.of("O-001", "O-002"), service::notifyAccepted);

        assertEquals(List.of("order=O-001 accepted", "order=O-002 accepted"), notifier.messages);
    }

    private static final class RecordingNotifier implements Notifier {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void send(String message) {
            messages.add(message);
        }
    }
}
