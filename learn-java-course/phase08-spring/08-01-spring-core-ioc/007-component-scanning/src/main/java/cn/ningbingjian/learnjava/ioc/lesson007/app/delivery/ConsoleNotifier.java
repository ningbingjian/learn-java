package cn.ningbingjian.learnjava.ioc.lesson007.app.delivery;

import cn.ningbingjian.learnjava.ioc.lesson007.api.Notifier;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Single-threaded console example; no external message is sent. */
@Component("emailNotifier")
public final class ConsoleNotifier implements Notifier {
    private final List<String> messages = new ArrayList<>();

    @Override
    public void send(String message) {
        messages.add(message);
        System.out.println("EMAIL " + message);
    }

    public List<String> messages() {
        return List.copyOf(messages);
    }
}
