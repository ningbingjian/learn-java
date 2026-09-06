package cn.ningbingjian.learnjava.ioc.lesson008;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RouteNotifier {
    private final String channel;
    private final DeliveryLog log;
    private String prefix = "";
    private List<String> recipients = List.of();
    private Map<String, String> headers = Map.of();
    private boolean ready;

    public RouteNotifier(String channel, DeliveryLog log) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.log = Objects.requireNonNull(log, "log");
    }

    public static RouteNotifier createStatic(String channel, DeliveryLog log) {
        return new RouteNotifier(channel, log);
    }

    public void setPrefix(String prefix) { this.prefix = Objects.requireNonNull(prefix, "prefix"); }
    public void setRecipients(List<String> recipients) { this.recipients = List.copyOf(recipients); }
    public void setHeaders(Map<String, String> headers) { this.headers = new LinkedHashMap<>(headers); }

    public void initialize() {
        if (recipients.isEmpty()) { throw new IllegalStateException("recipients must not be empty"); }
        ready = true;
        log.recordLifecycle("init:" + channel);
    }

    public void send(String orderId) {
        if (!ready) { throw new IllegalStateException("notifier is not ready"); }
        log.recordDelivery(channel + " " + prefix + " order=" + Objects.requireNonNull(orderId, "orderId")
                + " -> " + recipients + " headers=" + headers);
    }

    public void shutdown() {
        ready = false;
        log.recordLifecycle("close:" + channel);
    }

    public String channel() { return channel; }
    public String prefix() { return prefix; }
    public List<String> recipients() { return recipients; }
    public Map<String, String> headers() { return Map.copyOf(headers); }
    public DeliveryLog log() { return log; }
    public boolean ready() { return ready; }
}
