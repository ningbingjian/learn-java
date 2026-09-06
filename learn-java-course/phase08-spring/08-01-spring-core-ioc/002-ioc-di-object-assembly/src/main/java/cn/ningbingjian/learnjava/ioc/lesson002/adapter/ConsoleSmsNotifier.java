package cn.ningbingjian.learnjava.ioc.lesson002.adapter;

import cn.ningbingjian.learnjava.ioc.lesson002.application.Notifier;

/** 只打印消息，不发送真实短信。 */
public final class ConsoleSmsNotifier implements Notifier {
    @Override
    public void send(String message) {
        System.out.println("SMS " + message);
    }
}
