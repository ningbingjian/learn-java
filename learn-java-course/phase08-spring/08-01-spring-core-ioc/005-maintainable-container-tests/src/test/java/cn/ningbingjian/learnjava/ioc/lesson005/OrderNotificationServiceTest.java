package cn.ningbingjian.learnjava.ioc.lesson005;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderNotificationServiceTest {
    @Test
    void acceptedOrderSendsExactlyOneExpectedMessage() {
        var messages = new ArrayList<String>();
        var service = new OrderNotificationService(messages::add);

        service.notifyAccepted("O-001");

        assertEquals(List.of("order=O-001 accepted"), messages);
    }

    @Test
    void blankOrderIdIsRejectedBeforeAnyNotification() {
        var messages = new ArrayList<String>();
        var service = new OrderNotificationService(messages::add);

        assertThrows(IllegalArgumentException.class, () -> service.notifyAccepted(" "));

        assertTrue(messages.isEmpty());
    }

    @Test
    void nullOrderIdIsRejectedBeforeAnyNotification() {
        var messages = new ArrayList<String>();
        var service = new OrderNotificationService(messages::add);

        assertThrows(NullPointerException.class, () -> service.notifyAccepted(null));

        assertTrue(messages.isEmpty());
    }

    @Test
    void channelFailureIsPropagatedToTheCaller() {
        var channelFailure = new IllegalStateException("simulated channel failure");
        Notifier failingNotifier = message -> { throw channelFailure; };
        var service = new OrderNotificationService(failingNotifier);

        var actual = assertThrows(IllegalStateException.class, () -> service.notifyAccepted("O-001"));

        assertSame(channelFailure, actual);
    }

    @Test
    void mandatoryCollaboratorCannotBeNull() {
        assertThrows(NullPointerException.class, () -> new OrderNotificationService(null));
    }
}
