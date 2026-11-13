# paper-trade 股票模拟交易系统

模拟 A 股交易规则的证券交易后端演示系统：全链路虚拟资金 + 模拟行情，无真实交易/支付对接。项目沿「单库基础业务 → 分布式架构」四轮路线演进，核心展示金融级数据一致性、撮合引擎与分布式/海量数据存储优化能力。

> **当前进度：第一轮（单库基础业务）已完成并通过集成验收** —— A1–A18 全过、资金/持仓/订单三方对账零差异、单元测试 147 绿。
> 第二/三/四轮（Redis、RocketMQ、分库分表与主从、清算对账与压测）为规划演进，**尚未实现**，详见 [06-开发路线图](docs/06-开发路线图.md)。

## 第一轮已实现

单库（MySQL 8）+ 内存撮合，无中间件。八大领域模块 + common 底座：

| 模块 | 能力 |
|------|------|
| auth / user | 注册自动开户、JWT 登录、管理端角色拦截、用户资料维护 |
| account | 入金/出金（requestId 幂等）、冻结/解冻、资金流水、余额与总资产查询 |
| position | 持仓与实时市值、内部增减仓/冻结解冻、T+1 可卖校验、持仓流水 |
| order | 下单校验链（涨跌停/整手/停牌/资金/持仓/T+1）、下单冻结、requestId 幂等、订单状态机 |
| match | 内存盘口撮合（afterCommit 触发、resting 挂单价成交、部分成交比例解冻、自成交/停牌跳过）、成交流水、deal_seq 全局单调、重启盘口重建 |
| settlement | 最小日切三步（过期撤单解冻 → 行情日切 → today_buy 清零），幂等 + 乐观锁 CAS 重试 |
| stock | 股票管理、行情查询与刷新（随机游走）、停牌/复牌/退市 |
| common | 统一返回与错误码、雪花 ID、交易日历、金额精度、traceId 链路、全局异常处理 |

**一致性要点**：requestId 幂等去重、乐观锁 CAS + 新事务重试、资金/持仓冻结解冻勾稽、流水不可变审计、三条对账 SQL（资金/持仓/订单）零差异。分层红线：match → order/account/position/stock，order 不反向依赖 match；分片键已预埋，OrderBook / DealNotify 接口为后续轮次保留。

## 快速开始

环境：JDK 21 · Maven 3.8+ · MySQL 8.0（本地）。

1. **初始化数据库**（核心 13 表 + 演示种子，均可重复执行）：
   ```sql
   CREATE DATABASE IF NOT EXISTS stock_sim DEFAULT CHARSET utf8mb4;
   ```
   依次执行 `sql/schema.sql`、`sql/seed.sql`。`sql/schema_ext.sql` 为后续轮次扩展表，第一轮可不执行。

2. **配置本地密钥**（`application-local.yml` 已被 `.gitignore` 排除，切勿提交真实密钥）。二选一：
   - 在项目根新建 `application-local.yml`：
     ```yaml
     spring:
       datasource:
         password: <你的 MySQL 密码>
     stocksim:
       jwt:
         secret: <至少 32 字节的随机密钥>
     ```
   - 或设置环境变量 `MYSQL_PASSWORD`、`JWT_SECRET`（`application.yml` 以占位符读取）。

3. **启动**：
   ```bash
   mvn spring-boot:run
   ```
   - 应用：`http://localhost:8080`
   - 接口文档（Knife4j）：`http://localhost:8080/doc.html`
   - 健康检查：`http://localhost:8080/actuator/health`

4. **演示账号**（种子）：`admin/admin123`（管理员）、`user01/123456`、`user02/123456`。user02 持初始持仓（600000×1000 + 000007×100）作撮合对手盘。

> 验收/冒烟时建议冻结行情刷新以保证确定性：`mvn spring-boot:run -Dspring-boot.run.arguments=--stocksim.quote.refresh-interval-ms=3600000`。

## 质量与验收

- **单元测试**：`mvn test` → 147 tests，0 failures / 0 errors / 0 skipped（含 settlement 日切 11 项）。
- **集成验收**：§7.3 A1–A18 全过；§7.4 资金/持仓/订单三条对账 SQL 零差异。
- **关键场景**：日切过期撤单解冻、行情 trade_date 推进与涨跌停重算、T+1 次日卖出闭环、零股卖出费用封顶、重启盘口重建与 deal_seq 不回退、管理端鉴权。
- **验收归档**：[08-第一轮集成验收记录](docs/08-第一轮集成验收记录.md)（含实测证据与最终对账快照）。

## 文档导航

| 文档 | 内容 |
|------|------|
| [01-需求与边界](docs/01-需求与边界.md) | 项目定位、角色、A股交易规则口径、业务边界、验收链路、非功能指标 |
| [02-架构设计](docs/02-架构设计.md) | 技术选型、分层架构、分布式技术落地、撮合引擎、部署与集群演进 |
| [03-数据库设计](docs/03-数据库设计.md) | 17 张表、分库分表、读写分离、一致性与高可用 |
| [04-核心模块设计](docs/04-核心模块设计.md) | 10 个领域模块职责、关键实现、模块间调用链 |
| [05-接口设计](docs/05-接口设计.md) | REST 端点清单、统一约定、错误码 |
| [06-开发路线图](docs/06-开发路线图.md) | 四轮开发计划、阶段简化口径与验收标准 |
| [07-第一轮开发规划](docs/07-第一轮开发规划.md) | 第一轮执行计划：现状盘点、口径速查、里程碑 WBS、验收用例与风险 |
| [08-第一轮集成验收记录](docs/08-第一轮集成验收记录.md) | 第一轮收官验收归档：A1–A18 结果、三条对账 SQL 零差异、单测与日切/重启证据 |
| [09-系统操作与前端引入评估](docs/09-系统操作与前端引入评估.md) | 手动全链路操作指南（curl/Knife4j）、第一轮交互限制、前端引入分阶段评估与一键演示脚本 |
| [附录-高性能撮合演进](docs/附录-高性能撮合演进.md) | 面试知识扩展，**不属于当前实现范围** |

## 脚本

- `sql/schema.sql`：核心 13 表 DDL（第一轮单库，`IF NOT EXISTS` 可重复执行）
- `sql/schema_ext.sql`：扩展 4 表 DDL（stock_price_tick / order_status_log / risk_rule / sys_operation_log，随轮次启用）
- `sql/seed.sql`：演示种子数据（5 只股票 + 行情初始化 + 管理员/测试用户 + user02 初始持仓与流水，`INSERT IGNORE` 可重复执行）

## 开发轮次

1. **单库基础业务**（无中间件，内存撮合）✅ 已完成并通过验收 → 2. **Redis / RocketMQ 接入** → 3. **分库分表与主从** → 4. **清算对账与压测**

## 技术栈

- **第一轮已用**：JDK 21 · Spring Boot 3.2.11 · MyBatis-Plus 3.5.7 · MySQL 8.0 · JJWT 0.12.6 · Knife4j 4.4.0 · Hutool 5.8 · spring-security-crypto（仅 BCrypt）
- **后续轮次规划**（尚未引入）：Redis 7 · RocketMQ 4.9 · ShardingSphere-JDBC 5.5