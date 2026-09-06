package cn.ningbingjian.learnjava.ioc.lesson002.application;

/** 应用层需要的通知能力，由应用层定义；不包含邮件或短信厂商细节。 */
public interface Notifier {
    void send(String message);
}
