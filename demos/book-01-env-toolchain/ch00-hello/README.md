# demos/book-01-env-toolchain/ch00-hello

Part I《开发环境与工具链》的预置可跑样例：校验 **JDK → Maven → 编译 → 测试** 整条工具链是否打通。对应书中 ch07「一键跑通带测试的工程」。

## 前提

- 已安装 JDK **21 及以上**。查看版本：`java -version`（本项目编译目标为 21）。
  - 本机若 `JAVA_HOME` 指向了旧 JDK（如 11），编译前先切到 21：
    `export JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home`
- 有网（首次运行需下载 Maven 发行版与依赖，由 `./mvnw` 自动完成）。

## 怎么跑

```bash
cd demos/book-01-env-toolchain/ch00-hello

# 一键跑测试（等价于：编译 + 跑 JUnit 自测）
./mvnw test

# 运行主程序，看环境信息 + 问候
./mvnw -q exec:java -Dexec.mainClass=com.learnjava.volume1.ch00.Hello
```

> `./mvnw` 是 Maven Wrapper：首次运行会下载 `pom.xml` 指定的 Maven 版本到本地缓存，
> 之后该仓库任何工程都用它，避免依赖你机器上那个过老的全局 Maven。
> 不用 Wrapper 的话，也可以全局 `mvn test`（要求 Maven ≥ 3.6.3）。

### 不装 Maven 的极简验证（可选）

只想确认代码能编译运行、不想等 Maven 下载时：

```bash
cd demos/book-01-env-toolchain/ch00-hello
javac -d out src/main/java/com/learnjava/volume1/ch00/Hello.java
java -cp out com.learnjava.volume1.ch00.Hello
```

## 预期输出

`./mvnw test` 应以绿色通过 2 个测试；运行主程序大致输出：

```
================ 环境校验 ================
Java 版本 : 21.x.x
Java 厂商 : ...
操作系统 : Mac OS X ...
-----------------------------------------
Hello from learn-java!
=========================================
```

## 验收标准

- [ ] `./mvnw test` 全绿（证明 编译 + JUnit 链路 OK）
- [ ] 运行主程序能打印出你的 JDK 版本（证明跑的是 21 而不是旧版）

## 本示例想表达的写作规则

1. 代码与文档**同库共存、真实可跑**——它不是截图，是文件。
2. **自测即自检**：`HelloTest` 就是 ch00 这一章的「判分器」。
3. 讲版本特性要显式：主程序刻意不用高版本语法，因为这一章的任务是「装环境」。
