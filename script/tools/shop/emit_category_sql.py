"""Emit script/sql/crmeb_upgrade/08_category_rework.sql from category_result.json.

Guards each UPDATE on the OLD name so replaying is a no-op.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

WORK = Path(r"D:\workspace\nexus-erp\doc\assets\_t1_work")
RESULT = WORK / "category_result.json"
DEST = Path(r"D:\CRMEB\crmeb_java\script\sql\crmeb_upgrade\08_category_rework.sql")

rows = json.loads(RESULT.read_text(encoding="utf-8"))

header = """-- ----------------------------------------------------------------------------
-- 商城分类重构: 顺序修正 + 名称精简 + 分类图标 (08_category_rework.sql)
--
-- 背景: ERP 重建后向商城导入了 9 个顶级 + 37 个子类, 名称直接沿用 ERP 分类树, 出现三个问题:
--
-- 1) 顺序相反
--    根因: CategoryServiceImpl:279 的分页查询是 `ORDER BY sort DESC, id ASC` ——
--          本平台的语义是**sort 越大越靠前**。导入时按"显示顺序"给了 0..8, 于是整个列表倒过来。
--    修法: 改数据不改框架 —— 顶级按显示顺序给 9..1(金属五金=9 ... 其他制造=1),
--          子类在每个父类内按其显示顺序给 n..1。
--          (create_category 之后新加分类同样要遵守: 想排前面就给更大的 sort)
--
-- 2) 名称过长
--    default_cate.vue(模板1, 当前启用) 左侧栏 180rpx, 右侧子类格子 120rpx + 24rpx 单行文字,
--    超过约 5 个汉字就被截断。统一压到 4 字以内(子类个别 5 字)。
--    仅改商城显示名, ERP 侧分类名不动; 映射见下表注释。
--
-- 3) 缺少分类图标
--    模板1 用 `itemn.extra` 渲染 120rpx 圆形图标, 为空时显示灰色占位块。
--    extra 存**相对路径**(nexusimage/...), 由 NexusFrontImagePathFilterConfig 在
--    /api/front/* 响应里统一补成 CDN 绝对地址(该过滤器幂等, 绝对 URL 不会再被拼一次)。
--
-- 注意: extra 里的 object key 是**本机 MinIO(bucket shop)** 的对象, 换环境需重新上传;
--       名称与 sort 是可移植的。图标源图见 nexus-erp 仓库 doc/assets/category-icons-2026-09-14/。
-- 幂等: 每条 UPDATE 以旧名称为条件, 重放影响 0 行。
-- ----------------------------------------------------------------------------

-- 名称映射(ERP 原名 -> 商城显示名):
"""

lines = [header]
for r in rows:
    pad = "  " if r["level"] == "top" else "      "
    lines.append(f'-- {pad}{r["old_name"]} -> {r["new_name"]}\n')
lines.append("\n")

for r in rows:
    lines.append(
        f'UPDATE eb_category SET name = \'{r["new_name"]}\', sort = {r["sort"]}, '
        f'extra = \'{r["extra"]}\'\n'
        f' WHERE id = {r["id"]} AND name = \'{r["old_name"]}\';\n\n'
    )

lines.append("""-- 核对(手工执行):
-- SELECT id, pid, sort, name, extra FROM eb_category
--  WHERE id BETWEEN 814 AND 859 ORDER BY pid, sort DESC, id ASC;
-- 期望: 顶级 814..822 依次为 金属五金/机械设备/电子电气/汽车交通/医疗器械/消费轻工/塑胶复材/新能源/其他制造
-- 验证脚本: nexus-erp 仓库 script/tools/shop/verify_categories.py (退出码 0/1)
""")

DEST.write_text("".join(lines), encoding="utf-8")
print(f"wrote {DEST} ({DEST.stat().st_size} bytes, {len(rows)} UPDATEs)")
