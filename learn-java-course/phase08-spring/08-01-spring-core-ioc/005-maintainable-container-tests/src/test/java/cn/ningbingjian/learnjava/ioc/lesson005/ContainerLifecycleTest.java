package cn.ningbingjian.learnjava.ioc.lesson005;

import cn.ningbingjian.learnjava.ioc.lesson005.support.LifecycleProbe;
import cn.ningbingjian.learnjava.ioc.lesson005.support.ManagedRecordingNotifier;
import cn.ningbingjian.learnjava.ioc.lesson005.support.RecordingChannelConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class ContainerLifecycleTest {
    @Test
    void leavingTheResourceScopeInvokesTheConfiguredCloseCallbackOnce() {
        LifecycleProbe probe;
        ManagedRecordingNotifier recorder;
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, RecordingChannelConfig.class)) {
            probe = context.getBean(LifecycleProbe.class);
            recorder = context.getBean(ManagedRecordingNotifier.class);
            assertTrue(recorder.isOpen());
            assertEquals(List.of("created", "opened"), probe.events());
        }

        assertFalse(recorder.isOpen());
        assertEquals(List.of("created", "opened", "closed"), probe.events());
        assertEquals(1, probe.count("closed"));
    }

    @Test
    void businessFailureStillClosesTheContextAndItsManagedResource() {
        var context = new AnnotationConfigApplicationContext(BusinessConfig.class, RecordingChannelConfig.class);
        LifecycleProbe probe;
        ManagedRecordingNotifier recorder;
        try (context) {
            probe = context.getBean(LifecycleProbe.class);
            recorder = context.getBean(ManagedRecordingNotifier.class);
            assertThrows(IllegalArgumentException.class, () -> {
                try (context) {
                    context.getBean(OrderNotificationService.class).notifyAccepted(" ");
                }
            });
        }
        assertFalse(context.isActive());
        assertFalse(recorder.isOpen());
        assertTrue(recorder.messages().isEmpty());
        assertEquals(List.of("created", "opened", "closed"), probe.events());
    }

    @Test
    void applicationChannelConfigurationAlsoInitializesAndClosesItsOwnAdapter() {
        ManagedConsoleNotifier notifier;
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, ConsoleChannelConfig.class)) {
            notifier = context.getBean(ManagedConsoleNotifier.class);
            assertTrue(notifier.isOpen());
            context.getBean(OrderNotificationService.class).notifyAccepted("O-001");
        }

        assertFalse(notifier.isOpen());
        assertEquals(1, notifier.closeCount());
    }
}
