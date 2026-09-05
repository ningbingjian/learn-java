# demos/ · 每章最小可跑示例

> 每个 demo 对应某本书的某一章，代码与正文同库共存、**真实可跑**。
> 规范见 [docs/meta/writing-guide.md](../docs/meta/writing-guide.md) 第 5 节「代码与真实可跑规则」。

## 组织方式

```
demos/
├── README.md                ← 本文件
└── book-01-env-toolchain/      ← 按「书」分组（目录名镜像 docs/part-0X 的书目录 book-NN-<slug>）
    └── ch00-hello/          ← 按章命名（章号与书内 chNN 对齐）
        ├── pom.xml          ← 独立 Maven 工程（自带 ./mvnw）
        ├── README.md        ← 怎么跑、预期输出、验收
        └── src/…
```

## 约定

- 每个 demo 一个独立 Maven 工程，自带 `./mvnw`，不依赖机器全局 Maven 版本。
- 语言版本用 `pom.xml` 的 `<maven.compiler.release>` 锁死：正文讲到该版本特性才开，没讲到的别偷用。
- 覆盖到某本书/章时，登记进对应书的 README（书地图）；目前仅 `book-01-env-toolchain` 的 ch00 就绪，其余随正文写作补齐（避免空目录）。

## 从零复制一个新 demo

1. 复制 `demos/book-01-env-toolchain/ch00-hello/` 整个目录，改名 `demos/book-NN-<书slug>/chNN-主题/`（NN 与 docs 里该书目录一致）；
2. 改 `pom.xml` 的 `artifactId` 与 `<maven.compiler.release>`；
3. 换掉 `src/main/java` 与 `src/test/java` 里的包名和类。
