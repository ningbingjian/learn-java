package cn.ningbingjian.learnjava.ioc.lesson002.adapter;

import cn.ningbingjian.learnjava.ioc.lesson002.application.Notifier;

/** 只打印消息，不发送真实邮件。 */
public final class ConsoleEmailNotifier implements Notifier {
    @Override
    public void send(String message) {
        System.out.println("EMAIL " + message);
    }
}
