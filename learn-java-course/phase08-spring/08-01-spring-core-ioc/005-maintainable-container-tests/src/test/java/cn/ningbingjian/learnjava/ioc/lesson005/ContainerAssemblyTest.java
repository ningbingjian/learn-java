package cn.ningbingjian.learnjava.ioc.lesson005;

import cn.ningbingjian.learnjava.ioc.lesson005.support.LifecycleProbe;
import cn.ningbingjian.learnjava.ioc.lesson005.support.ManagedRecordingNotifier;
import cn.ningbingjian.learnjava.ioc.lesson005.support.RecordingChannelConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class ContainerAssemblyTest {
    @Test
    void realBusinessConfigurationUsesTheSelectedRecordingChannel() {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, RecordingChannelConfig.class)) {
            var recorder = context.getBean(ManagedRecordingNotifier.class);

            context.getBean(OrderNotificationService.class).notifyAccepted("O-001");

            assertEquals(List.of("order=O-001 accepted"), recorder.messages());
            assertEquals(1, context.getBeansOfType(Notifier.class).size());
            assertTrue(context.getBeansOfType(ManagedConsoleNotifier.class).isEmpty());
        }
    }

    @Test
    void repeatedLookupDoesNotConstructAnotherRecordingChannel() {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, RecordingChannelConfig.class)) {
            var first = context.getBean(Notifier.class);
            var second = context.getBean(Notifier.class);
            var probe = context.getBean(LifecycleProbe.class);

            assertSame(first, second);
            assertEquals(1, probe.count("created"));
            assertEquals(1, probe.count("opened"));
            assertEquals(0, probe.count("closed"));
        }
    }

    @Test
    void independentContextsHaveIndependentMessagesAndLifecycleProbes() {
        try (var first = new AnnotationConfigApplicationContext(BusinessConfig.class, RecordingChannelConfig.class);
             var second = new AnnotationConfigApplicationContext(BusinessConfig.class, RecordingChannelConfig.class)) {
            var firstRecorder = first.getBean(ManagedRecordingNotifier.class);
            var secondRecorder = second.getBean(ManagedRecordingNotifier.class);
            assertNotSame(firstRecorder, secondRecorder);
            assertNotSame(first.getBean(LifecycleProbe.class), second.getBean(LifecycleProbe.class));

            first.getBean(OrderNotificationService.class).notifyAccepted("O-001");
            assertTrue(secondRecorder.messages().isEmpty());
            second.getBean(OrderNotificationService.class).notifyAccepted("O-002");

            assertEquals(List.of("order=O-001 accepted"), firstRecorder.messages());
            assertEquals(List.of("order=O-002 accepted"), secondRecorder.messages());
            assertEquals(1, first.getBean(LifecycleProbe.class).count("created"));
            assertEquals(1, second.getBean(LifecycleProbe.class).count("created"));
        }
    }
}
