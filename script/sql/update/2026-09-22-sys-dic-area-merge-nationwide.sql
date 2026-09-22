-- ============================================================================
-- sys_dic_area 增量合并全国省市区数据（来源：crmeb.eb_system_city）
-- ============================================================================
-- 设计要点（详见 doc/system/2026-09-22-行政区划模块优化计划.md Task 11）：
--   1. 增量合并，绝不 TRUNCATE：ERP 独有 1522 个码（含 1481 行浙江街道），
--      商城独有 3451 个码，两者交集仅 139。清空重建会永久丢失浙江街道数据。
--   2. 商城 level 是 0=省/1=市/2=区县，ERP layered 是 1=省/2=市/3=区县 —— 方向相反，必须显式转换。
--   3. 商城 city_id 是合成自增 ID，GB 码在 area_code（12 位）；ERP area_id 是 6 位或 9 位。
--   4. 东莞(441900)/中山(442000)/儋州(460400) 三个"直筒子市"不设市辖区，
--      其市与下辖镇/街道的 area_code 前 6 位相同，一律取 6 位会重码，
--      故"区县前缀 == 父市前缀"时改取 9 位。
--   5. 商城为 2021 快照，缺 2021 年后新设区县；ERP 浙江数据更新，故 WHERE NOT EXISTS 保留 ERP 版本。
--   6. 本脚本不使用窗口函数（目标库为 MySQL 5.7），sort 直接用商城 id 保持原有顺序。
--
-- ⚠️ 本脚本为「执行一次」脚本：DML 部分是幂等的（WHERE NOT EXISTS + 唯一键兜底），
--    但备份步骤与唯一键 DDL 已做防重入保护，重复执行不会破坏备份、也不会中断。
--    重复执行是安全的，但不会产生任何额外效果。
-- ============================================================================

use `ry-vue`;

-- ---------------------------------------------------------------------------
-- 0. 备份（务必先执行；失败可整表回滚）
--    用 if not exists：重复执行本脚本时**不得覆盖**首次的 1661 行原始快照
-- ---------------------------------------------------------------------------
create table if not exists sys_dic_area_bak_20260922 as select * from sys_dic_area;
select 'BACKUP_ROWS' as k, count(*) as v from sys_dic_area_bak_20260922;

-- ---------------------------------------------------------------------------
-- 1. 修正省级父级：34 个省级行的 parent_area_id=156，而 156 这个 area_id 不存在
-- ---------------------------------------------------------------------------
update sys_dic_area set parent_area_id = 0 where layered = 1 and parent_area_id = 156;
select 'PROVINCE_PARENT_FIXED' as k, row_count() as v;

-- ---------------------------------------------------------------------------
-- 2. 已撤销行政区重挂到现行替代码（71 行街道 + 1 行区县）
--    这些父码是历史区划调整中撤销的，其下级街道一直悬空。
--    所有目标码均已确认存在于本表中（见脚本末尾校验）。
-- ---------------------------------------------------------------------------
update sys_dic_area set parent_area_id = 330212 where parent_area_id = 330204; -- 宁波江东区 → 鄞州区(2016)
update sys_dic_area set parent_area_id = 330213 where parent_area_id = 330283; -- 奉化市   → 奉化区(2016)
update sys_dic_area set parent_area_id = 330305 where parent_area_id = 330322; -- 洞头县   → 洞头区(2015)
update sys_dic_area set parent_area_id = 330603 where parent_area_id = 330621; -- 绍兴县   → 柯桥区(2013)
update sys_dic_area set parent_area_id = 330604 where parent_area_id = 330682; -- 上虞市   → 上虞区(2013)
update sys_dic_area set parent_area_id = 331083 where parent_area_id = 331021; -- 玉环县   → 玉环市(2017)
update sys_dic_area set parent_area_id = 330703 where parent_area_id = 330752; -- 孝顺镇归金东区
update sys_dic_area set parent_area_id = 331000 where parent_area_id = 331999; -- 台州经济开发区 → 台州市
select 'DEPRECATED_REMAPPED' as k, row_count() as v;

-- ---------------------------------------------------------------------------
-- 3. 合并商城「省」到 layered=1（实测 31 个码全部已存在，此处为幂等保险）
-- ---------------------------------------------------------------------------
insert into sys_dic_area
    (area_id, parent_area_id, area_name, lng, lat, layered, sort, address,
     create_dept, create_by, create_time, del_flag, tenant_id)
select cast(left(c.area_code, 6) as unsigned), 0, c.name,
       nullif(c.lng, ''), nullif(c.lat, ''), 1, c.id, c.name,
       103, 1, now(), '0', '000000'
from crmeb.eb_system_city c
where c.level = 0
  and char_length(c.area_code) = 12
  and not exists (select 1 from sys_dic_area d where d.area_id = cast(left(c.area_code, 6) as unsigned));
select 'INSERTED_PROVINCES' as k, row_count() as v;

-- ---------------------------------------------------------------------------
-- 4. 合并商城「市」到 layered=2
-- ---------------------------------------------------------------------------
insert into sys_dic_area
    (area_id, parent_area_id, area_name, lng, lat, layered, sort, address,
     create_dept, create_by, create_time, del_flag, tenant_id)
select cast(left(c.area_code, 6) as unsigned),
       cast(left(p.area_code, 6) as unsigned),
       c.name, nullif(c.lng, ''), nullif(c.lat, ''), 2, c.id,
       concat(p.name, ' ', c.name),
       103, 1, now(), '0', '000000'
from crmeb.eb_system_city c
join crmeb.eb_system_city p on p.city_id = c.parent_id
where c.level = 1
  and char_length(c.area_code) = 12 and char_length(p.area_code) = 12
  and not exists (select 1 from sys_dic_area d where d.area_id = cast(left(c.area_code, 6) as unsigned));
select 'INSERTED_CITIES' as k, row_count() as v;

-- ---------------------------------------------------------------------------
-- 5. 合并商城「区县」到 layered=3（含直筒子市 9 位码特殊处理）
-- ---------------------------------------------------------------------------
insert into sys_dic_area
    (area_id, parent_area_id, area_name, lng, lat, layered, sort, address,
     create_dept, create_by, create_time, del_flag, tenant_id)
select case when left(c.area_code, 6) = left(p.area_code, 6)
            then cast(left(c.area_code, 9) as unsigned)
            else cast(left(c.area_code, 6) as unsigned) end,
       cast(left(p.area_code, 6) as unsigned),
       c.name, nullif(c.lng, ''), nullif(c.lat, ''), 3, c.id,
       concat(ifnull(gp.name, ''), ' ', p.name, ' ', c.name),
       103, 1, now(), '0', '000000'
from crmeb.eb_system_city c
join crmeb.eb_system_city p on p.city_id = c.parent_id
left join crmeb.eb_system_city gp on gp.city_id = p.parent_id
where c.level = 2
  and char_length(c.area_code) = 12 and char_length(p.area_code) = 12
  and not exists (
      select 1 from sys_dic_area d
      where d.area_id = case when left(c.area_code, 6) = left(p.area_code, 6)
                             then cast(left(c.area_code, 9) as unsigned)
                             else cast(left(c.area_code, 6) as unsigned) end);
select 'INSERTED_DISTRICTS' as k, row_count() as v;

-- ---------------------------------------------------------------------------
-- 6. 唯一约束：area_id 全局唯一（表已加入租户排除，不再按租户隔离）
--    这同时把 Task 4 的"软删后不可重建同码"从应用层约定升级为存储层约束。
--    用 information_schema 做条件判断：重复执行时不会因 ERROR 1061 中断脚本。
-- ---------------------------------------------------------------------------
set @uk_exists := (select count(*) from information_schema.statistics
                   where table_schema = database()
                     and table_name = 'sys_dic_area'
                     and index_name = 'uk_area_tenant');
set @uk_ddl := if(@uk_exists = 0,
                  'alter table sys_dic_area add unique key uk_area_tenant (tenant_id, area_id)',
                  'select ''uk_area_tenant 已存在，跳过'' as note');
prepare uk_stmt from @uk_ddl;
execute uk_stmt;
deallocate prepare uk_stmt;

-- ---------------------------------------------------------------------------
-- 7. 校验
-- ---------------------------------------------------------------------------
select 'FINAL_TOTAL' as k, count(*) as v from sys_dic_area
union all select 'FINAL_DISTINCT_AREAID', count(distinct area_id) from sys_dic_area
union all select 'FINAL_ORPHANS', (select count(*) from sys_dic_area a
    where a.parent_area_id <> 0
      and not exists (select 1 from sys_dic_area p where p.area_id = a.parent_area_id))
union all select 'FINAL_LAYERED4_KEEP', (select count(*) from sys_dic_area where layered = 4)
union all select 'FINAL_LAYERED5_KEEP', (select count(*) from sys_dic_area where layered = 5)
union all select 'FINAL_PROVINCES_WITH_CHILDREN', (select count(distinct p.area_id)
    from sys_dic_area p join sys_dic_area c on c.parent_area_id = p.area_id where p.layered = 1);

select layered, count(*) as cnt from sys_dic_area group by layered order by layered;

-- ============================================================================
-- 回滚（如需）
-- ============================================================================
-- 本脚本为一次性增量合并，回滚即"恢复备份表快照"：
--
--   use `ry-vue`;
--   alter table sys_dic_area drop index uk_area_tenant;
--   start transaction;
--   delete from sys_dic_area;
--   insert into sys_dic_area select * from sys_dic_area_bak_20260922;
--   commit;
--   alter table sys_dic_area auto_increment = 2951;
--
-- 注意：回滚会丢弃合并进来的 3451 行，并恢复 34 个省级行的 parent_area_id=156
-- 与 72 行悬空父码。确认无需回滚后，可 drop table sys_dic_area_bak_20260922;
--
-- ============================================================================
-- ⚠️ 数据变更后必须清一次 Redis 全量树缓存（Task 6 引入，TTL 12h 兜底）
-- ============================================================================
-- 缓存 key 带租户前缀（TenantKeyPrefixHandler 会给所有非全局 key 加 "<tenantId>:"），
-- 所以真实 key 是 000000:sys_dic_area_tree，**不是** sys_dic_area_tree：
--
--   redis-cli -n 0 DEL 000000:sys_dic_area_tree
--
-- 用 --scan 确认更稳妥：
--   redis-cli -n 0 --scan --pattern '*sys_dic_area_tree*'
--
-- 否则 12 小时内接口仍返回旧的 1661 行树。应用运行中即时生效、无需重启。

