# 知序 LabFlow：校园实验室预约与审批平台

面向《Java语言及网络编程》课程的 Spring Boot 项目。学生按节次申请实验室，教师审批，管理员进行检修排期和周使用率统计。待审申请可以重叠；实际占用在审批事务中一次性写入，冲突时整段不生效。

## 从源码运行

准备 JDK 17 和 Maven 3.9，在仓库根目录执行：

```sh
mvn package
java -jar target/labflow.jar
```

浏览器打开 `http://127.0.0.1:18186`。默认使用本机 H2 文件数据库，数据保存在 `data/`；首次启动会生成示例实验室和周课表。Vue 3 和图标随静态资源提供，不需要 Node。首次 Maven 构建需要下载依赖。仓库提供源码，不提供在线预约服务。

访客可直接查看周课表。登录窗口提供学生 `student1`、`student2`，教师 `teacher1`、`teacher2` 及管理员 `admin`。本地演示口令统一为 `LabDemo123`；这些是虚构的示例身份，不用于真实账户或公网部署。

## 复现一次审批冲突

1. 选择同一间实验室、同一未来日期。两位学生分别申请第 3 至 4 节和第 4 至 5 节，使两份申请同时待审。
2. 教师先批准第一份，再尝试批准第二份。系统应指出第 4 节冲突；第二份申请仍为待审，第 5 节没有被占用。
3. 学生取消已批准的第一份申请，第 3 至 4 节释放，同日其他检修记录不受影响。

## 验证与 AI 工程化材料

| 入口 | 内容 |
| --- | --- |
| [Java 业务代码](src/main/java/edu/campus/lab) | 排期、事务、HTTP 接口和角色权限 |
| [项目 Skill](.agents/skills/labflow-acceptance/SKILL.md) | 任务输入、节次规约、评测集和人工审批边界 |
| [规格测试](src/test/java/edu/campus/lab/BookingWindowSpecTest.java) | 8 组节次边界用例，不启动数据库 |
| [集成测试](src/test/java/edu/campus/lab/PlatformTest.java) | 10 项并发、回滚、权限和业务测试 |
| [复验记录](verification/AI工程化复验.md) | 交付实现与临时故障对照的逐项结果 |

`mvn test` 运行全套测试，默认用隔离的 H2 内存库。只验证 8 组边界用例可运行 `mvn "-Dtest=BookingWindowSpecTest" test`。MySQL 配置读取环境变量 `LAB_DB_URL`、`LAB_DB_USER`、`LAB_DB_PASSWORD`；如需 MySQL 集成测试，仅使用专门的 `labflow_test` 空库，测试会重建该库内的项目表。

前端库随附各自的 [Vue 许可](src/main/resources/static/assets/vue-LICENSE.txt) 和 [Lucide 许可](src/main/resources/static/assets/lucide-LICENSE.txt)。
