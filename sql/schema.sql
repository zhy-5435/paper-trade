-- ============================================================
-- paper-trade 第一轮单库建表脚本（核心 12 张表）
-- 适用：MySQL 8.0.16+ / InnoDB / utf8mb4（CHECK 约束强制生效）
-- 特性：CREATE TABLE IF NOT EXISTS，可重复执行
-- 对应文档：docs/03-数据库设计.md
-- 说明：表名、字段、分片键(user_id)、时间字段按正式版一次到位，
--       后续接入 ShardingSphere 仅加路由配置，不改表结构
-- ============================================================

CREATE DATABASE IF NOT EXISTS stock_sim
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE stock_sim;

-- ------------------------------------------------------------
-- 1. sys_user 用户表（普通用户/管理员合一，role 区分）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
  id              BIGINT       NOT NULL COMMENT '主键，雪花ID',
  username        VARCHAR(50)  NOT NULL COMMENT '登录名',
  password        VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
  nickname        VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  phone           VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  email           VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  role            TINYINT      NOT NULL DEFAULT 1 COMMENT '角色：1普通用户 2运营管理员',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用 2注销',
  last_login_time DATETIME(3)  DEFAULT NULL COMMENT '最近登录时间',
  version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表（普通用户/管理员合一）';

-- ------------------------------------------------------------
-- 2. stock_info 股票基础信息表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_info (
  id                BIGINT        NOT NULL COMMENT '主键，雪花ID',
  stock_code        VARCHAR(10)   NOT NULL COMMENT '股票代码',
  stock_name        VARCHAR(32)   NOT NULL COMMENT '股票名称',
  market            TINYINT       NOT NULL DEFAULT 1 COMMENT '市场：1沪 2深 3北（预留）',
  board_type        TINYINT       NOT NULL DEFAULT 1 COMMENT '板块：1主板 2创业板 3科创板 4北交所',
  issue_price       DECIMAL(18,4) NOT NULL COMMENT '发行价',
  price_limit_ratio DECIMAL(10,4) NOT NULL COMMENT '涨跌停幅度：0.10/0.20/0.05',
  lot_size          INT           NOT NULL DEFAULT 100 COMMENT '每手股数',
  status            TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1上市 0停牌 2退市',
  list_date         DATE          DEFAULT NULL COMMENT '上市日期',
  version           INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stock_code (stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='股票基础信息表';

-- ------------------------------------------------------------
-- 3. stock_quote 最新行情表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_quote (
  id               BIGINT        NOT NULL COMMENT '主键，雪花ID',
  stock_code       VARCHAR(10)   NOT NULL COMMENT '股票代码',
  trade_date       DATE          NOT NULL COMMENT '交易日（日切重置依据）',
  pre_close_price  DECIMAL(18,4) DEFAULT NULL COMMENT '昨收价',
  open_price       DECIMAL(18,4) DEFAULT NULL COMMENT '今开价',
  latest_price     DECIMAL(18,4) NOT NULL COMMENT '最新成交价',
  high_price       DECIMAL(18,4) DEFAULT NULL COMMENT '当日最高价',
  low_price        DECIMAL(18,4) DEFAULT NULL COMMENT '当日最低价',
  limit_up_price   DECIMAL(18,4) DEFAULT NULL COMMENT '涨停价',
  limit_down_price DECIMAL(18,4) DEFAULT NULL COMMENT '跌停价',
  total_volume     BIGINT        NOT NULL DEFAULT 0 COMMENT '当日累计成交量（股）',
  total_amount     DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '当日累计成交额',
  change_ratio     DECIMAL(10,4) NOT NULL DEFAULT 0.0000 COMMENT '涨跌幅',
  quote_time       DATETIME(3)   NOT NULL COMMENT '行情生成时间',
  create_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stock_code (stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='最新行情表';

-- ------------------------------------------------------------
-- 4. user_account 资金账户表（按 user_id 分库，与订单同库）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_account (
  id            BIGINT        NOT NULL COMMENT '主键，雪花ID',
  user_id       BIGINT        NOT NULL COMMENT '用户ID（分片键）',
  account_no    VARCHAR(32)   NOT NULL COMMENT '资金账号',
  currency      VARCHAR(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
  balance       DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '账户总余额',
  frozen_amount DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '冻结金额',
  total_asset   DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '最近总资产（日终刷新）',
  status        TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1正常 0冻结 2注销',
  version       INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  create_time   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_id (user_id),
  UNIQUE KEY uk_account_no (account_no),
  CONSTRAINT chk_account_amount CHECK (balance >= 0 AND frozen_amount >= 0 AND frozen_amount <= balance)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资金账户表';

-- ------------------------------------------------------------
-- 5. fund_flow 资金流水表（只追加，不可变）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fund_flow (
  id             BIGINT        NOT NULL COMMENT '主键，雪花ID',
  flow_no        VARCHAR(32)   NOT NULL COMMENT '流水号（幂等键）',
  user_id        BIGINT        NOT NULL COMMENT '用户ID（分片键）',
  account_id     BIGINT        NOT NULL COMMENT '账户ID',
  flow_type      TINYINT       NOT NULL COMMENT '类型：1入金 2出金 3冻结 4解冻 5买入扣款 6卖出入账 7手续费 8清算调整',
  change_balance DECIMAL(20,2) NOT NULL COMMENT '余额变动（正入负出）',
  change_frozen  DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '冻结额度变动',
  balance_before DECIMAL(20,2) NOT NULL COMMENT '变动前余额',
  balance_after  DECIMAL(20,2) NOT NULL COMMENT '变动后余额',
  frozen_before  DECIMAL(20,2) NOT NULL COMMENT '变动前冻结额',
  frozen_after   DECIMAL(20,2) NOT NULL COMMENT '变动后冻结额',
  biz_type       TINYINT       NOT NULL COMMENT '业务类型：1订单 2成交 3清算 4人工',
  biz_no         VARCHAR(32)   DEFAULT NULL COMMENT '关联订单号/成交号/批次号',
  remark         VARCHAR(200)  DEFAULT NULL COMMENT '摘要',
  create_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记账时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_no (flow_no),
  KEY idx_user_time (user_id, create_time),
  KEY idx_biz_no (biz_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资金流水表（只追加）';

-- ------------------------------------------------------------
-- 6. user_position 持仓表（按 user_id 分库，与订单同库）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_position (
  id             BIGINT        NOT NULL COMMENT '主键，雪花ID',
  user_id        BIGINT        NOT NULL COMMENT '用户ID（分片键）',
  stock_code     VARCHAR(10)   NOT NULL COMMENT '股票代码',
  total_qty      BIGINT        NOT NULL DEFAULT 0 COMMENT '总持仓股数',
  frozen_qty     BIGINT        NOT NULL DEFAULT 0 COMMENT '冻结股数（卖出委托）',
  today_buy_qty  BIGINT        NOT NULL DEFAULT 0 COMMENT '当日买入股数（T+1冻结，日终清零）',
  cost_amount    DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '持仓总成本（含买入费用）',
  avg_cost_price DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '平均持仓成本价',
  latest_price   DECIMAL(18,4) DEFAULT NULL COMMENT '最近成交价',
  market_value   DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '持仓市值（日终落库）',
  floating_pnl   DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '浮动盈亏（日终落库）',
  pnl_ratio      DECIMAL(10,4) NOT NULL DEFAULT 0.0000 COMMENT '盈亏比例',
  version        INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  create_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_stock (user_id, stock_code),
  CONSTRAINT chk_position_qty CHECK (total_qty >= 0 AND frozen_qty >= 0 AND today_buy_qty >= 0 AND frozen_qty <= total_qty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='持仓表';

-- ------------------------------------------------------------
-- 7. position_flow 持仓变动流水表（只追加，不可变）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS position_flow (
  id                BIGINT        NOT NULL COMMENT '主键，雪花ID',
  flow_no           VARCHAR(32)   NOT NULL COMMENT '流水号（幂等键）',
  user_id           BIGINT        NOT NULL COMMENT '用户ID（分片键）',
  stock_code        VARCHAR(10)   NOT NULL COMMENT '股票代码',
  change_type       TINYINT       NOT NULL COMMENT '类型：1买入 2卖出 3清算调整',
  change_qty        BIGINT        NOT NULL COMMENT '变动股数，正增负减',
  deal_price        DECIMAL(18,4) NOT NULL COMMENT '成交价格',
  total_qty_before  BIGINT        NOT NULL COMMENT '变动前持仓',
  total_qty_after   BIGINT        NOT NULL COMMENT '变动后持仓',
  cost_before       DECIMAL(20,2) NOT NULL COMMENT '变动前成本',
  cost_after        DECIMAL(20,2) NOT NULL COMMENT '变动后成本',
  biz_no            VARCHAR(32)   DEFAULT NULL COMMENT '关联成交号/订单号',
  create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记账时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_no (flow_no),
  KEY idx_user_stock_time (user_id, stock_code, create_time),
  KEY idx_biz_no (biz_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='持仓变动流水表（只追加）';

-- ------------------------------------------------------------
-- 8. trade_order 订单表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trade_order (
  id            BIGINT        NOT NULL COMMENT '主键，雪花ID',
  order_no      VARCHAR(32)   NOT NULL COMMENT '订单号（幂等键）',
  user_id       BIGINT        NOT NULL COMMENT '用户ID（分片键）',
  request_id    VARCHAR(32)   NOT NULL COMMENT '客户端幂等号（防重复提交）',
  trade_date    DATE          NOT NULL COMMENT '交易日（撤单/对账归属）',
  stock_code    VARCHAR(10)   NOT NULL COMMENT '股票代码',
  direction     TINYINT       NOT NULL COMMENT '方向：1买入 2卖出',
  order_type    TINYINT       NOT NULL DEFAULT 1 COMMENT '类型：1限价 2市价（预留）',
  entrust_price DECIMAL(18,4) NOT NULL COMMENT '委托价格',
  entrust_qty   BIGINT        NOT NULL COMMENT '委托数量',
  matched_qty   BIGINT        NOT NULL DEFAULT 0 COMMENT '已成交数量',
  frozen_amount DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '买单冻结资金（含预估手续费）',
  frozen_qty    BIGINT        NOT NULL DEFAULT 0 COMMENT '卖单冻结股数',
  fee_amount    DECIMAL(20,2) NOT NULL DEFAULT 0.00 COMMENT '累计实际手续费',
  status        TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1待撮合 2部分成交 3全部成交 4已撤单 5部分成交后撤单 6已过期',
  expire_time   DATETIME(3)   DEFAULT NULL COMMENT '有效期截止时间',
  reject_reason VARCHAR(200)  DEFAULT NULL COMMENT '校验拒绝或撤单原因',
  source        TINYINT       NOT NULL DEFAULT 1 COMMENT '委托终端',
  version       INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  create_time   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  UNIQUE KEY uk_user_request (user_id, request_id),
  KEY idx_user_status_time (user_id, status, create_time),
  KEY idx_stock_status (stock_code, status),
  CONSTRAINT chk_order_qty CHECK (entrust_qty > 0 AND matched_qty >= 0 AND matched_qty <= entrust_qty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易订单表';

-- ------------------------------------------------------------
-- 9. deal_record 成交记录表（正式版单库按月分表，单库阶段先建基础表）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS deal_record (
  id              BIGINT        NOT NULL COMMENT '主键，雪花ID',
  deal_no         VARCHAR(32)   NOT NULL COMMENT '成交号（幂等键）',
  stock_code      VARCHAR(10)   NOT NULL COMMENT '股票代码',
  deal_price      DECIMAL(18,4) NOT NULL COMMENT '成交价格',
  deal_qty        BIGINT        NOT NULL COMMENT '成交数量',
  deal_amount     DECIMAL(20,2) NOT NULL COMMENT '成交金额',
  buyer_order_no  VARCHAR(32)   NOT NULL COMMENT '买方订单号',
  seller_order_no VARCHAR(32)   NOT NULL COMMENT '卖方订单号',
  buyer_user_id   BIGINT        NOT NULL COMMENT '买方用户ID',
  seller_user_id  BIGINT        NOT NULL COMMENT '卖方用户ID',
  deal_seq        BIGINT        NOT NULL COMMENT '撮合全局序号',
  deal_time       DATETIME(3)   NOT NULL COMMENT '成交时间（时间分表键）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_deal_no (deal_no),
  KEY idx_buyer_time (buyer_user_id, deal_time),
  KEY idx_seller_time (seller_user_id, deal_time),
  KEY idx_stock_time (stock_code, deal_time),
  KEY idx_buyer_order_no (buyer_order_no),
  KEY idx_seller_order_no (seller_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成交记录表';

-- ------------------------------------------------------------
-- 10. settlement_batch 清算批次表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS settlement_batch (
  id               BIGINT      NOT NULL COMMENT '主键，雪花ID',
  batch_no         VARCHAR(32) NOT NULL COMMENT '批次号',
  trade_date       DATE        NOT NULL COMMENT '交易日（幂等重跑依据）',
  status           TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1执行中 2成功 3部分异常 4失败',
  total_user_count INT         NOT NULL DEFAULT 0 COMMENT '清算用户数',
  diff_count       INT         NOT NULL DEFAULT 0 COMMENT '异常笔数',
  start_time       DATETIME(3) DEFAULT NULL COMMENT '开始时间',
  end_time         DATETIME(3) DEFAULT NULL COMMENT '结束时间',
  remark           VARCHAR(200) DEFAULT NULL COMMENT '备注',
  create_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_batch_no (batch_no),
  UNIQUE KEY uk_trade_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='清算批次表';

-- ------------------------------------------------------------
-- 11. user_asset_snapshot 日终资产快照表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_asset_snapshot (
  id            BIGINT        NOT NULL COMMENT '主键，雪花ID',
  user_id       BIGINT        NOT NULL COMMENT '用户ID（预留分片键）',
  trade_date    DATE          NOT NULL COMMENT '快照日期',
  cash_balance  DECIMAL(20,2) NOT NULL COMMENT '现金余额',
  market_value  DECIMAL(20,2) NOT NULL COMMENT '持仓市值',
  total_asset   DECIMAL(20,2) NOT NULL COMMENT '总资产',
  cost_amount   DECIMAL(20,2) NOT NULL COMMENT '持仓成本',
  floating_pnl  DECIMAL(20,2) NOT NULL COMMENT '浮动盈亏',
  pnl_ratio     DECIMAL(10,4) NOT NULL COMMENT '盈亏比例',
  create_time   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '生成时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_date (user_id, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='日终资产快照表';

-- ------------------------------------------------------------
-- 12. reconcile_diff 对账异常表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reconcile_diff (
  id             BIGINT       NOT NULL COMMENT '主键，雪花ID',
  batch_no       VARCHAR(32)  NOT NULL COMMENT '所属清算批次',
  trade_date     DATE         NOT NULL COMMENT '交易日',
  user_id        BIGINT       NOT NULL COMMENT '异常用户ID',
  diff_type      TINYINT      NOT NULL COMMENT '类型：1资金差异 2持仓差异 3订单状态差异',
  biz_no         VARCHAR(32)  DEFAULT NULL COMMENT '关联单号',
  expect_value   VARCHAR(100) DEFAULT NULL COMMENT '期望值',
  actual_value   VARCHAR(100) DEFAULT NULL COMMENT '实际值',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1待处理 2已处理 3误报关闭',
  handle_user_id BIGINT       DEFAULT NULL COMMENT '处理人ID',
  handle_remark  VARCHAR(200) DEFAULT NULL COMMENT '处理说明',
  handle_time    DATETIME(3)  DEFAULT NULL COMMENT '处理时间',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_status (status),
  KEY idx_batch (batch_no),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对账异常表';
