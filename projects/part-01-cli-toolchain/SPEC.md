# SPEC · Part I 收官项目 · 里程碑①：自写 JSON 解析器与格式化器（JsonParser）

> 状态：🟡 规格已起草（2026-09-05）· 尚未动工
> 所属：[Part I 语言地基 收官项目（CLI 文档工具链）](README.md) 的第一个里程碑
> 旧版（卷结构）文档已归档：[docs/_archive](../../docs/_archive/)

## 1. 为什么是这个项目

JSON 是「每个 Java 程序员都在用、却几乎没人写过」的格式。自己写一遍解析器，能把卷一的武器全部用上，而且对错**可客观判定**（用标准 JSON 测例对拍即可），不需要别人打分。

## 2. 需求（做什么）

纯 JDK 命令行程序，提供两个方向的命令：

```
java -cp ... JsonParser parse <file.json>          # 解析并打印对象模型树
java -cp ... JsonParser format <file.json>         # 重新格式化输出（缩进/紧凑）
java -cp ... JsonParser minify <file.json>         # 压缩成单行
```

- 支持完整 JSON 语法：`null / true / false`、数字（整数/小数/指数/负数）、字符串（含转义 `\" \\ \/ \b \f \n \r \t \uXXXX`）、数组、对象（含嵌套、空容器）。
- 解析结果用**自建的类结构**表达（`JsonNull/JsonBool/JsonNumber/JsonString/JsonArray/JsonObject`），而不是直接扔回 `Map/List` 了事（那会绕开你该练的建模）。
- 解析出错时给出**友好报错**：位置（行/列）、期望什么、实际遇到什么，例如 `line 3, col 7: expected ':' after object key, got 'x'`。
- `format` 输出标准缩进的可读 JSON；`minify` 输出紧凑单行。`format(parse(x))` 必须仍能被本程序自己重新解析成功（自洽性）。

## 3. 明确不做（范围边界）

- 不做 JSON→Java 对象的反序列化成任意 POJO（那是后面卷的序列化框架做的事）。
- 不做数字的 BigDecimal 精度策略大讨论（先 `BigDecimal` 存，注释里留 `[深潜]`）。
- 不做性能优化与 benchmark（那是卷二 JMH 的事）。

## 4. 武器自限（不许用的东西）

- 第三方库一律禁用（包括任何现成 JSON 库如 Jackson/Gson、以及 commons 工具）。只准 JDK 标准库 + JUnit。
- 不许 `System.out` 满天飞：解析逻辑与输出逻辑分离，便于测试。
- CLI 即可，不许上 Spring/网络/数据库。

## 5. 验收标准（做到才叫完成）

1. **对拍测试全绿**：内置一组测试用例（`src/test/resources/json/`），含合法样例与**非法样例**；对合法样例断言解析树与格式化输出，对非法样例断言抛出的异常信息含行/列。
2. **地狱样例 ×5**：手写 5 个刁钻输入能正确处理——
   - 深层嵌套（如 100 层数组，递归深度用显式栈或接受 JDK 栈深的取舍写明）
   - 极端转义与 `\u` 代理对
   - 数字边界（`-0`、`1e400`、极小小数）
   - 字符串里的控制字符
   - 尾随垃圾（合法 JSON 后跟多余字符必须报错）
3. **自洽性**：`format(parse(x))` 再 parse 通过（对全部合法样例）。
4. 出错信息含**行列号**且能复现。
5. 项目有 `README.md`：编译/运行命令、结构说明、一份「我踩过的坑与取舍」小结（≥3 条，这是写给自己看的资产）。

## 6. 拆解建议（动工顺序）

1. 先用「递归下降」手写 `JsonParser`（字符串→树）：值→对象→数组→字符串→数字 的语法函数。
2. 写 `JsonValue` 类型族 + `toString`。
3. 错误处理：带位置信息的异常（`JsonParseException`）。
4. `formatter`：树→可读/紧凑文本。
5. 对拍用例 + JUnit 判分器。

## 7. 留下的问题（给未来的自己）

- `[深潜]` 大数据量/流式解析（`JsonReader` 增量模型）——以后对比 Jackson 源码时回来补。
- `[待证]` 递归下降 vs 显式栈：100 层嵌套在默认栈深下的行为，动工后实测记录。
- `[?]` 数字到底该用 `BigDecimal` 还是 `double` 语义才「对」？先把标准 JSON 的测例当作准绳。
