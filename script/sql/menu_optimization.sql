-- ============================================================
-- Nexus-ERP 菜单结构优化迁移脚本
-- 模块: ERP / MES / AI
-- 策略: 拆分超载二级目录为多个职责清晰的二级目录(保持3层结构)
-- 约束: 仅改 sys_menu 结构(parent_id/order_num/新增M目录/重命名),
--       perms 权限码不变, 前端 component 路径不变, .vue 业务代码不变
-- 幂等: 可重复执行(新目录先 DELETE 再 INSERT; 现有记录 UPDATE 天然幂等)
--
-- ⚠️ 执行前务必备份:
--   CREATE TABLE sys_menu_bak_20260629        AS SELECT * FROM sys_menu;
--   CREATE TABLE sys_role_menu_bak_20260629   AS SELECT * FROM sys_role_menu;
--
-- 执行方式(中文 remark 需 utf8mb4 字符集, 否则报 "Incorrect string value"):
--   mysql -u root -proot --default-character-set=utf8mb4 ry-vue < script/sql/menu_optimization.sql
--   (ry-vue.sql 初始化脚本已同步修订为新结构, 新环境初始化后无需再执行本脚本)
--
-- 配套前端改动(需同步, 否则表单页高亮/列表跳转会失效):
--   - plus-ui/src/router/index.ts  (activeMenu 修订 + AI 静态路由块重构)
--   - 约18处组件 .vue 的 router.push 列表页跳转
-- ============================================================

-- ============================================================
-- 一、ERP 模块  (一级根: 2000 ERP系统)
-- ============================================================

-- ---------- 1.1 新增 14 个二级目录 (M) ----------
-- menu_id 2960-2973 已确认为空号
DELETE FROM `sys_menu` WHERE `menu_id` IN (2960,2961,2962,2963,2964,2965,2966,2967,2968,2969,2970,2971,2972,2973);

INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query_param`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_dept`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES
(2960,'工艺管理',    2000, 2,'tech',     NULL,NULL,1,0,'M','0','0','','cascader',   103,1,'2026-06-29 00:00:00',NULL,NULL,'工艺管理目录'),
(2961,'业务伙伴',    2000, 3,'partner',  NULL,NULL,1,0,'M','0','0','','peoples',    103,1,'2026-06-29 00:00:00',NULL,NULL,'业务伙伴目录'),
(2962,'制造资源',    2000, 4,'mfg',      NULL,NULL,1,0,'M','0','0','','tool',        103,1,'2026-06-29 00:00:00',NULL,NULL,'制造资源目录'),
(2963,'入库管理',    2000, 6,'inbound',  NULL,NULL,1,0,'M','0','0','','inbound',     103,1,'2026-06-29 00:00:00',NULL,NULL,'入库管理目录'),
(2964,'出库管理',    2000, 7,'outbound', NULL,NULL,1,0,'M','0','0','','outbound',    103,1,'2026-06-29 00:00:00',NULL,NULL,'出库管理目录'),
(2965,'库存管理',    2000, 8,'inventory',NULL,NULL,1,0,'M','0','0','','list',        103,1,'2026-06-29 00:00:00',NULL,NULL,'库存管理目录'),
(2966,'仓储策略',    2000,10,'wmRule',   NULL,NULL,1,0,'M','0','0','','setting',     103,1,'2026-06-29 00:00:00',NULL,NULL,'仓储策略目录'),
(2967,'销售商务',    2000,12,'soBiz',    NULL,NULL,1,0,'M','0','0','','documentation',103,1,'2026-06-29 00:00:00',NULL,NULL,'销售商务目录'),
(2968,'生产执行',    2000,14,'prdExec',  NULL,NULL,1,0,'M','0','0','','work-order', 103,1,'2026-06-29 00:00:00',NULL,NULL,'生产执行目录'),
(2969,'委外返工',    2000,15,'prdSub',   NULL,NULL,1,0,'M','0','0','','outsource',   103,1,'2026-06-29 00:00:00',NULL,NULL,'委外返工目录'),
(2970,'班次管理',    2000,17,'schShift', NULL,NULL,1,0,'M','0','0','','time',        103,1,'2026-06-29 00:00:00',NULL,NULL,'班次管理目录'),
(2971,'排班考勤',    2000,18,'schAttend',NULL,NULL,1,0,'M','0','0','','date',        103,1,'2026-06-29 00:00:00',NULL,NULL,'排班考勤目录'),
(2972,'采购执行',    2000,20,'puExec',   NULL,NULL,1,0,'M','0','0','','order',       103,1,'2026-06-29 00:00:00',NULL,NULL,'采购执行目录'),
(2973,'采购商务',    2000,21,'puBiz',    NULL,NULL,1,0,'M','0','0','','star',        103,1,'2026-06-29 00:00:00',NULL,NULL,'采购商务目录');

-- ---------- 1.2 重命名复用的二级目录 + 调整 order_num ----------
UPDATE `sys_menu` SET `menu_name`='物料主数据',    `order_num`= 1 WHERE `menu_id`=2001;  -- 原"基础数据"
UPDATE `sys_menu` SET `menu_name`='仓库设置',      `order_num`= 5 WHERE `menu_id`=2100;  -- 原"仓库管理"
UPDATE `sys_menu` SET `menu_name`='销售订单管理',  `order_num`=11 WHERE `menu_id`=2200;  -- 原"销售管理"
UPDATE `sys_menu` SET `menu_name`='生产计划',      `order_num`=13 WHERE `menu_id`=2300;  -- 原"生产管理"
UPDATE `sys_menu` SET `menu_name`='人员组织',      `order_num`=16 WHERE `menu_id`=2400;  -- 原"排班管理"
UPDATE `sys_menu` SET `menu_name`='采购申请',      `order_num`=19 WHERE `menu_id`=2500;  -- 原"采购管理"
UPDATE `sys_menu` SET                       `order_num`= 9 WHERE `menu_id`=2600;  -- "条码管理"名称不变,仅调序

-- ---------- 1.3 C 菜单 parent_id + order_num 调整 ----------

-- 基础数据(2001) 拆分: 留物料主数据 + 移出工艺/伙伴/资源
UPDATE `sys_menu` SET `parent_id`=2960,`order_num`=1 WHERE `menu_id`=2014;  -- BOM管理      → 工艺管理
UPDATE `sys_menu` SET `parent_id`=2960,`order_num`=2 WHERE `menu_id`=2900;  -- 工序管理      → 工艺管理
UPDATE `sys_menu` SET `parent_id`=2960,`order_num`=3 WHERE `menu_id`=2910;  -- 工艺路线      → 工艺管理
UPDATE `sys_menu` SET `parent_id`=2961,`order_num`=1 WHERE `menu_id`=2020;  -- 客户管理      → 业务伙伴
UPDATE `sys_menu` SET `parent_id`=2961,`order_num`=2 WHERE `menu_id`=2026;  -- 供应商管理    → 业务伙伴
UPDATE `sys_menu` SET `parent_id`=2962,`order_num`=1 WHERE `menu_id`=2033;  -- 车间管理      → 制造资源
UPDATE `sys_menu` SET `parent_id`=2962,`order_num`=2 WHERE `menu_id`=2034;  -- 工位管理      → 制造资源
UPDATE `sys_menu` SET `parent_id`=2962,`order_num`=3 WHERE `menu_id`=2035;  -- 序列号规则    → 制造资源
UPDATE `sys_menu` SET              `order_num`=3 WHERE `menu_id`=2032;  -- 计量单位 留2001, 原order6→3
UPDATE `sys_menu` SET              `order_num`=4 WHERE `menu_id`=2050;  -- 物料-供应商 留2001, 原order10→4
-- 留2001: 2002(1) 2008(2) 2032(3) 2050(4)

-- 仓库管理(2100) 拆分: 留仓库设置 + 移出入库/出库/库存; 条码管理收纳追溯
UPDATE `sys_menu` SET `parent_id`=2963,`order_num`=1 WHERE `menu_id`=2104;  -- 到货通知      → 入库管理
UPDATE `sys_menu` SET `parent_id`=2963,`order_num`=2 WHERE `menu_id`=2105;  -- 物料入库      → 入库管理
UPDATE `sys_menu` SET `parent_id`=2963,`order_num`=3 WHERE `menu_id`=2710;  -- 生产入库      → 入库管理
UPDATE `sys_menu` SET `parent_id`=2963,`order_num`=4 WHERE `menu_id`=2126;  -- 杂项入库      → 入库管理
UPDATE `sys_menu` SET `parent_id`=2964,`order_num`=1 WHERE `menu_id`=2107;  -- 出库管理      → 出库管理
UPDATE `sys_menu` SET `parent_id`=2964,`order_num`=2 WHERE `menu_id`=2190;  -- 生产领料      → 出库管理
UPDATE `sys_menu` SET `parent_id`=2964,`order_num`=3 WHERE `menu_id`=2120;  -- 杂项出库      → 出库管理
UPDATE `sys_menu` SET `parent_id`=2965,`order_num`=1 WHERE `menu_id`=2106;  -- 库存清单      → 库存管理
UPDATE `sys_menu` SET `parent_id`=2965,`order_num`=2 WHERE `menu_id`=2108;  -- 调拨管理      → 库存管理
UPDATE `sys_menu` SET `parent_id`=2965,`order_num`=3 WHERE `menu_id`=2114;  -- 盘点管理      → 库存管理
UPDATE `sys_menu` SET `parent_id`=2965,`order_num`=4 WHERE `menu_id`=2132;  -- 安全库存预警  → 库存管理
UPDATE `sys_menu` SET `parent_id`=2965,`order_num`=5 WHERE `menu_id`=2138;  -- 库存台账校验  → 库存管理
-- 留2100: 2101(1) 2102(2,隐藏) 2103(3,隐藏)

-- 条码管理(2600) 重组: 留条码/序列号/批次 + 收纳追溯; 移出上架/拣货/估值
UPDATE `sys_menu` SET `parent_id`=2966,`order_num`=1 WHERE `menu_id`=2616;  -- 上架规则      → 仓储策略
UPDATE `sys_menu` SET `parent_id`=2966,`order_num`=2 WHERE `menu_id`=2621;  -- 拣货规则      → 仓储策略
UPDATE `sys_menu` SET `parent_id`=2966,`order_num`=3 WHERE `menu_id`=2631;  -- 库存估值      → 仓储策略
UPDATE `sys_menu` SET `parent_id`=2600,`order_num`=3 WHERE `menu_id`=5000;  -- 序列号管理 留2600, 原order10→3
UPDATE `sys_menu` SET `parent_id`=2600,`order_num`=4 WHERE `menu_id`=5010;  -- 批次管理   留2600, 原order12→4
UPDATE `sys_menu` SET `parent_id`=2600,`order_num`=5 WHERE `menu_id`=5020;  -- 追溯查询   从2100移入2600
-- 留2600: 2601(1) 2610(2) 5000(3) 5010(4) 5020(5)

-- 销售管理(2200) 拆分: 留销售订单管理 + 移出商务
UPDATE `sys_menu` SET `parent_id`=2967,`order_num`=1 WHERE `menu_id`=2060;  -- 合同管理      → 销售商务
UPDATE `sys_menu` SET `parent_id`=2967,`order_num`=2 WHERE `menu_id`=2070;  -- 开票管理      → 销售商务
UPDATE `sys_menu` SET `parent_id`=2967,`order_num`=3 WHERE `menu_id`=2209;  -- 退货管理      → 销售商务
-- 留2200: 2201(1) 2202(2) 2203(3)

-- 生产管理(2300) 拆分: 留生产计划 + 移出执行/委外返工
UPDATE `sys_menu` SET `parent_id`=2968,`order_num`=1 WHERE `menu_id`=2303;  -- 生产工单      → 生产执行
UPDATE `sys_menu` SET `parent_id`=2968,`order_num`=2 WHERE `menu_id`=2304;  -- 工单报工      → 生产执行
UPDATE `sys_menu` SET `parent_id`=2968,`order_num`=3 WHERE `menu_id`=2810;  -- 工单资源分配  → 生产执行
UPDATE `sys_menu` SET `parent_id`=2969,`order_num`=1 WHERE `menu_id`=2144;  -- 委外订单      → 委外返工
UPDATE `sys_menu` SET `parent_id`=2969,`order_num`=2 WHERE `menu_id`=2150;  -- 返工订单      → 委外返工
UPDATE `sys_menu` SET              `order_num`=3 WHERE `menu_id`=2700;  -- 产能日历 留2300, 原order10→3
-- 留2300: 2301(1) 2302(2) 2700(3)

-- 排班管理(2400) 拆分: 留人员组织 + 移出班次/排班考勤
UPDATE `sys_menu` SET `parent_id`=2970,`order_num`=1 WHERE `menu_id`=2403;  -- 班次管理      → 班次管理
UPDATE `sys_menu` SET `parent_id`=2970,`order_num`=2 WHERE `menu_id`=2406;  -- 打卡规则      → 班次管理
UPDATE `sys_menu` SET `parent_id`=2970,`order_num`=3 WHERE `menu_id`=2407;  -- 节假日        → 班次管理
UPDATE `sys_menu` SET `parent_id`=2971,`order_num`=1 WHERE `menu_id`=2404;  -- 排班计划      → 排班考勤
UPDATE `sys_menu` SET `parent_id`=2971,`order_num`=2 WHERE `menu_id`=2405;  -- 考勤记录      → 排班考勤
-- 留2400: 2401(1) 2402(2)

-- 采购管理(2500) 拆分: 留采购申请(含部门请购单归位) + 移出执行/商务
UPDATE `sys_menu` SET `parent_id`=2500,`order_num`=2,`path`='deptRequisition' WHERE `menu_id`=2080;  -- 部门请购单 从2000根归位采购申请,改path避免与2501(requisition)冲突
UPDATE `sys_menu` SET `parent_id`=2972,`order_num`=1 WHERE `menu_id`=2510;  -- 采购订单      → 采购执行
UPDATE `sys_menu` SET `parent_id`=2972,`order_num`=2 WHERE `menu_id`=2520;  -- 采购退货      → 采购执行
UPDATE `sys_menu` SET `parent_id`=2973,`order_num`=1 WHERE `menu_id`=2540;  -- 采购合同      → 采购商务
UPDATE `sys_menu` SET `parent_id`=2973,`order_num`=2 WHERE `menu_id`=2530;  -- 供应商评估    → 采购商务 (含子报表2800自动跟随)
-- 留2500: 2501(1) 2080(2)


-- ============================================================
-- 二、MES 模块  (一级根: 4000 MES系统)
-- ============================================================

-- ---------- 2.1 新增 2 个二级目录 (M) ----------
DELETE FROM `sys_menu` WHERE `menu_id` IN (4150,4151);

INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query_param`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_dept`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES
(4150,'主数据',   4000,1,'md',NULL,'',1,0,'M','0','0','','tree', 103,1,'2026-06-29 00:00:00',NULL,NULL,'MES主数据目录'),
(4151,'生产订单', 4000,2,'wo',NULL,'',1,0,'M','0','0','','list', 103,1,'2026-06-29 00:00:00',NULL,NULL,'MES生产订单目录');

-- ---------- 2.2 现有二级目录 order_num 后移 ----------
UPDATE `sys_menu` SET `order_num`=3 WHERE `menu_id`=4001;  -- 生产执行
UPDATE `sys_menu` SET `order_num`=4 WHERE `menu_id`=4002;  -- 质量管理
UPDATE `sys_menu` SET `order_num`=5 WHERE `menu_id`=4003;  -- 设备管理
UPDATE `sys_menu` SET `order_num`=6 WHERE `menu_id`=4004;  -- 数据采集
UPDATE `sys_menu` SET `order_num`=7 WHERE `menu_id`=4005;  -- SPC分析

-- ---------- 2.3 C 菜单 parent_id + order_num 调整 ----------
-- 主数据(4150): 收纳原平铺的 md 三项 + 游离 MBOM
UPDATE `sys_menu` SET `parent_id`=4150,`order_num`=1 WHERE `menu_id`=4030;  -- 物料查询      (原parent4000)
UPDATE `sys_menu` SET `parent_id`=4150,`order_num`=2 WHERE `menu_id`=4031;  -- BOM查询        (原parent4000)
UPDATE `sys_menu` SET `parent_id`=4150,`order_num`=3 WHERE `menu_id`=4032;  -- 工艺路线查询   (原parent4000)
UPDATE `sys_menu` SET `parent_id`=4150,`order_num`=4 WHERE `menu_id`=2950;  -- MBOM管理 (原parent4001,order75→归位主数据)

-- 生产订单(4151): 从生产执行拆出工单/流转卡
UPDATE `sys_menu` SET `parent_id`=4151,`order_num`=1 WHERE `menu_id`=4010;  -- MES工单
UPDATE `sys_menu` SET `parent_id`=4151,`order_num`=2 WHERE `menu_id`=4040;  -- 流转卡

-- 生产执行(4001): 留派工/安灯/SN, 重排 order
UPDATE `sys_menu` SET `order_num`=1 WHERE `menu_id`=4060;  -- 派工管理 (原order3→1)
UPDATE `sys_menu` SET `order_num`=2 WHERE `menu_id`=4070;  -- 安灯看板 (原order4→2)
UPDATE `sys_menu` SET `order_num`=3 WHERE `menu_id`=4080;  -- SN追溯   (原order5→3)
-- 隐藏详情菜单 4041/4061/4081 parent 不变, 自动跟随父菜单到新目录

-- ---------- 2.4 MES C菜单 path 修正: 移除冗余域名前缀 ----------
-- 原 path 写成 domain/leaf (如 eq/equipment) 但又挂在同名M目录(eq)下,
-- RuoYi前端 filterChildren 会拼接父path, 导致路由重复: /mes/eq/eq/equipment
-- 改为叶子路径, 与ERP(wm/warehouse)、MES-pro(wo/workorder)规范一致, 路由变为 /mes/eq/equipment
UPDATE `sys_menu` SET `path`='equipment'       WHERE `menu_id`=4110;  -- 原 eq/equipment
UPDATE `sys_menu` SET `path`='maintenance'      WHERE `menu_id`=4115;  -- 原 eq/maintenance
UPDATE `sys_menu` SET `path`='repair'           WHERE `menu_id`=4120;  -- 原 eq/repair
UPDATE `sys_menu` SET `path`='inspection'        WHERE `menu_id`=4125;  -- 原 eq-inspection
UPDATE `sys_menu` SET `path`='iqc'              WHERE `menu_id`=4090;  -- 原 qc/iqc
UPDATE `sys_menu` SET `path`='ipqc'             WHERE `menu_id`=4094;  -- 原 qc/ipqc
UPDATE `sys_menu` SET `path`='oqc'              WHERE `menu_id`=4098;  -- 原 qc/oqc
UPDATE `sys_menu` SET `path`='collection-point' WHERE `menu_id`=4130;  -- 原 dc/collection-point
UPDATE `sys_menu` SET `path`='data-record'      WHERE `menu_id`=4135;  -- 原 dc/data-record
UPDATE `sys_menu` SET `path`='control-chart'    WHERE `menu_id`=4138;  -- 原 spc/control-chart
UPDATE `sys_menu` SET `path`='data-point'       WHERE `menu_id`=4144;  -- 原 spc/data-point
UPDATE `sys_menu` SET `path`='material'         WHERE `menu_id`=4030;  -- 原 md-material (移入md目录后去冗余前缀)
UPDATE `sys_menu` SET `path`='bom'              WHERE `menu_id`=4031;  -- 原 md-bom
UPDATE `sys_menu` SET `path`='routing'          WHERE `menu_id`=4032;  -- 原 md-routing


-- ============================================================
-- 三、AI 模块  (一级根: 1700 AI 智能助手)
-- ============================================================

-- ---------- 3.1 新增 5 个二级目录 (M) ----------
DELETE FROM `sys_menu` WHERE `menu_id` IN (1706,1707,1708,1709,1713);

INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query_param`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_dept`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES
(1706,'对话与应用',1700,1,'chat',     NULL,NULL,1,0,'M','0','0','','chat-dot-round', 103,1,'2026-06-29 00:00:00',NULL,NULL,'对话与应用目录'),
(1707,'模型配置',  1700,2,'modelCfg', NULL,NULL,1,0,'M','0','0','','cpu',            103,1,'2026-06-29 00:00:00',NULL,NULL,'模型配置目录'),
(1708,'知识与工具',1700,3,'toolkit',  NULL,NULL,1,0,'M','0','0','','collection',     103,1,'2026-06-29 00:00:00',NULL,NULL,'知识与工具目录'),
(1709,'提示工程',  1700,4,'promptEng',NULL,NULL,1,0,'M','0','0','','documentation',   103,1,'2026-06-29 00:00:00',NULL,NULL,'提示工程目录'),
(1713,'运营分析',  1700,5,'stats',    NULL,NULL,1,0,'M','0','0','','chart',           103,1,'2026-06-29 00:00:00',NULL,NULL,'运营分析目录');

-- ---------- 3.2 C 菜单 parent_id + order_num 调整 ----------
-- 对话与应用(1706)
UPDATE `sys_menu` SET `parent_id`=1706,`order_num`=1 WHERE `menu_id`=1701;  -- 智能对话
UPDATE `sys_menu` SET `parent_id`=1706,`order_num`=2 WHERE `menu_id`=1810;  -- 文生图
-- 模型配置(1707)
UPDATE `sys_menu` SET `parent_id`=1707,`order_num`=1 WHERE `menu_id`=1702;  -- 模型配置
UPDATE `sys_menu` SET `parent_id`=1707,`order_num`=2 WHERE `menu_id`=1703;  -- 厂商管理
-- 知识与工具(1708)
UPDATE `sys_menu` SET `parent_id`=1708,`order_num`=1 WHERE `menu_id`=1704;  -- 知识库管理
UPDATE `sys_menu` SET `parent_id`=1708,`order_num`=2 WHERE `menu_id`=1705;  -- 工具管理(MCP)
UPDATE `sys_menu` SET `parent_id`=1708,`order_num`=3 WHERE `menu_id`=1800;  -- MCP市场
-- 提示工程(1709)
UPDATE `sys_menu` SET `parent_id`=1709,`order_num`=1 WHERE `menu_id`=1760;  -- Prompt模板
UPDATE `sys_menu` SET `parent_id`=1709,`order_num`=2 WHERE `menu_id`=1770;  -- SQL示例
-- 运营分析(1713)
UPDATE `sys_menu` SET `parent_id`=1713,`order_num`=1 WHERE `menu_id`=1780;  -- A/B测试
UPDATE `sys_menu` SET `parent_id`=1713,`order_num`=2 WHERE `menu_id`=1790;  -- Token用量


-- ============================================================
-- 四、角色菜单授权补全 (sys_role_menu)
-- RuoYi 按 menu_id 授权, 新增二级目录需补授权, 否则非超管用户看不到归入新目录的菜单。
-- 策略: 为每个新二级目录, 复制其一级根目录(2000/4000/1700)已授权的角色。
-- ============================================================

-- 删除可能存在的旧授权(幂等), 再补全
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (
  2960,2961,2962,2963,2964,2965,2966,2967,2968,2969,2970,2971,2972,2973,  -- ERP
  4150,4151,                                                                  -- MES
  1706,1707,1708,1709,1713                                                    -- AI
);

-- ERP 新目录: 继承 2000 的角色授权
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id, n.menu_id
FROM `sys_role_menu` r
CROSS JOIN (SELECT 2960 AS menu_id UNION SELECT 2961 UNION SELECT 2962 UNION SELECT 2963 UNION SELECT 2964
            UNION SELECT 2965 UNION SELECT 2966 UNION SELECT 2967 UNION SELECT 2968 UNION SELECT 2969
            UNION SELECT 2970 UNION SELECT 2971 UNION SELECT 2972 UNION SELECT 2973) n
WHERE r.menu_id = 2000;

-- MES 新目录: 继承 4000 的角色授权
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id, n.menu_id
FROM `sys_role_menu` r
CROSS JOIN (SELECT 4150 AS menu_id UNION SELECT 4151) n
WHERE r.menu_id = 4000;

-- AI 新目录: 继承 1700 的角色授权
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id, n.menu_id
FROM `sys_role_menu` r
CROSS JOIN (SELECT 1706 AS menu_id UNION SELECT 1707 UNION SELECT 1708 UNION SELECT 1709 UNION SELECT 1713) n
WHERE r.menu_id = 1700;


-- ============================================================
-- 五、校验查询 (执行后运行, 确认无冲突)
-- ============================================================
-- 5.1 order_num 唯一性: 期望返回空
-- SELECT parent_id, order_num, COUNT(*) AS cnt
-- FROM sys_menu WHERE menu_type IN ('M','C')
-- GROUP BY parent_id, order_num HAVING COUNT(*) > 1;

-- 5.2 ERP 二级目录数: 期望 21
-- SELECT COUNT(*) FROM sys_menu WHERE parent_id=2000 AND menu_type='M';

-- 5.3 MES 二级目录数: 期望 7
-- SELECT COUNT(*) FROM sys_menu WHERE parent_id=4000 AND menu_type='M';

-- 5.4 AI 二级目录数: 期望 5
-- SELECT COUNT(*) FROM sys_menu WHERE parent_id=1700 AND menu_type='M';

-- 5.5 游离菜单已归位: 期望都返回空
-- SELECT * FROM sys_menu WHERE menu_id=2080;  -- parent_id 应为 2500
-- SELECT * FROM sys_menu WHERE menu_id=2950;  -- parent_id 应为 4150
-- SELECT * FROM sys_menu WHERE parent_id=4000 AND menu_type='C';  -- 主数据三项应已移走
