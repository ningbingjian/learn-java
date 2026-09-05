package com.learnjava.volume1.ch00;

/**
 * 卷一 ch00 · 环境校验示例。
 *
 * <p>它只做三件事，用来确认你的工具链是通的：
 * 1. 打印运行它的 JDK / 操作系统信息；
 * 2. 打印一句问候；
 * 3. 提供一个可被 JUnit 断言的 {@link #message()}，作为「自测即自检」的第一个例子。
 *
 * <p>刻意不用任何高版本语法：它属于「装好环境」这一章，而不是讲特性的章。
 * 代码规范：见 docs/meta/writing-guide.md。
 */
public final class Hello {

    private Hello() {
        // 工具类，不允许实例化
    }

    /** 返回问候语——它是纯逻辑，便于测试，不依赖 System.out。 */
    public static String message() {
        return "Hello from learn-java!";
    }

    /** 打印一段环境信息 + 问候。 */
    public static void printEnvironment() {
        System.out.println("================ 环境校验 ================");
        System.out.println("Java 版本 : " + System.getProperty("java.version"));
        System.out.println("Java 厂商 : " + System.getProperty("java.vendor"));
        System.out.println("操作系统 : " + System.getProperty("os.name") + " "
                + System.getProperty("os.version"));
        System.out.println("-----------------------------------------");
        System.out.println(message());
        System.out.println("=========================================");
    }

    public static void main(String[] args) {
        printEnvironment();
    }
}
