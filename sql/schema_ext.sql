-- ============================================================
-- paper-trade 扩展表建表脚本（4 张，随第二/四轮及选做项启用）
-- 适用：MySQL 8.0 / InnoDB / utf8mb4，可重复执行
-- 对应文档：docs/design/03-数据库设计.md 第五章
-- ============================================================

USE stock_sim;

-- ------------------------------------------------------------
-- 14. stock_price_tick 分时行情快照表（选做：行情分时数据）
--     数据量增长快，正式环境按 tick_time 月分表并冷归档
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_price_tick (
  id         BIGINT        NOT NULL COMMENT '主键，雪花ID',
  stock_code VARCHAR(10)   NOT NULL COMMENT '股票代码',
  price      DECIMAL(18,4) NOT NULL COMMENT '快照价格',
  volume     BIGINT        NOT NULL DEFAULT 0 COMMENT '区间成交量（股）',
  amount     DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '区间成交额',
  tick_time  DATETIME(3)   NOT NULL COMMENT '快照时间（时间分表键）',
  PRIMARY KEY (id),
  KEY idx_code_time (stock_code, tick_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分时行情快照表';

-- ------------------------------------------------------------
-- 15. order_status_log 订单状态变更日志
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_status_log (
  id          BIGINT      NOT NULL COMMENT '主键，雪花ID',
  order_no    VARCHAR(32) NOT NULL COMMENT '订单号',
  from_status TINYINT     DEFAULT NULL COMMENT '原状态',
  to_status   TINYINT     NOT NULL COMMENT '新状态',
  change_type TINYINT     NOT NULL COMMENT '变更类型：1撮合 2用户撤单 3过期撤单 4清算',
  operator_id BIGINT      DEFAULT NULL COMMENT '操作人ID（系统操作为空）',
  remark      VARCHAR(200) DEFAULT NULL COMMENT '备注',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单状态变更日志';

-- ------------------------------------------------------------
-- 16. risk_rule 风控规则配置表（选做：基础风控）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS risk_rule (
  id              BIGINT        NOT NULL COMMENT '主键，雪花ID',
  rule_code       VARCHAR(32)   NOT NULL COMMENT '规则编码',
  rule_name       VARCHAR(50)   NOT NULL COMMENT '规则名称',
  rule_type       TINYINT       NOT NULL COMMENT '类型：1单笔限额 2单日累计 3单股持仓占比',
  threshold_value DECIMAL(20,4) NOT NULL COMMENT '阈值（金额类单位元；占比类为小数，如0.2000，4位精度对齐比率约定）',
  status          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_rule_code (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控规则配置表';

-- ------------------------------------------------------------
-- 17. sys_operation_log 系统操作审计日志
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_operation_log (
  id             BIGINT      NOT NULL COMMENT '主键，雪花ID',
  operator_id    BIGINT      NOT NULL COMMENT '操作人ID',
  operator_name  VARCHAR(50) DEFAULT NULL COMMENT '操作人名称',
  operation_type TINYINT     NOT NULL COMMENT '类型：1登录 2股票维护 3行情管理 4差错处理 5其他',
  target_type    VARCHAR(32) DEFAULT NULL COMMENT '操作对象类型',
  target_id      VARCHAR(32) DEFAULT NULL COMMENT '操作对象ID',
  detail         TEXT        COMMENT '变更明细（JSON）',
  ip             VARCHAR(45) DEFAULT NULL COMMENT '操作IP',
  create_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_operator_time (operator_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统操作审计日志';
