# 修订记录（结构 / 规范级变更）

> 只记「结构、规范」级别的改动；正文内容改动不在此列。
> 格式：`YYYY-MM-DD 一句话描述（原因）`

- 2026-09-05 确立六卷结构、写作规范（writing-guide）、章节模板；否决「单主线贯穿项目」，改为每卷卷末独立项目；补中间件缺口（Netty/ES/ShardingSphere/XXL-Job 等）。
- 2026-09-05 骨架期：README 课程地图、todo、ch00 可跑 demo（JDK21 + Maven Wrapper）落地。
- 2026-09-05 **粒度升级：六卷 → 7 部 × 25 本主线书**。理由：原「每卷一行」把可独立成书的主题（Redis/并发/JVM/Spring…）压缩成小章，深度不足。新标准：能撑起专著的主题就自成一本书（8–15 章、独立目录树与收官实验）。新增 CURRICULUM.md 总地图；每书配 README 书地图；旧卷结构移入 docs/_archive/ 留档。
- 2026-09-05 书目落序：书目录重命名 `book-<slug>` → `book-NN-<slug>`（NN=部内阅读序号，按 CURRICULUM 行序锁定，文件树排序即阅读顺序）。CURRICULUM 链接、各书「关联」引用、demo 分组 `demos/book-NN-<书slug>`、模板/写作规范/目录速查全量同步；todo 销掉「Part III 顺序待定」。
- 2026-09-05 《Spring 框架原理》章目录 12→14 补缺：新增「容器架构地图+refresh 导览」「Bean 作用域与单例并发」「Environment 与属性解析」三章；事务章补「事务抽象与传播行为」、条件装配章并进 `@Enable*` 编程模型、生命周期章显式含 FactoryBean；CURRICULUM 章数 13→14 对齐（原 12 章与声明 13 不符为骨架期遗留）。

