-- ============================================================
-- paper-trade 演示种子数据（可重复执行：固定 ID + INSERT IGNORE）
-- 前置：先执行 schema.sql
-- 账号：admin/admin123（管理员）、user01/123456、user02/123456（测试用户）
-- 说明：股票名称/价格为演示固定值，非实时数据；低价股保证 1 手 500~6000 元
--       user02 为初始持仓方（撮合对手盘）：600000 1000 股 + 000007 100 股，
--       today_buy_qty=0 保证即日可卖；同步注入持仓流水保证对账勾稽
-- ============================================================

USE stock_sim;

-- ------------------------------------------------------------
-- 1. 股票基础信息（覆盖 0.10/0.20/0.05 三种涨跌停幅度）
-- ------------------------------------------------------------
INSERT IGNORE INTO stock_info
  (id, stock_code, stock_name, market, board_type, issue_price, price_limit_ratio, lot_size, status, list_date)
VALUES
  (1, '600000', '浦发银行', 1, 1, 10.0000, 0.1000, 100, 1, '1999-11-10'),
  (2, '000001', '平安银行', 2, 1, 12.0000, 0.1000, 100, 1, '1991-04-03'),
  (3, '300059', '东方财富', 2, 2, 15.0000, 0.2000, 100, 1, '2010-03-19'),
  (4, '688981', '中芯国际', 1, 3, 60.0000, 0.2000, 100, 1, '2020-07-16'),
  (5, '000007', 'ST全新',   2, 1,  5.0000, 0.0500, 100, 1, '1992-05-07');

-- ------------------------------------------------------------
-- 2. 行情初始化：pre_close=latest=发行价，涨跌停四舍五入到分，
--    trade_date=执行当天；open/high/low 留 NULL 待首笔行情
-- ------------------------------------------------------------
INSERT IGNORE INTO stock_quote
  (id, stock_code, trade_date, pre_close_price, latest_price, limit_up_price, limit_down_price, quote_time)
SELECT id, stock_code, CURDATE(), issue_price, issue_price,
       ROUND(issue_price * (1 + price_limit_ratio), 2),
       ROUND(issue_price * (1 - price_limit_ratio), 2),
       NOW(3)
FROM stock_info;

-- ------------------------------------------------------------
-- 3. 账号（BCrypt 哈希，与运行时编码器一致）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_user (id, username, password, nickname, role, status) VALUES
  (1, 'admin',  '$2a$10$PI.YpRW7IuhKMeXjeCPW9urPNVhPqHQQXybyxY7x9r3R7iSO9w3Wu', '运营管理员', 2, 1),
  (2, 'user01', '$2a$10$m4UTAkED3d/FxihHM/nHDuaFJztrUB6pDF4m4B5hUcmSfKL1LL6rK', '测试用户01', 1, 1),
  (3, 'user02', '$2a$10$m4UTAkED3d/FxihHM/nHDuaFJztrUB6pDF4m4B5hUcmSfKL1LL6rK', '测试用户02', 1, 1);

-- ------------------------------------------------------------
-- 4. 测试用户资金账户（余额 0，验收时走入金链路；管理员不开户）
-- ------------------------------------------------------------
INSERT IGNORE INTO user_account (id, user_id, account_no) VALUES
  (1, 2, 'A0000000000000000002'),
  (2, 3, 'A0000000000000000003');

-- ------------------------------------------------------------
-- 5. 测试用户初始持仓（撮合验收的对手盘来源，见 docs/rounds/round1/01 R1）
--    user02 持有 600000 1000 股（成本=发行价）与 000007 100 股（零股卖出用例）
--    today_buy_qty=0：非当日买入，即日可卖
-- ------------------------------------------------------------
INSERT IGNORE INTO user_position
  (id, user_id, stock_code, total_qty, frozen_qty, today_buy_qty, cost_amount, avg_cost_price)
VALUES
  (1, 3, '600000', 1000, 0, 0, 10000.00, 10.0000),
  (2, 3, '000007',  100, 0, 0,   500.00,  5.0000);

-- 初始持仓同步落流水（change_type=3 清算调整，biz_no=SEED），保证 Σ流水=持仓 可对账
INSERT IGNORE INTO position_flow
  (id, flow_no, user_id, stock_code, change_type, change_qty, change_frozen,
   total_qty_before, total_qty_after, frozen_qty_before, frozen_qty_after,
   cost_before, cost_after, biz_no)
VALUES
  (1, 'SEED0000000000000001', 3, '600000', 3, 1000, 0, 0, 1000, 0, 0, 0.00, 10000.00, 'SEED'),
  (2, 'SEED0000000000000002', 3, '000007', 3,  100, 0, 0,  100, 0, 0, 0.00,   500.00, 'SEED');