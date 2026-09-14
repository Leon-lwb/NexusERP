# -*- coding: utf-8 -*-
"""物料分类映射与工艺模板（原材料 / 半成品）。

**为什么单独成模块**：原先分类靠 `raw_leaf()` / `semi_leaf()` 的关键字规则，
规则漏匹配时兜底 `return "CH_LUBE"`，导致 45 个物料（伺服电机、谐波减速器、
单晶硅电池片…）全部堆进「润滑剂」。这里改成**显式全量映射 + 未命中即报错**，
宁可生成时炸掉，也不让物料静默落进错误的分类。

数据来源：
- 原材料 → `raw_table.RAW_TABLE`（名称 → 分类/成本/重量/体积，132 条全量）
- 半成品 → `SEMI_CAT`（101 条全量）
"""

try:
    from raw_table import RAW_TABLE
except ImportError:  # 表格尚未生成时仍可导入本模块做分类校验
    RAW_TABLE = {}

# ---------------------------------------------------------------- 新增分类节点
# (code, name, parent_code)。parent_code 为 None 表示顶级。
# 备注：这批节点是为修正"外购件被塞进原材料/润滑剂"而补的，
# 外购的机电、传动、密封、光学、光伏、模具配件统一挂在新的二级「外购件与机电」下。
CATEGORY_ADD = [
    # 二级
    ("PURCHASED", "外购件与机电", "RAW"),
    # 电子元器件补充
    ("EL_MODULE", "模块与器件", "ELECTRONIC"),
    ("EL_WIRE", "线束线缆", "ELECTRONIC"),
    ("EL_MAGNET", "磁性材料", "ELECTRONIC"),
    # 化工补充
    ("CH_PLATING", "表面处理药剂", "CHEMICAL"),
    ("CH_ELECTRO", "电子化学品", "CHEMICAL"),
    # 塑料补充
    ("PL_ALLOY", "塑料合金", "PLASTIC"),
    ("PL_TPU", "TPU", "PLASTIC"),
    # 外购件与机电
    ("TR_BEARING", "轴承", "PURCHASED"),
    ("TR_LINEAR", "直线传动件", "PURCHASED"),
    ("TR_CHAIN", "链条与带", "PURCHASED"),
    ("ME_MOTOR", "电机", "PURCHASED"),
    ("ME_REDUCER", "减速装置", "PURCHASED"),
    ("SE_OIL", "油封", "PURCHASED"),
    ("SE_MECH", "机械密封", "PURCHASED"),
    ("SE_RUBBER", "橡胶密封件", "PURCHASED"),
    ("OP_LENS", "光学镜头", "PURCHASED"),
    ("OP_GLASS", "玻璃件", "PURCHASED"),
    ("PV_CELL", "光伏电池片", "PURCHASED"),
    ("PV_AUX", "光伏辅材", "PURCHASED"),
    ("ML_GUIDE", "模具导向件", "PURCHASED"),
    ("ML_COOLING", "模具冷却件", "PURCHASED"),
    ("AX_FILTER", "过滤耗材", "PURCHASED"),
    ("AX_TEXTILE", "纺织软装", "PURCHASED"),
    ("AX_MISC", "其他辅材", "PURCHASED"),
    ("OS_SHELL", "外购结构件", "PURCHASED"),
    ("OS_HARDWARE", "外购五金件", "PURCHASED"),
    # 高值外购总成（补齐 5 个产品 BOM 的结构性缺料）
    ("ME_CTRL", "电控与驱动装置", "PURCHASED"),
    ("ME_SPINDLE", "机床功能部件", "PURCHASED"),
    ("ME_CORE", "主机核心部件", "PURCHASED"),
    ("ME_COOLER", "换热与冷却装置", "PURCHASED"),
    ("TR_COUPLING", "联轴器", "PURCHASED"),
    ("ML_STD", "模具标准件", "PURCHASED"),
    # 半成品补充
    ("SM_RUBBER", "橡胶成型件", "SEMI"),
    ("SM_SOFTWARE", "软件与固件", "SEMI"),
]

# ---------------------------------------------------------------- 高值外购总成
# 这 5 个产品的 BOM 原先只写了钢材与通用件，真正占成本大头的外购总成缺失，
# 导致标准成本只有售价的一两成（毛利率 53%~82%）。补齐后标定才能落进目标区间。
# 这些件按件计价、**不参与用量标定**（缩放它们会破坏真实采购价）。
# 元组格式与 products.py 的 raw 一致：(物料名, 规格, 单位, 用量)
COMPONENT_BOM = {
    "注塑模具": [("模架标准件 300×300", "LKM 标准模架", "套", 1),
                ("热流道系统 2 点针阀", "2 点针阀", "套", 1),
                ("顶出系统", "含顶针顶板", "套", 1)],
    "齿轮减速机 JZQ-350": [("配套电机 Y132M-4", "7.5kW B3", "台", 1),
                          ("弹性联轴器 HL4", "GB/T5014 HL4", "套", 1)],
    "单级离心泵": [("离心泵用电机 Y132S-4", "5.5kW B35", "台", 1),
                  ("集装式机械密封", "Φ35 碳化硅", "套", 1),
                  ("弹性联轴器 HL3", "GB/T5014 HL3", "套", 1)],
    "数控铣床 XK714": [("数控系统", "FANUC 0i-MF / 广数 GSK25i", "套", 1),
                      ("主轴单元 BT40", "8000rpm 7.5kW", "套", 1),
                      ("伺服驱动套装", "3 轴交流伺服", "套", 1),
                      ("圆盘刀库 16 刀", "BT40 斗笠式", "套", 1)],
    "螺杆式空压机": [("螺杆主机", "1.2m³/min 空气端", "台", 1),
                    ("主电机 Y160M-4", "11kW B3", "台", 1),
                    ("变频器 22kW", "380V 矢量", "台", 1),
                    ("板式冷却器", "换热面积 3m²", "台", 1),
                    ("PLC 控制器", "含 7 寸触摸屏", "套", 1)],
}

# 高值外购件名单：成本标定时视为固定成本，不缩放
FIXED_MATERIALS = {t[0] for items in COMPONENT_BOM.values() for t in items}

# ---------------------------------------------------------------- 半成品分类（101 条全量）
# 只列**修正后**的正确分类；与原自动规则的差异见 SEMI_CAT_FIXED 注释。
SEMI_CAT = {
    # 金属制品与五金
    "冷镦坯 M12": "SM_STAMP",
    "螺母 M12 8级": "SM_MACHINED",
    "平垫 M12": "SM_STAMP",                      # 原 SM_MACHINED
    "棘轮头 机加件": "SM_MACHINED",
    "套筒 10件组": "SM_MACHINED",
    "扳手手柄 锻件": "SM_CAST",
    "中心铆钉": "SM_MACHINED",
    "剪体 冲压件": "SM_STAMP",
    "剪柄 冲压件": "SM_STAMP",
    "锁体 冲压件": "SM_STAMP",
    "锁扣 冲压件": "SM_STAMP",
    "锁芯 机加件": "SM_MACHINED",                # 原 SM_INJECT（锌合金压铸≠注塑）
    "门锁执行器": "SM_ASSY",                     # 原 SM_MACHINED（直流马达组件）
    "弹簧毛坯": "SM_MACHINED",
    # 机加工件
    "大齿轮 模数3×50齿": "SM_MACHINED",
    "小齿轮 模数3×20齿": "SM_MACHINED",
    "传动轴 精磨": "SM_MACHINED",
    "齿轮组 模数4": "SM_MACHINED",
    "输入轴 精磨": "SM_MACHINED",
    "输出轴 精磨": "SM_MACHINED",
    "泵轴 精磨": "SM_MACHINED",
    "螺杆转子 精磨": "SM_MACHINED",              # 原 SM_MOTOR（40Cr 渗碳转子≠电机组件）
    "工作台 精磨": "SM_CAST",
    "主动滚筒": "SM_MACHINED",
    "从动滚筒": "SM_MACHINED",
    "托辊组": "SM_MACHINED",
    "模架 精加工": "SM_MACHINED",
    "型腔 精加工": "SM_MACHINED",
    "型芯 精加工": "SM_MACHINED",
    "传感器封装件": "SM_MACHINED",
    "油气分离器": "SM_MACHINED",
    # 铸锻件
    "齿轮箱体 精加工": "SM_CAST",                # 原 SM_MACHINED
    "减速机箱体 精加工": "SM_CAST",              # 原 SM_SHEET
    "泵体 精加工": "SM_CAST",
    "叶轮 精加工": "SM_CAST",
    "床身 精加工": "SM_CAST",
    "立柱 精加工": "SM_CAST",
    "主轴箱 精加工": "SM_CAST",
    "主机壳体 精加工": "SM_CAST",                # 原 SM_INJECT（HT250 铸造件）
    # 钣金件
    "机箱框架 焊接件": "SM_SHEET",
    "机箱面板 折弯件": "SM_SHEET",
    "型材框架 焊接件": "SM_SHEET",
    "斜撑组件": "SM_SHEET",
    "机架 焊接件": "SM_SHEET",
    "散热片 铝型材": "SM_SHEET",
    "车架 焊接件": "SM_SHEET",
    "轮椅车架 弯管焊接件": "SM_SHEET",
    "侧板组件 焊接件": "SM_SHEET",
    "层板 折弯件": "SM_SHEET",                   # 原 SM_MACHINED
    "横梁 折弯件": "SM_SHEET",                   # 原 SM_MACHINED
    "桩体 钣金焊接件": "SM_SHEET",
    "控制柜 钣金件": "SM_SHEET",                 # 原 SM_MACHINED
    "臂体结构件 精加工": "SM_SHEET",
    "网关铝壳 阳极氧化": "SM_SHEET",
    # 注塑件
    "路由器外壳 注塑件": "SM_INJECT",
    "风叶 注塑件": "SM_INJECT",
    "风扇底座 注塑件": "SM_INJECT",
    "灯罩 注塑件": "SM_INJECT",
    "灯座 注塑件": "SM_INJECT",
    "上壳 注塑件": "SM_INJECT",
    "下壳 注塑件": "SM_INJECT",
    "音箱上下壳 注塑件": "SM_INJECT",
    # 橡胶成型件
    "硅胶密封圈毛坯": "SM_RUBBER",               # 原 SM_MACHINED（硅胶模压件）
    # 电子组件
    "PCB 图形转移板": "SM_PCBA",                 # 原 SM_MACHINED
    "主控板 PCBA-R1200": "SM_PCBA",
    "控制板 PCBA-FAN": "SM_PCBA",
    "电源控制板 PCBA-PWR": "SM_PCBA",
    "信号调理板 PCBA-TS": "SM_PCBA",
    "主控板 PCBA-DMM": "SM_PCBA",
    "灯板 PCBA-LED": "SM_PCBA",
    "主控板 PCBA-GW1000": "SM_PCBA",
    "BMS 控制板 PCBA-BMS": "SM_PCBA",
    "主控板 PCBA-EVSE": "SM_PCBA",
    "主控板 PCBA-BAND3": "SM_PCBA",
    "主控板 PCBA-POS8": "SM_PCBA",
    "主控板 PCBA-IPC300": "SM_PCBA",
    "功放板 PCBA-SPK20": "SM_PCBA",
    # 电机组件
    "直流无刷电机": "SM_MOTOR",
    "定子铁芯 叠压件": "SM_MOTOR",
    "转子组件": "SM_MOTOR",
    # 装配组件
    "旋转开关组件": "SM_ASSY",
    "车轮总成 26 寸": "SM_ASSY",
    "车把组件": "SM_ASSY",
    "碳纤维机臂 4 支": "SM_ASSY",
    "中心板 上下层": "SM_ASSY",
    "起落架组件": "SM_ASSY",
    "大轮总成 24 寸": "SM_ASSY",
    "小轮总成 8 寸": "SM_ASSY",
    "鹅颈软管": "SM_ASSY",
    "电芯模组 15S3P": "SM_ASSY",
    "充电接口模块": "SM_ASSY",                   # 原 SM_MACHINED
    "电池串组": "SM_ASSY",
    "关节模组 1-3 轴": "SM_ASSY",                # 原 SM_MACHINED
    "关节模组 4-6 轴": "SM_ASSY",                # 原 SM_MACHINED
    "心率传感器模组": "SM_ASSY",
    "触控屏组件": "SM_ASSY",
    "扫码模组": "SM_ASSY",
    "NFC 刷卡模组": "SM_ASSY",
    "打印模组总成": "SM_ASSY",
    "镜头模组": "SM_ASSY",
    # 软件与固件
    "网关固件 FW-GW1000": "SM_SOFTWARE",         # 原 SM_MACHINED
}

# 半成品 BOM 的原材料模板（按半成品分类）。
# 变更半成品分类时，必须同时把它的模板行换成新分类的模板行，
# 否则会出现「HT250 铸造件消耗 ABS 工程塑料」这类不成立的配方。
SEMI_RAW_TEMPLATE = {
    "SM_MACHINED": [("合金钢棒料 40Cr", "Φ20mm", "千克", 1.5),
                    ("切削液", "水溶性", "升", 0.05)],
    "SM_PCBA":     [("FR-4 覆铜板", "1.6mm 双面", "张", 1),
                    ("主控芯片 MC32F006", "SOP-16 32位MCU", "颗", 1),
                    ("贴片电阻电容组", "0603/0805", "套", 1),
                    ("板对板连接器", "0.5mm 40P", "个", 2)],
    "SM_MOTOR":    [("硅钢片 50W470", "0.5mm", "千克", 0.85),
                    ("漆包线 QZ-2", "Φ0.35mm", "千克", 0.16),
                    ("钕铁硼磁钢", "N35 弧形", "件", 4),
                    ("轴承 608", "GB/T276 608", "件", 2)],
    "SM_SHEET":    [("冷轧钢板 SPCC", "1.5mm", "千克", 1.2),
                    ("焊丝 ER50-6", "Φ1.0mm", "千克", 0.05)],
    "SM_INJECT":   [("ABS 工程塑料", "白色 注塑级", "千克", 1.1),
                    ("色母粒", "透明", "千克", 0.01)],
    "SM_CAST":     [("铸铁 HT250", "HT250", "千克", 1.2),
                    ("铸造生铁", "Z14", "千克", 0.3)],
    "SM_STAMP":    [("冷轧钢板 SPCC", "1.2mm", "千克", 1.1)],
    "SM_ASSY":     [("标准紧固件组", "M3-M6", "套", 1),
                    ("装配润滑脂", "锂基脂", "千克", 0.02)],
    "SM_RUBBER":   [("食品级硅胶混炼胶", "硬度 50 Shore A", "千克", 1.05),
                    ("硫化剂", "双二五", "千克", 0.02)],
    "SM_SOFTWARE": [],
}

# 铝合金壳体/型材的特殊模板（铝件不能用钢板模板）
SEMI_ALUMINIUM_TEMPLATE = [("铝合金型材 6063", "100×60×25mm", "千克", 0.45),
                           ("阳极氧化剂", "硫酸型", "升", 0.02)]

# ---------------------------------------------------------------- 查询接口


def semi_leaf(name: str) -> str:
    """半成品 → 叶子分类。未登记直接报错，避免静默落错分类。"""
    try:
        return SEMI_CAT[name]
    except KeyError:
        raise KeyError("半成品未登记分类：%r（请补进 taxonomy.SEMI_CAT）" % name) from None


def raw_leaf(name: str) -> str:
    """原材料 → 叶子分类。"""
    try:
        return RAW_TABLE[name][0]
    except KeyError:
        raise KeyError("原材料未登记分类：%r（请补进 raw_table.RAW_TABLE）" % name) from None


def semi_template(name: str):
    """取半成品的原材料模板（含铝合金特例）。"""
    if "铝" in name and ("壳" in name or "型材" in name):
        return SEMI_ALUMINIUM_TEMPLATE
    return SEMI_RAW_TEMPLATE[semi_leaf(name)]


def raw_cost(name: str):
    """(单价, 重量, 体积)。"""
    _cat, cost, weight, volume = RAW_TABLE[name]
    return cost, weight, volume
