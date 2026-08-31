#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 NexusERP 用户操作手册 V3.0 (docx)

用法: python gen_manual.py
输出: doc/system/user-manual/NexusERP_用户操作手册.docx
依赖: python-docx (pip install python-docx)
"""
import docx
from docx import Document
from docx.shared import Pt, RGBColor, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   '..', '..', '..', 'doc', 'system', 'user-manual',
                   'NexusERP_用户操作手册.docx')

# ---------- 样式 ----------
FONT = '微软雅黑'
ACCENT = RGBColor(0x1F, 0x4E, 0x79)      # 深蓝
HEADER_BG = '1F4E79'
HEADER_FONT = RGBColor(0xFF, 0xFF, 0xFF)


def set_font(run, size=10.5, bold=False, color=None, name=FONT):
    run.font.name = name
    run.font.size = Pt(size)
    run.font.bold = bold
    if color is not None:
        run.font.color.rgb = color
    rpr = run._element.get_or_add_rPr()
    rf = rpr.find(qn('w:rFonts'))
    if rf is None:
        rf = OxmlElement('w:rFonts')
        rpr.append(rf)
    rf.set(qn('w:eastAsia'), name)


def add_para(doc, text, size=10.5, bold=False, color=None, style=None,
             align=None, space_after=4, indent=None):
    p = doc.add_paragraph(style=style)
    r = p.add_run(text)
    set_font(r, size=size, bold=bold, color=color)
    if align is not None:
        p.alignment = align
    p.paragraph_format.space_after = Pt(space_after)
    if indent is not None:
        p.paragraph_format.left_indent = Cm(indent)
    return p


def h1(doc, text):
    p = doc.add_heading(level=1)
    r = p.add_run(text)
    set_font(r, size=18, bold=True, color=ACCENT)
    p.paragraph_format.space_before = Pt(18)
    p.paragraph_format.space_after = Pt(8)
    return p


def h2(doc, text):
    p = doc.add_heading(level=2)
    r = p.add_run(text)
    set_font(r, size=14, bold=True, color=ACCENT)
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(6)
    return p


def h3(doc, text):
    p = doc.add_heading(level=3)
    r = p.add_run(text)
    set_font(r, size=12, bold=True, color=RGBColor(0x40, 0x40, 0x40))
    p.paragraph_format.space_before = Pt(8)
    p.paragraph_format.space_after = Pt(4)
    return p


def bullet(doc, text, size=10.5):
    p = doc.add_paragraph(style='List Bullet')
    r = p.add_run(text)
    set_font(r, size=size)
    p.paragraph_format.space_after = Pt(2)
    return p


def numbered(doc, text, size=10.5):
    p = doc.add_paragraph(style='List Number')
    r = p.add_run(text)
    set_font(r, size=size)
    p.paragraph_format.space_after = Pt(2)
    return p


def shade_cell(cell, color):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement('w:shd')
    shd.set(qn('w:val'), 'clear')
    shd.set(qn('w:color'), 'auto')
    shd.set(qn('w:fill'), color)
    tcPr.append(shd)


def table(doc, headers, rows, widths=None, header_bg=HEADER_BG):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = 'Table Grid'
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    hdr = t.rows[0].cells
    for i, htext in enumerate(headers):
        hdr[i].text = ''
        p = hdr[i].paragraphs[0]
        r = p.add_run(htext)
        set_font(r, size=10, bold=True, color=HEADER_FONT)
        shade_cell(hdr[i], header_bg)
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    for row in rows:
        cells = t.add_row().cells
        for i, val in enumerate(row):
            cells[i].text = ''
            p = cells[i].paragraphs[0]
            r = p.add_run(str(val))
            set_font(r, size=10)
    if widths:
        for i, w in enumerate(widths):
            for row in t.rows:
                row.cells[i].width = Cm(w)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    return t


# ---------- 文档 ----------
doc = Document()
# 页边距
for section in doc.sections:
    section.top_margin = Cm(2.0)
    section.bottom_margin = Cm(2.0)
    section.left_margin = Cm(2.2)
    section.right_margin = Cm(2.2)

# ============ 封面 ============
for _ in range(5):
    doc.add_paragraph()
add_para(doc, 'NexusERP 用户操作手册', size=26, bold=True, color=ACCENT,
         align=WD_ALIGN_PARAGRAPH.CENTER, space_after=10)
add_para(doc, 'User Operation Manual', size=14, color=RGBColor(0x80, 0x80, 0x80),
         align=WD_ALIGN_PARAGRAPH.CENTER, space_after=24)
add_para(doc, '面向制造业与贸易型企业的一站式企业管理系统', size=12,
         align=WD_ALIGN_PARAGRAPH.CENTER, space_after=40)
add_para(doc, '版本：V3.0', size=12, align=WD_ALIGN_PARAGRAPH.CENTER, space_after=6)
add_para(doc, '日期：2026年8月', size=12, align=WD_ALIGN_PARAGRAPH.CENTER, space_after=6)
add_para(doc, '作者：王欢', size=12, align=WD_ALIGN_PARAGRAPH.CENTER, space_after=6)

doc.add_page_break()

# ============ 目录 ============
h1(doc, '目录')
add_para(doc, '（请在Word中右键此处，选择“更新域”以生成自动目录）', size=9,
         color=RGBColor(0x80, 0x80, 0x80))
toc_field = OxmlElement('w:fldSimple')
toc_field.set(qn('w:instr'), ' TOC \\o "1-2" \\h \\z \\u ')
p = doc.add_paragraph()
p._p.append(toc_field)
doc.add_page_break()

# ============ 第1章 系统概述 ============
h1(doc, '第1章 系统概述')

h2(doc, '1.1 系统简介')
add_para(doc, 'NexusERP是一套基于RuoYi-Vue-Plus v5.6.0框架开发的企业资源计划（ERP）管理系统，'
              '专为制造业与贸易型企业设计。系统采用前后端分离架构，后端基于Spring Boot + MyBatis-Plus，'
              '前端采用Vue 3 + Element Plus，数据库支持MySQL/PostgreSQL。')
add_para(doc, '系统覆盖企业运营的全部核心业务环节，包括：基础数据、销售、采购、生产、排产调度、'
              '仓储、MES制造执行、质量管理、设备管理、批次追溯、工装、SOP、NCR、数据采集、'
              'IoT物联网、视觉识别、AI智能助手、即时通讯、财务核心、支付、工作流、报表等模块。'
              '系统支持多租户模式，可满足集团化企业多公司、多工厂的管理需求。')

h2(doc, '1.2 核心模块总览')
add_para(doc, '下表展示了NexusERP的十五大核心模块：')
table(doc,
      ['序号', '模块名称', '功能概述'],
      [
          ['1', '基础数据', '物料、物料分类、BOM、工艺路线、工序、客户、供应商、车间工位、计量单位、序列号规则'],
          ['2', '销售管理', '询价、报价、合同、客户订单、发货、发票、退货、价格管理、信用检查、定价引擎'],
          ['3', '采购管理', '部门请购、采购申请、合同、订单、收货、退货、供应商评估、价格比较、低库存自动请购'],
          ['4', '请购管理', '内部请购申请、审批流转、请购转采购订单'],
          ['5', '生产管理', '生产计划、工单、物料需求、返工、委外、产能日历、工单资源、完工入库、倒冲'],
          ['6', '排产调度', '班次、班组、工人档案、排班、打卡、假期、考勤、人脸打卡、移动端'],
          ['7', 'MES制造执行', 'MBOM、MES工单、工序派工、流转卡、扫码报工、SN、追溯、安灯、进站出站'],
          ['8', '质量管理与SPC', 'IQC来料、IPQC过程、OQC出货检验、控制图、过程能力分析'],
          ['9', '设备管理', '设备台账、保养、维修、点检、OEE、备件、巡检、设备监控'],
          ['10', '批次/工装/SOP/NCR', '批次管理、工装模治具、电子SOP、不合格品NCR与返工、8D报告'],
          ['11', 'IoT物联网', '产品、设备、MQTT接入、实时数据SSE、告警、数据采集'],
          ['12', '视觉识别', '人脸底库、1:1/1:N识别、物料OCR、标签拍照解析'],
          ['13', 'AI智能助手', '对话、模型、知识库、知识图谱、数据查询Agent、文生图、MCP工具'],
          ['14', 'IM/财务/支付/工作流/报表', '即时通讯、总账、应收应付、固定资产、成本、支付、流程引擎、积木报表'],
          ['15', '系统管理', '用户、角色、菜单、部门、字典、参数、租户、登录设备、社交账号、系统监控'],
      ],
      widths=[1.2, 4.0, 11.0])

h2(doc, '1.3 系统登录')
add_para(doc, '打开浏览器，访问系统地址：http://nexuserp.website/index')
add_para(doc, '登录步骤：')
numbered(doc, '在登录页面输入用户名和密码')
numbered(doc, '选择所属租户（多租户模式下需要选择）')
numbered(doc, '输入图片验证码或滑动验证')
numbered(doc, '点击“登录”按钮进入系统')
add_para(doc, '温馨提示：首次登录后请立即修改初始密码。进入“个人中心” > “修改密码”。')

h2(doc, '1.4 界面布局')
add_para(doc, 'NexusERP采用经典的后台管理系统布局：')
bullet(doc, '左侧导航栏：展示所有菜单，支持多级折叠展开，可收起为图标模式')
bullet(doc, '顶部工具栏：系统消息、全局搜索、AI智能助手、快速切换租户、项目文档、个人中心')
bullet(doc, '中央工作区：当前操作页面的主要内容展示区域')
bullet(doc, '多标签页：支持多个页面同时打开，可关闭、刷新、关闭其他')
bullet(doc, '分页组件：列表页面底部显示分页信息')

h2(doc, '1.5 通用操作说明')
table(doc,
      ['操作', '说明'],
      [
          ['新增', '点击“新增”按钮，填写表单后保存'],
          ['修改', '选中记录，点击“修改”按钮，修改后保存'],
          ['删除', '选中记录，点击“删除”确认删除。部分数据存在关联保护'],
          ['导出', '点击“导出”将列表数据导出为Excel'],
          ['搜索与重置', '输入条件点击“搜索”过滤，点击“重置”清空'],
          ['状态激活/停用', '切换记录启用/停用状态，停用后不可在下拉框中选择'],
      ],
      widths=[3.0, 13.0])

h2(doc, '1.6 版本说明')
add_para(doc, '本手册对应NexusERP V3.0版本。V3.0在V2.0基础上新增了财务核心（总账/应收应付/固定资产/'
              '成本/期末关账）、IoT物联网、视觉识别、即时通讯、MES P0系列（批次/工装/SOP/NCR/进站出站）、'
              '质量管理三链路（IQC/IPQC/OQC）、SPC统计过程控制、AI文生图与知识图谱等功能。'
              '详细更新内容见附录B。')

# ============ 第2章 基础数据管理 ============
h1(doc, '第2章 基础数据管理')
add_para(doc, '基础数据是整个ERP系统的基石，所有业务模块均围绕基础数据运转。')

h2(doc, '2.1 物料管理')
add_para(doc, '物料是ERP系统中最核心的基础数据。物料管理页面采用左侧分类树 + 右侧列表的经典布局。')
add_para(doc, '创建物料的步骤：')
numbered(doc, '点击菜单“基础数据” > “物料管理”')
numbered(doc, '点击“新增”，打开物料新增表单')
numbered(doc, '填写基本信息：物料编码、名称、规格、计量单位、分类')
numbered(doc, '设置物料属性：类型（原材料/半成品/成品/辅料/包装材料）、序列号管理、批次管理')
numbered(doc, '填写采购信息：默认供应商、采购提前期、最小采购量')
numbered(doc, '填写仓储信息：默认仓库、安全库存、最高库存、重订点')
numbered(doc, '点击“保存”完成物料创建')
add_para(doc, '物料管理支持批量导入导出，支持自定义属性字段（物料分类下配置属性模板），'
              '支持按分类快速筛选库存。')

h2(doc, '2.2 物料分类')
add_para(doc, '物料分类采用树形结构，支持多级分类：')
bullet(doc, '一级分类：原材料、半成品、成品、辅料、包装材料')
bullet(doc, '二级分类：如原材料下分钢材、塑料、电子元器件')
bullet(doc, '三级分类：如钢材下分不锈钢、碳钢、合金钢')
add_para(doc, '操作：左侧树右键可新增子分类、修改名称、删除。含物料的分类无法删除。'
              '分类可配置属性模板，物料新增时按分类带出自定义属性。')

h2(doc, '2.3 工序管理')
add_para(doc, '工序管理用于定义生产过程中的各道工序。')
add_para(doc, '工序类型：')
bullet(doc, '加工工序：切削、成型、磨削等加工操作')
bullet(doc, '装配工序：将多个零部件组装成半成品或成品')
bullet(doc, '检验工序：质量检测、性能测试')
bullet(doc, '包装工序：包装、贴标、入箱')
add_para(doc, '搜索字段：工序编码、工序名称、工序类型、状态。')
add_para(doc, '创建工序的步骤：')
numbered(doc, '点击“基础数据” > “工序管理”')
numbered(doc, '点击“新增”')
numbered(doc, '填写工序编码（如OP-001）')
numbered(doc, '填写工序名称（如“数控车削”）')
numbered(doc, '选择工序类型')
numbered(doc, '设置默认工时（分钟）')
numbered(doc, '填写备注说明')
numbered(doc, '点击“保存”')

h2(doc, '2.4 工艺路线管理')
add_para(doc, '工艺路线定义产品从原材料到成品的加工顺序，是MES工序执行、工时统计、成本核算的基础。')
add_para(doc, '工艺路线管理支持：')
bullet(doc, '路线版本管理：同一产品可维护多个版本的工艺路线，激活版本用于生产')
bullet(doc, '工序步骤编排：按顺序添加工序，设置工序名称、工时、费用费率、作业指导说明（描述文本）')
bullet(doc, '工序与SOP绑定：在“电子SOP”模块中将作业指导书绑定到具体工序')
bullet(doc, '路由复制：基于现有路线复制生成新版本')
add_para(doc, 'MES工单下达时自动快照工艺路线，生成工序步骤列表，生产过程中路线变更不影响已下达工单。')

h2(doc, '2.5 BOM物料清单')
add_para(doc, 'BOM（Bill of Materials）是制造业ERP的核心数据。')

h3(doc, '2.5.1 BOM类型')
table(doc,
      ['BOM类型', '缩写', '说明', '应用场景'],
      [
          ['工程BOM', 'EBOM', '产品设计阶段的物料清单', '产品设计、研发评审'],
          ['制造BOM', 'MBOM', '生产制造阶段，考虑工艺流程', '生产计划、MES执行'],
          ['销售BOM', 'SBOM', '销售配置阶段，含客户选配件', '销售报价、订单配置'],
          ['成本BOM', 'CBOM', '用于成本核算', '成本分析、定价决策'],
      ],
      widths=[2.2, 2.0, 6.0, 5.0])

h3(doc, '2.5.2 BOM列表页')
add_para(doc, '搜索字段：BOM编码、父物料编码/名称、BOM类型、BOM版本、状态。')
add_para(doc, '工具栏操作：')
table(doc,
      ['序号', '操作', '功能说明'],
      [
          ['1', '新增', '创建新的BOM记录'],
          ['2', '修改', '修改当前BOM（仅草稿可修改）'],
          ['3', '查看', '查看BOM详情，含物料明细、BOM树、成本卷积'],
          ['4', '激活版本', '将当前版本设为激活状态'],
          ['5', '验证BOM', '检查数据完整性，检查循环引用'],
          ['6', 'BOM树', '树形结构展示多级组成关系'],
          ['7', '复制BOM', '基于现有BOM创建副本，自动新版本号'],
          ['8', 'BOM比较', '选择两个BOM进行差异对比'],
          ['9', 'Where-Used反查', '查看某物料被哪些BOM引用'],
          ['10', '导出', '将BOM数据导出为Excel'],
      ],
      widths=[1.2, 3.0, 12.0])

h3(doc, '2.5.3 新增BOM')
add_para(doc, '创建BOM的步骤：')
numbered(doc, '点击“新增”，打开BOM新增表单')
numbered(doc, '选择父物料：从物料列表中选择产品')
numbered(doc, '选择BOM类型（EBOM/MBOM/SBOM/CBOM）')
numbered(doc, '设置BOM版本号（默认V1.0）')
numbered(doc, '在物料明细表格中添加子物料：物料、用量、损耗率、工序步骤、替代料')
numbered(doc, '检查BOM数据完整性')
numbered(doc, '点击“保存”，状态默认为“草稿”')

h3(doc, '2.5.4 BOM表单页')
add_para(doc, 'BOM表单页包含基本信息区域和四个功能标签页。')
add_para(doc, '标签页1：物料明细')
bullet(doc, '子物料编码/名称/规格')
bullet(doc, '用量：生产单位父物料所需的子物料数量')
bullet(doc, '损耗率（%）：实际需求量 = 用量 x (1 + 损耗率)')
bullet(doc, '工序步骤：关联到具体生产工序，用于MES物料投放')
bullet(doc, '替代料标识：标记是否存在替代料')
add_para(doc, '标签页2：BOM树形视图——以可视化树形图展示BOM的多级组成结构，支持展开/折叠。')
add_para(doc, '标签页3：缩进式BOM——以缩进列表形式展示多级BOM，显示层级号、物料编码、名称、规格、用量、单位。')
add_para(doc, '标签页4：成本卷积——自动计算BOM的多级成本卷积：')
bullet(doc, '单层材料成本 = 子物料单价 x 用量 x (1 + 损耗率)')
bullet(doc, '单层人工成本 = 人工费率 x 工时')
bullet(doc, '单层制造成本 = 机器费率 x 工时')
bullet(doc, '总成本 = 各层材料成本 + 人工成本 + 制造成本（自底向上逐层累加）')

h3(doc, '2.5.5 替代料管理')
add_para(doc, '在BOM物料明细中可为每个子物料配置替代料：')
table(doc,
      ['替代类型', '英文', '说明'],
      [
          ['完全替代', 'FULL', '替代料可完全替换原物料，功能性能等同'],
          ['部分替代', 'PARTIAL', '可按一定比例替换，需设置替代比例'],
          ['互换替代', 'INTERCHANGEABLE', '原物料与替代料可互相替换，根据库存自动选择'],
      ],
      widths=[3.0, 3.5, 9.0])
add_para(doc, '替代料可设置有效期范围（开始日期至结束日期），超出有效期自动失效。')

h3(doc, '2.5.6 BOM复制')
numbered(doc, '选中要复制的BOM，点击“复制BOM”')
numbered(doc, '系统自动生成新的BOM编码和版本号')
numbered(doc, '复制内容包括：基本信息、物料明细、替代料关系')
numbered(doc, '复制后状态为“草稿”，可修改后激活')

h3(doc, '2.5.7 BOM比较')
numbered(doc, '点击“BOM比较”')
numbered(doc, '分别选择两个BOM')
numbered(doc, '系统从四个维度对比：')
bullet(doc, '物料差异：新增、删除、共有的子物料')
bullet(doc, '用量差异：同一物料的用量变化')
bullet(doc, '损耗率差异：同一物料的损耗率变化')
bullet(doc, '成本差异：各层成本的变化对比')

h3(doc, '2.5.8 Where-Used反查')
numbered(doc, '点击“Where-Used反查”')
numbered(doc, '选择要查询的物料')
numbered(doc, '系统展示所有引用该物料的BOM列表')
add_para(doc, '该功能在物料变更、停产替代等场景下非常有用，可快速评估影响范围。')

h2(doc, '2.6 物料-供应商关系')
bullet(doc, '物料与供应商的绑定关系')
bullet(doc, '供应价格、供应提前期、最小起订量')
bullet(doc, '优先级设置：同一物料多个供应商时设置优先采购顺序（默认供应商唯一约束）')
bullet(doc, '供应商资质证书管理')
bullet(doc, '物料-供应商价格对比报表')

h2(doc, '2.7 客户管理')
bullet(doc, '客户基本信息：编码、名称、类型、行业')
bullet(doc, '联系信息：联系人、电话、邮箱、地址')
bullet(doc, '业务信息：信用额度、付款条件、开票信息')
bullet(doc, '收货地址：支持多个收货地址')
bullet(doc, '客户Logo上传（品牌形象展示）')

h2(doc, '2.8 供应商管理')
bullet(doc, '供应商基本信息：编码、名称、类型、经营范围')
bullet(doc, '联系信息：联系人、电话、邮箱、地址')
bullet(doc, '业务信息：付款条件、结算方式、税务信息')
bullet(doc, '供应商评级：A/B/C/D四级评估体系')

h2(doc, '2.9 车间与工位')
bullet(doc, '车间管理：定义生产车间编码、名称、负责人、产线类型')
bullet(doc, '工位管理：在车间下定义工位，含编码、名称、设备绑定、产能')
bullet(doc, '支持车间-工位的层级结构展示')

h2(doc, '2.10 计量单位')
bullet(doc, '基本单位：个、件、千克、米、升等')
bullet(doc, '单位换算：定义不同单位间的换算关系，如1箱=12个')
bullet(doc, '单位分组：长度、重量、体积、数量等分组')

h2(doc, '2.11 序列号规则')
bullet(doc, '定义序列号生成规则：前缀 + 日期 + 流水号')
bullet(doc, '支持MOD10/MOD11校验位算法')
bullet(doc, '物料按规则自动生成序列号（SN）')

h2(doc, '2.12 序列号/批次管理')
bullet(doc, '库存序列号管理（wm_sn）：为每个产品分配唯一序列号，支持手动录入和自动生成')
bullet(doc, '库存批次管理（wm_batch）：按批次号管理物料，支持生产日期、有效期、批次属性')
bullet(doc, '追溯查询：通过序列号或批次号查询物料全生命周期')

# ============ 第3章 销售管理 ============
h1(doc, '第3章 销售管理')
add_para(doc, '销售管理模块覆盖从客户询价到发票开具的完整销售流程。')

h2(doc, '3.1 客户询价')
bullet(doc, '询价单包含：客户信息、询价物料、需求数量、期望交货日期、备注')
bullet(doc, '支持将询价单直接转为销售报价单')
bullet(doc, '询价历史记录可作为客户跟踪和市场分析依据')

h2(doc, '3.2 销售报价')
bullet(doc, '报价单包含：客户信息、报价物料、单价、折扣、有效期、付款条件')
bullet(doc, '支持多级审批，报价超过一定金额需上级审批')
bullet(doc, '审批通过后可转为销售合同或销售订单')

h2(doc, '3.3 销售合同')
bullet(doc, '合同基本信息：合同编号、客户名称、合同金额、签订日期、有效期')
bullet(doc, '合同明细：物料、数量、单价、交货计划')
bullet(doc, '合同执行跟踪：已交货金额、已回款金额、未执行金额')
bullet(doc, '合同变更管理：支持合同修改、补充协议、终止')

h2(doc, '3.4 销售订单（客户订单）')
add_para(doc, '订单头：订单编号、客户、订单日期、交货日期、币种、汇率。')
add_para(doc, '订单明细：物料、数量、单价、金额、税率、备注。')
add_para(doc, '状态流转：草稿 > 待审批 > 已确认 > 部分发货 > 已发货 > 已完成 > 已取消。')
add_para(doc, '高级能力：')
bullet(doc, '信用检查：订单金额超出客户信用额度时自动拦截')
bullet(doc, '定价引擎：按价格表自动匹配单价与折扣')
bullet(doc, 'Flowable审批流：提交后自动路由审批')
bullet(doc, '销售订单行联动：行项目可关联生产工单（MO）与采购订单（PO）')
bullet(doc, '销售执行报表 + 客户排名')

h2(doc, '3.5 销售发票')
bullet(doc, '从销售订单或发货单生成发票')
bullet(doc, '发票信息：发票号码、开票日期、金额、税额、发票类型')
bullet(doc, '发票状态：待开票、已开票、已作废、已红冲')
bullet(doc, '发票核销：记录发票与回款的匹配关系')

h2(doc, '3.6 销售退货')
bullet(doc, '退货单关联原销售订单，自动带出原订单信息')
bullet(doc, '支持部分退货和全部退货')
bullet(doc, '退货原因：质量问题、规格不符、客户变更、其他')
bullet(doc, '退货审批流程：支持多级审批')
bullet(doc, '退货入库：审批通过后自动生成入库单')

h2(doc, '3.7 价格管理')
bullet(doc, '价格表管理：定义不同客户群组的价格策略')
bullet(doc, '折扣管理：数量折扣、金额折扣、客户特殊折扣')
bullet(doc, '价格历史：记录价格变更历史，支持价格趋势分析')
bullet(doc, '价格审批：价格调整需经过审批流程')

h2(doc, '3.8 销售报表')
bullet(doc, '销售额统计：按客户、产品、区域、时间维度统计')
bullet(doc, '销售趋势分析：月度、季度、年度销售趋势图')
bullet(doc, '客户分析：客户贡献度排名、购买频次分析')
bullet(doc, '产品分析：畅销产品排名、产品利润率分析')

# ============ 第4章 采购管理 ============
h1(doc, '第4章 采购管理')
add_para(doc, '采购管理模块覆盖从采购申请到供应商评估的完整采购流程。')

h2(doc, '4.1 部门请购')
bullet(doc, '部门请购单：业务部门提出物料需求')
bullet(doc, '请购单头：请购部门、请购人、需求日期、请购原因')
bullet(doc, '请购明细：物料、数量、期望交货日期、建议供应商')

h2(doc, '4.2 采购申请')
add_para(doc, '采购申请支持两种方式：')
bullet(doc, '手动创建：由采购人员手动填写采购申请单')
bullet(doc, '自动生成：系统根据库存预警自动生成，当库存低于重订点时触发')
add_para(doc, '采购申请包含：物料编码、名称、申请数量、需求日期、建议供应商、备注。')
add_para(doc, '请购单状态流转：')
table(doc,
      ['状态', '说明', '可执行操作'],
      [
          ['草稿', '刚创建', '修改、删除、提交审批'],
          ['待审批', '已提交等待审批', '撤回、查看'],
          ['审批通过', '审批已通过', '转采购订单、查看'],
          ['已转采购订单', '已生成采购订单', '查看'],
          ['审批拒绝', '审批未通过', '修改后重新提交、删除'],
      ],
      widths=[3.0, 4.0, 9.0])

h2(doc, '4.3 采购合同')
bullet(doc, '框架合同：与供应商签订长期采购框架协议')
bullet(doc, '一次性合同：针对单次采购签订')
bullet(doc, '批次合同：按批次交货，支持多次交货计划')
bullet(doc, '合同转采购订单联动')

h2(doc, '4.4 采购订单')
bullet(doc, '订单头：订单编号、供应商、订单日期、交货日期、币种')
bullet(doc, '订单明细：物料、数量、单价、金额、税率')
bullet(doc, '状态：草稿、已审批、部分收货、已收货、已完成、已取消（9态完整状态机）')
bullet(doc, '支持从请购单自动转换生成')
bullet(doc, 'Flowable审批流集成')
bullet(doc, '订单行收货差异处理')

h2(doc, '4.5 采购退货')
bullet(doc, '采购退货单关联原采购订单')
bullet(doc, '支持部分退货和全部退货')
bullet(doc, '退货原因：质量不合格、数量多余、规格不符')
bullet(doc, '审批通过后自动生成出库单')

h2(doc, '4.6 供应商评估')
add_para(doc, '系统提供全面的供应商评估体系：')
bullet(doc, '评估等级：A（优秀）、B（良好）、C（合格）、D（不合格）')
bullet(doc, '评估维度：交货及时率、质量合格率、价格竞争力、服务响应速度、合同履行率')
bullet(doc, '雷达图展示：以雷达图形式直观展示各维度评分')
bullet(doc, '评估周期：支持月度、季度、年度评估')

h2(doc, '4.7 采购价格比较')
bullet(doc, '选择同一物料的多个供应商报价对比')
bullet(doc, '自动计算价格差异、排名')
bullet(doc, '支持历史价格趋势对比')
bullet(doc, '生成比价报告，辅助采购决策')

h2(doc, '4.8 采购报表')
bullet(doc, '采购额统计：按供应商、物料、时间维度')
bullet(doc, '采购趋势分析：采购金额趋势图')
bullet(doc, '供应商交货分析：交货及时率、质量合格率')
bullet(doc, '采购周期分析：从申请到收货的平均周期')

# ============ 第5章 请购管理 ============
h1(doc, '第5章 请购管理')
add_para(doc, '请购管理模块用于处理企业内部的物料需求申请，经审批后转为采购订单。')

h2(doc, '5.1 创建请购单')
numbered(doc, '点击“请购管理” > “请购单”')
numbered(doc, '点击“新增”')
numbered(doc, '填写请购单头：请购部门、请购人、需求日期、请购原因')
numbered(doc, '添加请购物料：选择物料、填写数量、期望交货日期、建议供应商')
numbered(doc, '点击“保存”')

h2(doc, '5.2 请购单状态流转')
add_para(doc, '请购单状态流转与采购申请一致：草稿 > 待审批 > 审批通过 > 已转采购订单（或审批拒绝）。')

h2(doc, '5.3 请购转采购订单')
numbered(doc, '选择状态为“审批通过”的请购单')
numbered(doc, '点击“转采购订单”')
numbered(doc, '确认采购信息：供应商、价格、交货日期')
numbered(doc, '系统自动生成采购订单，关联原请购单')

# ============ 第6章 生产管理 ============
h1(doc, '第6章 生产管理')
add_para(doc, '生产管理模块覆盖从生产计划到生产执行的全流程管理。')

h2(doc, '6.1 生产计划')
bullet(doc, '基于销售订单和库存情况自动生成生产计划')
bullet(doc, 'BOM展开：自动根据BOM展开物料需求')
bullet(doc, '物料需求运算：考虑现有库存、在途采购、安全库存')
bullet(doc, '支持手动调整生产计划')
bullet(doc, '生产计划版本管理')

h2(doc, '6.2 生产工单')
bullet(doc, '生产工单包含：产品信息、计划数量、开始/结束日期、优先级')
bullet(doc, '基于Flowable的审批流程：提交 > 审批 > 下达')
bullet(doc, '状态机：草稿、待审批、已审批、已下达、生产中、已完工、已关闭（7态+转换矩阵）')
bullet(doc, '工单状态原子更新防并发')

h2(doc, '6.3 物料需求')
bullet(doc, '自动计算生产所需物料的需求量')
bullet(doc, '考虑BOM用量、损耗率、现有库存、在途采购')
bullet(doc, '缺料预警：当物料不足时自动提醒')
bullet(doc, '支持生成采购申请')

h2(doc, '6.4 返工订单')
bullet(doc, '返工订单管理：创建、审批、下达、跟踪')
bullet(doc, '关联原生产工单')
bullet(doc, '返工完成后入库（MQ异步处理）')

h2(doc, '6.5 委外加工')
bullet(doc, '委外加工订单管理：创建、审批、下达、跟踪')
bullet(doc, '委外物料发放与回收管理')
bullet(doc, '委外加工费用管理')
bullet(doc, '委外质量检验与入库')

h2(doc, '6.6 产能日历')
bullet(doc, '产能日历管理：定义产能与占用/释放')
bullet(doc, '工单资源分配（设备/工装/人力）')
bullet(doc, '排产冲突检测')

h2(doc, '6.7 完工入库与倒冲')
bullet(doc, '完工入库单号自动生成 + 库位选择')
bullet(doc, '生产领料可用库存实时展示')
bullet(doc, '倒冲模式：完工后MQ异步扣减物料')
bullet(doc, 'MES工单完工回写ERP（ErpWorkOrderCompletedConsumer）')

h2(doc, '6.8 生产报表')
bullet(doc, '生产进度报表：工单完成率、生产进度跟踪')
bullet(doc, '产能分析：设备利用率、产线产能趋势')
bullet(doc, '生产成本报表：实际成本与计划成本对比')
bullet(doc, '质量统计：良品率、不良品分析')

# ============ 第7章 排产调度 ============
h1(doc, '第7章 排产调度')
add_para(doc, '排产调度模块管理生产人员的班次、排班和考勤。')

h2(doc, '7.1 班次管理')
bullet(doc, '定义班次类型：早班、中班、晚班、全天班')
bullet(doc, '设置班次时间：上班时间、下班时间、休息时间')
bullet(doc, '支持跨天班次设置')

h2(doc, '7.2 班组管理')
bullet(doc, '创建班组：班组名称、班组长、成员列表')
bullet(doc, '分配班次：为班组分配默认班次')
bullet(doc, '班组成员管理：添加/移除成员')

h2(doc, '7.3 工人档案')
bullet(doc, '工人基本信息：工号、姓名、部门、岗位、技能等级')
bullet(doc, '工人技能矩阵：记录可操作的工序和设备')
bullet(doc, '工人产能记录：跟踪生产效率')

h2(doc, '7.4 排班计划')
bullet(doc, '制定月度/周度排班计划')
bullet(doc, '支持自动排班和手动调整')
bullet(doc, '排班规则：连续工作天数限制、休息日安排')
bullet(doc, '排班发布与通知')

h2(doc, '7.5 班组排班模板')
bullet(doc, '周/月排班模板维护')
bullet(doc, '计划-班组关联')
bullet(doc, '员工排班明细查看')

h2(doc, '7.6 打卡管理')
bullet(doc, '支持多种打卡方式：指纹、人脸、IC卡、手机APP')
bullet(doc, '打卡规则：上班打卡、下班打卡、外勤打卡（打卡规则配置）')
bullet(doc, '异常打卡处理：迟到、早退、缺卡、补卡申请')

h2(doc, '7.7 假期管理')
bullet(doc, '假期类型：事假、病假、年假、婚假、产假等')
bullet(doc, '假期额度管理：设置各类假期天数额度')
bullet(doc, '请假申请与审批流程')
bullet(doc, '假期日历管理：设置公司假日和调休日')

h2(doc, '7.8 考勤记录')
bullet(doc, '查看个人考勤记录：打卡时间、工作时长、加班时长')
bullet(doc, '部门考勤汇总：按部门统计出勤率、迟到率')
bullet(doc, '考勤异常报表：迟到、早退、缺勤、加班统计')

h2(doc, '7.9 移动端考勤')
bullet(doc, '手机APP打卡：支持GPS定位打卡和WiFi打卡')
bullet(doc, '移动端请假、补卡申请')
bullet(doc, '移动端查看个人考勤记录和排班信息')

h2(doc, '7.10 人脸打卡')
bullet(doc, '人脸底库管理：注册员工人脸照片（视觉识别模块）')
bullet(doc, '人脸1:1验证：打卡时进行人脸比对')
bullet(doc, '视觉服务不可用时自动降级（考勤置NONE，由补跑任务兜底）')

# ============ 第8章 MES制造执行 ============
h1(doc, '第8章 MES制造执行')
add_para(doc, 'MES（Manufacturing Execution System）制造执行模块位于ERP计划层与设备控制层之间，'
              '负责车间级生产执行管理，实现从计划层到执行层的闭环管理。')

h2(doc, '8.1 MBOM管理')
h3(doc, '8.1.1 MBOM与ERP BOM的关系')
add_para(doc, 'ERP BOM主要用于成本核算和物料需求计算，而MBOM用于MES制造执行，'
              '定义了实际生产过程中的物料投放和工序关联。')
add_para(doc, '一个ERP BOM对应一个MBOM，MBOM在工单下达时自动快照，确保生产过程中数据的一致性。')
h3(doc, '8.1.2 MBOM列表页')
add_para(doc, '列表字段：MBOM ID、父物料、BOM版本、工艺路线、MBOM版本、状态。')
h3(doc, '8.1.3 新增MBOM')
numbered(doc, '选择关联的ERP BOM')
numbered(doc, '系统自动带出BOM的物料明细')
numbered(doc, '调整工序关联：为每个子物料指定投放工序')
numbered(doc, '设置MBOM版本号')
numbered(doc, '验证数据完整性：确保所有物料已关联工序')
numbered(doc, '点击“保存”')
add_para(doc, '验证规则：每个子物料必须关联至少一个工序；父物料必须已存在激活版本的BOM。')

h2(doc, '8.2 MES工单管理')
h3(doc, '8.2.1 创建MES工单')
numbered(doc, '点击“MES制造执行” > “MES工单”')
numbered(doc, '点击“新增”')
numbered(doc, '选择产品（父物料）')
numbered(doc, '选择BOM版本和工艺路线版本')
numbered(doc, '填写计划数量、计划开始/结束日期')
numbered(doc, '点击“保存”')
h3(doc, '8.2.2 工单下达')
add_para(doc, '工单审批通过后进行下达操作。下达时系统自动执行以下操作：')
bullet(doc, '自动快照MBOM数据，生成工单专属的物料清单')
bullet(doc, '自动快照工艺路线，生成工序步骤列表')
bullet(doc, '生成工序流转卡（可选）')
bullet(doc, '锁定MBOM和工艺路线，防止生产过程中被修改')
h3(doc, '8.2.3 工单详情页')
add_para(doc, '工单详情页包含四个标签页：')
bullet(doc, '基本信息：工单编号、产品、数量、日期、状态')
bullet(doc, '工序步骤：展示工艺路线的所有工序，包含执行状态')
bullet(doc, '物料明细（MBOM）：展示快照后的物料清单')
bullet(doc, '审批流程：显示审批记录')
add_para(doc, '工单状态流转：草稿 > 待审批 > 已审批 > 已下达 > 生产中 > 已完工 > 已关闭。')

h2(doc, '8.3 工序派工')
bullet(doc, '为工序分配工人/工位')
bullet(doc, '工序派工明细查看')
bullet(doc, '扫码执行（ScanExecute）：工位扫码开工')

h2(doc, '8.4 流转卡管理')
h3(doc, '8.4.1 创建流转卡')
add_para(doc, '流转卡是MES执行的核心载体，跟随产品在各工序间流转。')
bullet(doc, '从已下达的MES工单自动生成流转卡')
bullet(doc, '流转卡包含：卡号、产品信息、数量、工序列表、当前工序')
bullet(doc, '支持批量生成多张流转卡（数量校验）')
h3(doc, '8.4.2 流转卡详情与工序报工')
add_para(doc, '流转卡的执行流程分为四个步骤：')
numbered(doc, '开工：操作员扫码或手动点击“开工”，记录实际开工时间')
numbered(doc, '完工报工：填写完成数量、良品数、不良品数，点击“完工报工”')
numbered(doc, '流转卡完工：所有工序完成后，点击“流转卡完工”')
numbered(doc, '关闭：确认数据无误后关闭流转卡，数据归档')

h2(doc, '8.5 进站/出站校验')
bullet(doc, '任务进站/出站/跳站（5类规则校验）')
bullet(doc, '工序流程指引配置（ProStepGuide）：工序步骤作业指引')
bullet(doc, '工序校验规则配置（ProStepCheckRule）：进站/出站条件校验')

h2(doc, '8.6 SN管理')
h3(doc, '8.6.1 批量生成SN')
bullet(doc, '根据规则自动生成序列号：前缀 + 日期 + 流水号')
bullet(doc, '支持批量生成，一次生成多个SN')
bullet(doc, '支持手动录入和扫码录入')
h3(doc, '8.6.2 SN生命周期')
table(doc,
      ['状态', '说明'],
      [
          ['生产中', 'SN已创建，正在生产过程中'],
          ['已完成', '生产完成，等待发货'],
          ['已发货', '已随销售订单发货'],
          ['已报废', '生产或检验不合格，已报废处理'],
      ],
      widths=[3.0, 13.0])

h2(doc, '8.7 组件追溯')
h3(doc, '8.7.1 SN追溯详情页')
add_para(doc, '通过SN号查看产品的完整追溯信息：')
add_para(doc, '前向追溯（从原材料到成品）：')
bullet(doc, '查看该产品使用了哪些原材料批次')
bullet(doc, '查看各工序的执行记录：操作员、时间、设备')
bullet(doc, '查看质量检验记录')
add_para(doc, '后向追溯（从成品到客户）：')
bullet(doc, '查看该产品发往哪个客户')
bullet(doc, '查看发货单和物流信息')
bullet(doc, '查看客户反馈和售后记录')

h2(doc, '8.8 安灯告警')
bullet(doc, '安灯呼叫：异常时一键呼叫（物料/设备/质量/其他）')
bullet(doc, 'SSE实时推送：看板实时显示安灯状态')
bullet(doc, '安灯历史记录查询')
bullet(doc, 'IoT设备联动：设备数据异常自动触发安灯')

# ============ 第9章 质量管理与SPC ============
h1(doc, '第9章 质量管理与SPC')
add_para(doc, '质量管理模块覆盖来料、过程、出货三大检验环节，SPC提供统计过程控制能力。')

h2(doc, '9.1 IQC来料检验')
bullet(doc, '来料检验单管理：检验单号、供应商、物料、数量')
bullet(doc, '判定结果：合格/不合格/特采')
bullet(doc, '自动创建：ERP收货审批通过后MQ自动创建IQC任务')
bullet(doc, '检验记录回写ERP质检状态（ErpIqcCompletedConsumer）')

h2(doc, '9.2 IPQC过程检验')
bullet(doc, '过程检验单管理：关联工单/工序')
bullet(doc, '关键工序报工触发检验（工单完工门禁）')
bullet(doc, '检验项目录入：测量值、判定')

h2(doc, '9.3 OQC出货检验')
bullet(doc, '出货检验单管理：关联销售出库单')
bullet(doc, '销售出库审批通过后自动创建OQC任务')
bullet(doc, '检验结果记录')

h2(doc, '9.4 质量追溯')
bullet(doc, '批次→检验记录追溯')
bullet(doc, '不合格品处理流程（MRB/NCR）')

h2(doc, '9.5 SPC控制图')
bullet(doc, '控制图定义：X-bar/R等，UCL/LCL/CL')
bullet(doc, '数据点管理（SPC数据点）')
bullet(doc, 'OOC超限自动触发安灯（SnailJob定时检查）')
bullet(doc, '过程能力分析（Cp/Cpk）')
bullet(doc, '控制图趋势分析前端可视化')

# ============ 第10章 设备管理 ============
h1(doc, '第10章 设备管理')
add_para(doc, '设备管理模块（MES eq）提供设备的全生命周期管理。')

h2(doc, '10.1 设备台账')
bullet(doc, '设备编号、名称、型号、位置、状态')
bullet(doc, '设备状态跟踪（运行/停机/维修/保养）')
bullet(doc, '设备与工位/产线关联')

h2(doc, '10.2 保养计划')
bullet(doc, '定期维护任务自动提醒（SnailJob + SSE + 安灯联动）')
bullet(doc, '保养记录管理')

h2(doc, '10.3 维修记录')
bullet(doc, '故障报修、维修记录、故障分析')
bullet(doc, '维修成本统计')

h2(doc, '10.4 设备点检')
bullet(doc, '点检执行 + 异常联动安灯')
bullet(doc, '点检项目配置')

h2(doc, '10.5 设备OEE')
bullet(doc, 'OEE综合效率分析（稼动率 x 性能 x 良率）')
bullet(doc, 'OEE看板（跨租户聚合）')

h2(doc, '10.6 备件管理')
bullet(doc, '备件台账 + 库存流水')
bullet(doc, '备件领用/归还')

h2(doc, '10.7 设备巡检')
bullet(doc, '巡检路线/任务管理')
bullet(doc, '巡检记录 + 异常处理')

h2(doc, '10.8 设备监控')
bullet(doc, '设备监控大屏')
bullet(doc, '工单联动：设备状态与生产工单关联')

# ============ 第11章 批次/工装/SOP/NCR ============
h1(doc, '第11章 批次/工装/SOP/NCR')
add_para(doc, '本章介绍MES P0系列新增的四个能力模块。')

h2(doc, '11.1 批次管理')
bullet(doc, '批次主档 + 状态机（IN_STOCK/IN_USE/CONSUMED/QUARANTINED/SCRAPPED）')
bullet(doc, '工单-批次消耗（FEFO先进先出推荐）')
bullet(doc, 'SN-批次绑定 + 追溯链')
bullet(doc, '批次隔离/放行/守卫')
bullet(doc, 'IQC结果钩子：检验不合格自动隔离批次')

h2(doc, '11.2 工装/模治具管理')
bullet(doc, '工装台账 + 寿命计数')
bullet(doc, '保养计划/记录（计数归零）')
bullet(doc, '使用/领用归还记录')

h2(doc, '11.3 电子SOP')
bullet(doc, 'SOP文档库 + 版本管理')
bullet(doc, '工序-SOP绑定（多对多，版本指定）')
bullet(doc, '查阅记录（含电子签名预留）')
bullet(doc, '工位推送：进站触发SSE推送SOP到工位')

h2(doc, '11.4 NCR不合格品与返工')
bullet(doc, 'NCR单据 + 生命周期（OPEN→REVIEW→DISPOSITION→IN_REWORK→CLOSED）')
bullet(doc, '缺陷明细 + 缺陷代码库（分层）')
bullet(doc, '返工工单派生（关联原工单）')
bullet(doc, '8D报告（D1-D8）')
bullet(doc, '触发点：IQC/IPQC/OQC检验不合格 + 过程异常')

# ============ 第12章 数据采集与IoT物联网 ============
h1(doc, '第12章 数据采集与IoT物联网')
add_para(doc, '数据采集模块（MES dc）负责手工/业务采集，IoT物联网模块负责自动设备采集（MQTT/传感器数据）。')

h2(doc, '12.1 数据采集')
bullet(doc, '采集点配置（AUTO自动/MANUAL手动）')
bullet(doc, '数据记录查询 + 归档')
bullet(doc, 'IoT自动归档dc_data_record（SnailJob定时任务）')
bullet(doc, 'AUTO采集点IoT数据同步')

h2(doc, '12.2 IoT产品管理')
bullet(doc, '产品（设备模板）注册：产品编码、名称、协议类型（MQTT/OPC_UA/MODBUS/HTTP）')
bullet(doc, '产品下定义属性（数据点）')

h2(doc, '12.3 IoT设备管理')
bullet(doc, '设备实例注册：设备编码（MQTT ClientID）、设备密钥（MQTT认证）')
bullet(doc, '设备在线/离线状态（Redis缓存，15分钟心跳超时判离线）')
bullet(doc, '设备密钥自动生成')
bullet(doc, '设备数据查询')

h2(doc, '12.4 MQTT接入')
bullet(doc, '内置MQTT Broker（Moquette内嵌），端口1883')
bullet(doc, 'Topic规范：按产品/设备组织')
bullet(doc, '设备认证：设备编码+密钥鉴权')
bullet(doc, '多租户访问控制')

h2(doc, '12.5 实时数据')
bullet(doc, '实时数据SSE推送（IotRealtimeSseManager）')
bullet(doc, '设备最新数据缓存（Redis）')
bullet(doc, '前端实时看板：设备运行状态实时展示')

h2(doc, '12.6 告警检测')
bullet(doc, '基于规则的实时告警检测（IotAlertChecker）')
bullet(doc, '告警通知推送（IotAlertNotifier）')
bullet(doc, 'MES安灯联动：设备数据异常自动触发安灯')

h2(doc, '12.7 定时任务')
bullet(doc, '设备心跳检测（15分钟超时→OFFLINE）')
bullet(doc, '历史数据清理（90天）')
bullet(doc, 'Redis Stream裁剪（100K上限）')

# ============ 第13章 视觉识别 ============
h1(doc, '第13章 视觉识别')
add_para(doc, '视觉识别模块对接独立Vision服务，提供人脸识别与物料OCR能力。')

h2(doc, '13.1 人脸底库管理')
bullet(doc, '人脸底库：注册员工人脸照片')
bullet(doc, '人脸1:1验证：单张照片身份核验')
bullet(doc, '人脸1:N识别：从底库中识别匹配人员')
bullet(doc, '批量注册：一次性注册多人')
bullet(doc, 'FAIL复查：识别失败照片人工复核')

h2(doc, '13.2 考勤人脸打卡')
bullet(doc, '打卡时进行人脸比对验证身份')
bullet(doc, '异步人脸验证 + 降级补跑任务兜底')
bullet(doc, '视觉服务不可用时自动降级，不阻断考勤主流程')

h2(doc, '13.3 物料OCR')
bullet(doc, '物料标签拍照识别：拍照自动解析物料信息')
bullet(doc, '标签拍照解析组件（LabelCapture）：收料/入库场景快速录入')

# ============ 第14章 AI智能助手 ============
h1(doc, '第14章 AI智能助手')
add_para(doc, 'AI智能助手提供智能化的数据查询和业务辅助能力，基于LangChain4j框架。')

h2(doc, '14.1 对话模式')
bullet(doc, '支持自然语言对话交互')
bullet(doc, '多轮对话上下文记忆')
bullet(doc, '支持中英文混合输入')
bullet(doc, '流式响应（SSE推送）')

h2(doc, '14.2 使用方式')
bullet(doc, '侧边栏AI助手入口：随时调用AI助手')
bullet(doc, '顶部工具栏AI按钮：打开全局对话抽屉')
bullet(doc, '业务页面嵌入：在特定页面中提供专属AI功能')

h2(doc, '14.3 AI模型管理')
bullet(doc, '配置AI厂商（DeepSeek/通义千问/智谱/Ollama等OpenAI兼容接口）')
bullet(doc, '配置AI模型：选择模型类型、设置参数')
bullet(doc, '模型动态构建 + 缓存')
bullet(doc, '推理模式（Reasoning）')
bullet(doc, '多厂商路由 + Rerank')
bullet(doc, '联网搜索')

h2(doc, '14.4 知识库管理')
bullet(doc, '上传企业文档、操作手册、FAQ等知识')
bullet(doc, '文档自动解析和索引建立（分块）')
bullet(doc, 'AI基于知识库回答问题（RAG）')
bullet(doc, '检索测试：验证检索效果')
bullet(doc, '知识图谱：构建/查询/邻居/社区/全局检索')

h2(doc, '14.5 数据查询Agent')
bullet(doc, '自然语言查询数据库：用自然语言描述查询需求')
bullet(doc, 'AI自动生成SQL并执行，返回结果')
bullet(doc, '支持多表关联查询和复杂统计')
bullet(doc, '查询结果可导出为Excel')
bullet(doc, '查询安全限制：仅允许SELECT，白名单表前缀')

h2(doc, '14.6 Prompt模板管理')
bullet(doc, '创建和管理Prompt模板')
bullet(doc, '模板分类：数据查询、报表生成、业务分析等')
bullet(doc, '模板变量支持：在Prompt中使用变量占位符')
bullet(doc, '模板共享和版本管理')

h2(doc, '14.7 SQL示例库')
bullet(doc, '维护常用查询的SQL示例')
bullet(doc, 'AI生成SQL时参考示例库提高准确率')
bullet(doc, '支持按业务模块分类管理')
bullet(doc, '示例测试和验证功能')

h2(doc, '14.8 A/B测试')
bullet(doc, '同时配置多个AI模型进行对比测试')
bullet(doc, '自动记录各模型的回答质量')
bullet(doc, '生成对比报告，辅助模型选型决策')

h2(doc, '14.9 文生图')
bullet(doc, 'AI图片生成（通义万象）')
bullet(doc, '生成图片OSS持久化')
bullet(doc, '图片管理/历史记录')

h2(doc, '14.10 MCP工具管理')
bullet(doc, 'MCP（Model Context Protocol）工具管理')
bullet(doc, '添加、配置、启用/停用MCP工具')
bullet(doc, 'MCP市场：远程工具列表 + 一键加载')
bullet(doc, '工具权限管理：设置哪些角色可使用哪些工具')

h2(doc, '14.11 Token用量统计')
bullet(doc, '查看AI调用的Token消耗情况')
bullet(doc, '按用户、模型、时间维度统计')
bullet(doc, '费用预算管理和超额预警')
bullet(doc, '用量趋势分析报表')

# ============ 第15章 即时通讯 ============
h1(doc, '第15章 即时通讯')
add_para(doc, '即时通讯模块提供站内私聊、群聊与系统通知能力。')

h2(doc, '15.1 私聊')
bullet(doc, '一对一消息发送/接收')
bullet(doc, '会话列表管理')
bullet(doc, '消息撤回')

h2(doc, '15.2 群聊')
bullet(doc, '群组创建/管理 + 成员管理')
bullet(doc, '群消息发送/接收')

h2(doc, '15.3 系统通知')
bullet(doc, '系统通知创建 + 标记已读')
bullet(doc, 'SSE实时消息推送（ImRealtimePush）')

h2(doc, '15.4 移动端IM')
bullet(doc, 'App端即时通讯（好友/群组/消息）')
bullet(doc, 'App端通知接收')

# ============ 第16章 财务核心模块 ============
h1(doc, '第16章 财务核心模块')
add_para(doc, '财务核心模块（V3.0新增）覆盖总账、应收应付、固定资产、存货成本与期末关账，'
              '与业务模块打通形成完整的财务闭环。')

h2(doc, '16.1 总账管理（GL）')
bullet(doc, '会计科目管理：科目编码、名称、方向、辅助核算')
bullet(doc, '凭证管理：凭证录入、审核、过账')
bullet(doc, '会计期间管理：开账/结账')
bullet(doc, '汇率管理')
bullet(doc, '试算平衡表')

h2(doc, '16.2 应收应付核销（AR/AP）')
bullet(doc, '发票过账：业务发票自动生成财务凭证')
bullet(doc, '收款管理：登记回款并核销应收')
bullet(doc, '付款管理：登记付款并核销应付')
bullet(doc, '账龄分析报表')
bullet(doc, '收付款单审核流程')

h2(doc, '16.3 固定资产（FA）')
bullet(doc, '资产分类 + 资产卡片')
bullet(doc, '折旧计提（月度折旧）')
bullet(doc, '资产处置')
bullet(doc, '折旧报表')

h2(doc, '16.4 存货成本')
bullet(doc, '计价方法接线（移动平均法）')
bullet(doc, '成本过账：库存变动自动生成成本凭证')
bullet(doc, '暂估处理')

h2(doc, '16.5 报表与期末关账')
bullet(doc, '三大报表：资产负债表、利润表、现金流量表')
bullet(doc, '报表项目配置')
bullet(doc, '月度结账 / 年度结账')
bullet(doc, '模拟支付入口：模拟渠道回调（F1自动收款/核销）')

# ============ 第17章 支付管理 ============
h1(doc, '第17章 支付管理')
add_para(doc, '支付管理模块提供统一支付入口与支付订单管理。')

h2(doc, '17.1 统一支付')
bullet(doc, '支持多种支付方式：支付宝、微信支付')
bullet(doc, '支付测试页（paytest）')
bullet(doc, '支付回调处理 → ERP自动收款/核销')

h2(doc, '17.2 支付订单管理')
bullet(doc, '支付订单查询')
bullet(doc, '支付状态跟踪')
bullet(doc, '发票管理')

h2(doc, '17.3 收款联动')
bullet(doc, '支付回调 → 自动生成应收收款单')
bullet(doc, '收款核销：自动匹配发票核销')

# ============ 第18章 工作流引擎 ============
h1(doc, '第18章 工作流引擎')
add_para(doc, '流程引擎基于Flowable工作流引擎，提供灵活的审批流程配置和执行能力。')

h2(doc, '18.1 流程设计')
bullet(doc, '流程分类管理')
bullet(doc, '流程模型管理')
bullet(doc, '可视化流程设计器：拖拽式设计审批流程')
bullet(doc, 'BPMN 2.0格式流程定义文件')
bullet(doc, '流程部署管理：查看、部署、挂起、激活流程')
bullet(doc, '设计器表单管理 / 业务自定义表单')

h2(doc, '18.2 提交审批')
bullet(doc, '在各业务单据中点击“提交审批”按钮')
bullet(doc, '填写审批备注（可选）')
bullet(doc, '系统根据流程配置自动确定审批人')
bullet(doc, '提交后单据状态变为“待审批”')

h2(doc, '18.3 审批处理')
bullet(doc, '在“我的待办”中查看待审批任务')
bullet(doc, '支持通过、拒绝、转办、加签等操作')
bullet(doc, '填写审批意见和备注')
bullet(doc, '支持批量审批')

h2(doc, '18.4 任务管理')
bullet(doc, '待办任务列表')
bullet(doc, '已办任务列表')
bullet(doc, '我发起的流程')
bullet(doc, '可认领任务（签收后任务锁定给当前用户）')
bullet(doc, '抄送列表')
bullet(doc, '发起新流程')
bullet(doc, '流程详情查看（嵌入业务表单）')

h2(doc, '18.5 ERP审批流集成')
add_para(doc, '已集成的审批流：')
bullet(doc, '销售订单审批 / 销售退货审批')
bullet(doc, '采购申请审批 / 采购订单审批 / 采购退货审批')
bullet(doc, '生产工单审批')
bullet(doc, '物料入库审批 / 销售出库审批 / 调拨单审批 / 盘点计划审批 / 到货通知审批')

# ============ 第19章 报表管理 ============
h1(doc, '第19章 报表管理')
add_para(doc, '报表模块基于JimuReport（积木报表）提供灵活的自助报表能力。')

h2(doc, '19.1 报表仪表盘')
bullet(doc, '数据可视化仪表盘')
bullet(doc, '拖拽式报表设计器，无需编码即可创建报表')
bullet(doc, '支持多种图表类型：柱状图、折线图、饼图、仪表盘')

h2(doc, '19.2 报表设计')
bullet(doc, '数据源配置：支持SQL查询、API接口')
bullet(doc, '报表分享和定时推送')
bullet(doc, '报表权限管理：设置查看、编辑、导出权限')

# ============ 第20章 系统管理 ============
h1(doc, '第20章 系统管理')
add_para(doc, '系统管理模块提供系统配置和权限管理功能。')

h2(doc, '20.1 用户管理')
bullet(doc, '用户账户管理：新建、修改、删除、密码重置')
bullet(doc, '用户角色分配：为用户分配一个或多个角色')
bullet(doc, '用户状态管理：启用、停用、锁定')
bullet(doc, '用户批量导入：通过Excel批量导入用户')

h2(doc, '20.2 角色管理')
bullet(doc, '角色定义：创建角色并分配权限')
bullet(doc, '菜单权限：设置角色可访问的菜单页面')
bullet(doc, '数据权限：设置角色可查看的数据范围')
bullet(doc, '按钮权限：设置角色可执行的操作')

h2(doc, '20.3 菜单管理')
bullet(doc, '菜单树管理：添加、修改、排序、删除菜单')
bullet(doc, '菜单类型：目录、菜单、按钮')
bullet(doc, '菜单图标和路由配置')
bullet(doc, '菜单权限标识配置')

h2(doc, '20.4 部门管理')
bullet(doc, '部门树管理：创建多级部门结构')
bullet(doc, '部门负责人设置')
bullet(doc, '部门与用户的关联管理')

h2(doc, '20.5 字典管理')
bullet(doc, '系统字典维护：定义下拉框、单选框等选项数据')
bullet(doc, '字典类型和字典值的层级管理')
bullet(doc, '字典值排序和启用/停用管理')

h2(doc, '20.6 租户管理')
bullet(doc, '租户创建和配置：租户名称、Logo、联系信息')
bullet(doc, '租户套餐管理：设置租户可使用的功能模块')
bullet(doc, '租户用户数限制')
bullet(doc, '租户数据隔离保证')

h2(doc, '20.7 登录设备管理')
bullet(doc, '查看用户登录设备列表')
bullet(doc, '设备绑定和解绑')
bullet(doc, '登录日志查询：登录时间、IP地址、设备信息')
bullet(doc, '异常登录检测和告警')

h2(doc, '20.8 社交账号绑定')
bullet(doc, '支持绑定企业微信、钉钉、飞书等社交账号')
bullet(doc, '社交账号登录配置')
bullet(doc, '绑定和解绑操作管理')

h2(doc, '20.9 系统监控')
bullet(doc, '在线用户管理')
bullet(doc, '操作日志 / 登录日志')
bullet(doc, '缓存监控 / 服务监控（Spring Boot Admin）')
bullet(doc, '任务调度（SnailJob控制台）')

# ============ 附录A 典型业务流程 ============
doc.add_page_break()
h1(doc, '附录A 典型业务流程')

h2(doc, 'A.1 销售生产全链路')
numbered(doc, '客户询价 > 销售报价 > 销售合同')
numbered(doc, '销售订单 > 生产计划 > 物料需求运算')
numbered(doc, '采购申请 > 采购订单 > 收货入库')
numbered(doc, '生产工单 > MES工单 > 流转卡执行 > 工序报工')
numbered(doc, '成品入库 > 发货出库 > 销售发票 > 回款核销')

h2(doc, 'A.2 采购流程')
numbered(doc, '采购申请（手动/自动）> 审批')
numbered(doc, '采购订单 > 供应商确认')
numbered(doc, '到货通知 > 质量检验（IQC）> 收货入库')
numbered(doc, '采购发票 > 付款审批 > 支付执行')

h2(doc, 'A.3 生产流程')
numbered(doc, '生产计划 > 物料需求运算 > 缺料检查')
numbered(doc, '生产工单 > 审批 > 下达')
numbered(doc, '生产任务分配 > 领料 > 生产执行')
numbered(doc, '质量检验（IPQC）> 成品入库')

h2(doc, 'A.4 MES制造执行流程')
numbered(doc, 'MES工单创建 > 选择BOM和工艺路线')
numbered(doc, '工单审批 > 下达（自动快照MBOM）')
numbered(doc, '生成流转卡 > 生成SN')
numbered(doc, '工序执行：进站 > 开工 > 报工 > 出站 > 下一工序')
numbered(doc, '成品完工 > 质量检验（OQC）> 入库')

h2(doc, 'A.5 SN追溯流程')
numbered(doc, '输入SN号查询')
numbered(doc, '前向追溯：查看原材料批次、生产工序记录、检验记录')
numbered(doc, '后向追溯：查看发货信息、客户信息、售后记录')

h2(doc, 'A.6 委外加工流程')
numbered(doc, '创建委外加工订单 > 审批')
numbered(doc, '发放委外物料 > 出库')
numbered(doc, '委外厂商加工')
numbered(doc, '收回加工成品 > 质量检验 > 入库')
numbered(doc, '委外费用结算')

h2(doc, 'A.7 退货流程')
bullet(doc, '销售退货：客户申请退货 > 审批 > 退货收货 > 入库 > 退款')
bullet(doc, '采购退货：发现问题 > 创建退货单 > 审批 > 退货出库 > 供应商确认')

h2(doc, 'A.8 财务收款流程（V3.0）')
numbered(doc, '销售出库 > 销售发票开票')
numbered(doc, '发票过账：自动生成财务凭证（应收）')
numbered(doc, '客户付款 > 收款登记 > 核销应收')
numbered(doc, '期末：成本核算 > 折旧计提 > 月结 > 出具三大报表')

h2(doc, 'A.9 质检触发链路（V3.0）')
bullet(doc, 'IQC：ERP收货审批通过 → MQ自动创建来料检验任务')
bullet(doc, 'IPQC：关键工序报工触发过程检验（工单完工门禁）')
bullet(doc, 'OQC：销售出库审批通过 → MQ自动创建出货检验任务')
bullet(doc, '检验不合格 → 批次隔离 + NCR单据 → 返工工单 → 8D报告')

# ============ 附录B 更新日志 ============
h1(doc, '附录B 更新日志')

h2(doc, 'B.1 V2.0更新（2026年6月）')
add_para(doc, 'BOM管理增强（Phase 1-5）')
table(doc,
      ['阶段', '功能', '说明'],
      [
          ['Phase 1', 'BOM基础增强', '新增BOM类型（EBOM/MBOM/SBOM/CBOM）、版本管理、状态流转'],
          ['Phase 2', 'BOM工具增强', 'BOM复制、BOM比较、Where-Used反查、BOM验证'],
          ['Phase 3', '替代料管理', '支持FULL/PARTIAL/INTERCHANGEABLE替代类型，有效期管理'],
          ['Phase 4', 'BOM成本卷积', '多级成本自动计算，含材料、人工、制造成本'],
          ['Phase 5', '工艺路线融合', '工艺路线版本管理、工序关联、费率配置'],
      ],
      widths=[2.0, 4.0, 10.0])
add_para(doc, 'BOM成本卷积与MES制造BOM融合：')
bullet(doc, 'ERP BOM专注于成本核算和物料需求计算')
bullet(doc, 'MES MBOM专注于制造执行和工序物料投放')
bullet(doc, '工单下达时自动快照，确保执行数据一致性')
bullet(doc, '成本卷积可基于实际生产数据进行更精确的成本核算')
bullet(doc, '支持实际成本与标准成本的差异分析')

h2(doc, 'B.2 V3.0更新（2026年7-8月）')
table(doc,
      ['模块', '新增功能', '说明'],
      [
          ['财务核心', '总账GL', '科目/凭证/期间/汇率/试算平衡（F0）'],
          ['财务核心', '应收应付核销', '发票过账/收付款/核销/账龄（F1）'],
          ['财务核心', '固定资产', '卡片/折旧/处置（F2）'],
          ['财务核心', '存货成本', '计价接线/成本过账/暂估（F3-a）'],
          ['财务核心', '报表与关账', '三大报表/月结/年结（F5）'],
          ['IoT物联网', '设备接入', '产品/设备/MQTT Broker/认证/实时数据SSE/告警'],
          ['视觉识别', '人脸与OCR', '人脸底库/1:1验证/1:N识别/物料OCR/标签拍照解析/人脸打卡'],
          ['即时通讯', 'IM', '私聊/群聊/系统通知/消息撤回/SSE推送/移动端IM'],
          ['MES P0', '批次管理', '批次主档/状态机/FEFO消耗/SN绑定/隔离放行'],
          ['MES P0', '工装管理', '工装台账/寿命计数/保养/领用归还'],
          ['MES P0', '电子SOP', '文档库/工序绑定/查阅记录/工位推送'],
          ['MES P0', 'NCR与返工', 'NCR单据/缺陷代码/返工派生/8D报告'],
          ['MES P0', '进站出站', '进站/出站/跳站校验/流程指引/校验规则'],
          ['质量管理', 'QC三链路', 'IQC自动创建/IPQC过程/OQC出货/检验回写ERP'],
          ['质量管理', 'SPC', '控制图/数据点/OOC告警/Cp-Cpk/趋势可视化'],
          ['设备管理', 'MES eq', '台账/保养/维修/点检/OEE/备件/巡检/监控'],
          ['AI', '知识图谱', '构建/查询/邻居/社区/全局检索'],
          ['AI', '文生图', '通义万象图片生成 + OSS持久化'],
          ['AI', '推理与检索', '推理模式/多厂商路由/Rerank/联网搜索'],
          ['移动端', 'App', '打卡考勤/排班查询/即时通讯'],
      ],
      widths=[3.0, 4.5, 8.5])

add_para(doc, '—— 本手册到此结束，感谢使用NexusERP系统！')

# 保存
os.makedirs(os.path.dirname(OUT), exist_ok=True)
doc.save(OUT)
print('OK:', OUT)
