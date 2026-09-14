"""Build the category rework manifest: new names, display order, and icon prompts.

Facts driving this:
  * CategoryServiceImpl:279 orders by `sort DESC, id ASC` -> BIGGER sort shows FIRST.
    My original data used sort 0..8 in display order, hence the reversed list.
  * default_cate.vue renders a 120rpx circle from `itemn.extra` for each CHILD,
    with a 24rpx single-line label -> names longer than ~5 CJK chars get clipped.
  * admin sort.vue labels `extra` as 分类图标 and suggests 100x100px.

Emits doc/assets/_t1_work/category_manifest.json:
  [{id, pid, level, old_name, new_name, sort, subject, prompt}, ...]
Sorted so that `sort DESC` yields the intended display order.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import pymysql

sys.stdout.reconfigure(encoding="utf-8")

OUT = Path(r"D:\workspace\nexus-erp\doc\assets\_t1_work\category_manifest.json")

# top-level: renamed to <=4 chars, listed in intended display order
TOP_ORDER = [
    ("金属制品与五金", "金属五金", "一组金属紧固件与五金件（六角螺栓、螺母、垫圈与扳手）"),
    ("机械设备", "机械设备", "一组机械传动部件（金属齿轮、轴承与联轴器）"),
    ("电子电气", "电子电气", "一块绿色电路板与电子元件（芯片、电容、连接器）"),
    ("汽车与交通", "汽车交通", "汽车零部件组合（刹车盘、齿轮与轮胎轮毂）"),
    ("医疗器械", "医疗器械", "医疗器械（不锈钢手术剪与听诊器造型）"),
    ("消费品与轻工", "消费轻工", "家用轻工消费品（现代台灯与金属置物架）"),
    ("塑料橡胶与复合材料", "塑胶复材", "工程塑料件与橡胶密封圈组合"),
    ("新能源与电气设备", "新能源", "新能源设备组合（锂电池组、光伏板与充电枪）"),
    ("其他离散装配", "其他制造", "工业机器臂与智能手持终端组合"),
]

# leaf: old -> (new, subject).  Order here = intended display order within the parent.
LEAF: dict[str, tuple[str, str]] = {
    # 金属五金
    "紧固件": ("紧固件", "一组成套高强度六角螺栓、螺母与平垫圈"),
    "传动部件": ("传动部件", "金属传动部件（齿轮、传动轴与联轴器）"),
    "弹簧与弹性件": ("弹簧件", "一组金属压缩弹簧与拉伸弹簧"),
    "冲压钣金件": ("钣金件", "冲压钣金零件（折弯金属板件与冲压件）"),
    "金属结构件": ("结构件", "焊接钢结构支架与金属框架构件"),
    "模具": ("模具", "一套精密注塑模具（带冷却水路的钢制模架）"),
    "五金工具": ("五金工具", "一套五金手工具（棘轮扳手与套筒）"),
    # 机械设备
    "减速与传动设备": ("减速传动", "一台铸铁箱体齿轮减速机"),
    "泵阀液压": ("泵阀液压", "工业离心泵与金属阀门组合"),
    "机床": ("机床", "一台数控铣床（立式加工中心造型）"),
    "输送与物流设备": ("输送设备", "一条滚筒皮带输送线"),
    "压缩与气动": ("压缩气动", "一台螺杆式空气压缩机与气缸"),
    # 电子电气
    "PCB与电路板": ("电路板", "一块绿色双层印刷电路板（沉金焊盘）"),
    "消费电子": ("消费电子", "消费电子产品（无线路由器与蓝牙音箱）"),
    "家用电器": ("家用电器", "家用电器（落地式电风扇）"),
    "电源与电池": ("电源电池", "开关电源模块与圆柱电池组合"),
    "电机与电控": ("电机电控", "无刷直流电机与变频控制器"),
    "传感器": ("传感器", "工业传感器探头（温度传感器与接近开关）"),
    "仪器仪表": ("仪器仪表", "数字万用表与温度变送器"),
    # 汽车交通
    "汽车零部件": ("汽车零件", "汽车门锁总成与车内零部件"),
    "两轮车": ("两轮车", "一辆城市通勤自行车"),
    "无人机": ("无人机", "一架四轴无人机机架（碳纤维机身）"),
    # 医疗器械
    "手术器械": ("手术器械", "不锈钢医用手术剪与镊子"),
    "康复器械": ("康复器械", "一台折叠式手动轮椅"),
    # 消费轻工
    "家具": ("家具", "四层金属工具置物架"),
    "灯具": ("灯具", "一盏 LED 护眼台灯"),
    "智能家居": ("智能家居", "一台带天线的智能网关设备"),
    # 塑胶复材
    "塑料外壳": ("塑料外壳", "一组精密塑料外壳件（阻燃 PC 材质）"),
    "橡胶密封件": ("密封件", "一组食品级硅胶密封圈（不同内径）"),
    # 新能源
    "电池包": ("电池包", "一台带 BMS 的磷酸铁锂电池包"),
    "充电桩": ("充电桩", "一台壁挂式交流充电桩"),
    "光伏组件": ("光伏组件", "一块单晶硅光伏组件板"),
    # 其他制造
    "机器人": ("机器人", "一台六轴工业机械臂"),
    "智能穿戴": ("智能穿戴", "一只智能手环（彩屏表带）"),
    "POS与打印": ("POS打印", "一台手持 POS 收银终端"),
    "安防设备": ("安防设备", "一台网络摄像机（POE 半球机）"),
    "摄像与音响": ("影音设备", "一台便携蓝牙音箱与网络摄像头组合"),
}

TEMPLATE = (
    "电商分类图标：{subject}。"
    "单个主体居中，纯白色无缝背景，柔和均匀的棚拍光线，细节清晰锐利，"
    "风格统一简洁的电商分类图标，无文字，无logo，无任何标注，"
    "方形构图，主体占比适中，四周留白，高清。"
)


def main() -> None:
    con = pymysql.connect(host="127.0.0.1", user="root", password="root", database="crmeb",
                          charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor, autocommit=True)
    with con, con.cursor() as cur:
        cur.execute("""SELECT id, pid, name FROM eb_category
                       WHERE id BETWEEN 814 AND 859 ORDER BY id""")
        rows = cur.fetchall()

    by_id = {r["id"]: r for r in rows}
    tops = [r for r in rows if r["pid"] == 0]
    leaves = [r for r in rows if r["pid"] != 0]

    # sanity: every existing name must be covered by the rename tables
    unknown = [r["name"] for r in tops if r["name"] not in {o for o, _, _ in TOP_ORDER}]
    unknown += [r["name"] for r in leaves if r["name"] not in LEAF]
    if unknown:
        raise SystemExit(f"unmapped category names: {unknown}")

    top_new = {old: new for old, new, _ in TOP_ORDER}
    top_subject = {old: sub for old, _, sub in TOP_ORDER}

    manifest = []

    # ---- top level: sort DESC -> first item gets the HIGHEST sort
    for pos, (old, new, subject) in enumerate(TOP_ORDER):
        row = next((r for r in tops if r["name"] == old), None)
        if row is None:
            raise SystemExit(f"top category not found in DB: {old}")
        manifest.append({
            "id": row["id"], "pid": 0, "level": "top",
            "old_name": old, "new_name": new,
            "sort": len(TOP_ORDER) - pos,
            "subject": subject,
            "prompt": TEMPLATE.format(subject=subject),
        })

    # ---- leaves: group by parent, keep LEAF dict order as display order
    leaf_order = list(LEAF.keys())
    for top_old, top_new_name, _ in TOP_ORDER:
        parent_row = next(r for r in tops if r["name"] == top_old)
        kids = [r for r in leaves if r["pid"] == parent_row["id"]]
        # order the children by their position in LEAF
        kids.sort(key=lambda r: leaf_order.index(r["name"]) if r["name"] in leaf_order else 999)
        n = len(kids)
        for pos, r in enumerate(kids):
            new, subject = LEAF[r["name"]]
            manifest.append({
                "id": r["id"], "pid": parent_row["id"], "level": "leaf",
                "parent_new_name": top_new_name,
                "old_name": r["name"], "new_name": new,
                "sort": n - pos,
                "subject": subject,
                "prompt": TEMPLATE.format(subject=subject),
            })

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")

    print(f"wrote {OUT}  ({len(manifest)} categories: "
          f'{sum(1 for m in manifest if m["level"] == "top")} top + '
          f'{sum(1 for m in manifest if m["level"] == "leaf")} leaf)\n')
    for m in manifest:
        pad = "  " if m["level"] == "top" else "      "
        print(f'{pad}{m["id"]} sort={m["sort"]:<3} {m["old_name"]} -> {m["new_name"]}')


if __name__ == "__main__":
    main()
