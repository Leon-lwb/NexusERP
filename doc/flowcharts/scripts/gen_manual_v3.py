#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""NexusERP 用户操作手册 V3.0 扩充版生成器（完整版）

用法: python gen_manual_v3.py
输出: doc/system/user-manual/NexusERP_用户操作手册.docx

章节模块:
  manual_ch2_ch3.py   — 第2章 基础数据 + 第3章 销售
  manual_ch4_ch7.py   — 第4章 采购 + 第5章 请购 + 第6章 生产 + 第7章 排产
  manual_ch8_ch11.py  — 第8章 MES + 第9章 质量SPC + 第10章 设备 + 第11章 批次/工装/SOP/NCR
  manual_ch12_ch15.py — 第12章 IoT + 第13章 视觉 + 第14章 AI + 第15章 IM
  manual_ch16_ch20.py — 第16章 财务 + 第17章 支付 + 第18章 工作流 + 第19章 报表 + 第20章 系统 + 附录

写作密度：每个功能页包含 入口 → 列表说明 → 表单字段表 → 状态流转 → 操作步骤 → 注意事项。
"""
import os
import docx
from docx import Document
from docx.shared import Pt, RGBColor, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

from manual_ch2_ch3 import ch2_bd, ch3_so
from manual_ch4_ch7 import ch4_pu, ch5_req, ch6_prd, ch7_sch
from manual_ch8_ch11 import ch8_mes, ch9_qc, ch10_eq, ch11_lt
from manual_ch12_ch15 import ch12_iot, ch13_cv, ch14_ai, ch15_im
from manual_ch16_ch20 import ch16_fin, ch17_pay, ch18_workflow, ch19_report, ch20_system, appendix

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   '..', '..', '..', 'doc', 'system', 'user-manual',
                   'NexusERP_用户操作手册.docx')

FONT = '微软雅黑'
ACCENT = RGBColor(0x1F, 0x4E, 0x79)
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


def para(doc, text, size=10.5, bold=False, color=None, align=None, after=4):
    p = doc.add_paragraph()
    r = p.add_run(text)
    set_font(r, size=size, bold=bold, color=color)
    if align is not None:
        p.alignment = align
    p.paragraph_format.space_after = Pt(after)
    return p


def h1(doc, text):
    p = doc.add_heading(level=1)
    r = p.add_run(text)
    set_font(r, size=18, bold=True, color=ACCENT)
    p.paragraph_format.space_before = Pt(18)
    p.paragraph_format.space_after = Pt(8)


def h2(doc, text):
    p = doc.add_heading(level=2)
    r = p.add_run(text)
    set_font(r, size=14, bold=True, color=ACCENT)
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(6)


def h3(doc, text):
    p = doc.add_heading(level=3)
    r = p.add_run(text)
    set_font(r, size=12, bold=True, color=RGBColor(0x40, 0x40, 0x40))
    p.paragraph_format.space_before = Pt(8)
    p.paragraph_format.space_after = Pt(4)


def bullet(doc, text, size=10.5):
    p = doc.add_paragraph(style='List Bullet')
    r = p.add_run(text)
    set_font(r, size=size)
    p.paragraph_format.space_after = Pt(2)


def numbered(doc, text, size=10.5):
    p = doc.add_paragraph(style='List Number')
    r = p.add_run(text)
    set_font(r, size=size)
    p.paragraph_format.space_after = Pt(2)


def shade(cell, color):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement('w:shd')
    shd.set(qn('w:val'), 'clear')
    shd.set(qn('w:color'), 'auto')
    shd.set(qn('w:fill'), color)
    tcPr.append(shd)


def table(doc, headers, rows, widths=None):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = 'Table Grid'
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    hdr = t.rows[0].cells
    for i, htext in enumerate(headers):
        hdr[i].text = ''
        p = hdr[i].paragraphs[0]
        r = p.add_run(htext)
        set_font(r, size=10, bold=True, color=HEADER_FONT)
        shade(hdr[i], HEADER_BG)
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


def ch1_overview(doc):
    h1(doc, '第1章 系统概述')
    h2(doc, '1.1 系统简介')
    para(doc, 'NexusERP是一套基于RuoYi-Vue-Plus v5.6.0框架开发的企业资源计划（ERP）管理系统，'
              '专为制造业与贸易型企业设计。系统采用前后端分离架构，后端基于Spring Boot + MyBatis-Plus，'
              '前端采用Vue 3 + Element Plus；实际数据库类型和模块部署以管理员确认的环境为准。')
    para(doc, '系统覆盖企业运营的全部核心业务环节，包括：基础数据、销售、采购、生产、排产调度、'
              '仓储、MES制造执行、质量管理、设备管理、批次追溯、工装、SOP、NCR、数据采集、'
              'IoT物联网、视觉识别、AI智能助手、即时通讯、财务核心、支付、工作流、报表等模块。'
              '系统支持多租户模式，可满足集团化企业多公司、多工厂的管理需求。')

    h2(doc, '1.2 核心模块总览')
    para(doc, '下表按业务功能分组介绍系统；不等同于后端 Maven 模块数量。独立 Flow、工业决策、'
              'Station 工位通道、生产履历和电子签章的当前说明见相应章节。')
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
    para(doc, '打开浏览器，访问管理员提供的本组织系统地址；生产环境地址和 HTTPS 配置须由管理员确认。'
              '本手册不提供可通用登录的公开演示地址或默认账户。')
    para(doc, '登录步骤：')
    numbered(doc, '在登录页面输入用户名和密码')
    numbered(doc, '选择所属租户（多租户模式下需要选择）')
    numbered(doc, '输入图片验证码或滑动验证')
    numbered(doc, '点击“登录”按钮进入系统')
    para(doc, '温馨提示：首次登录后请立即修改初始密码。进入“个人中心” > “修改密码”。')

    h2(doc, '1.4 界面布局')
    para(doc, 'NexusERP采用经典的后台管理系统布局：')
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
              ['提交审批', '按具体单据和来源选择审批入口；认证采购生命周期与传统Flowable不是同一流程'],
              ['自动编码', '带“生成”开关的编码字段：开启后由系统按规则生成（预览不占号，保存时正式生成）'],
              ['状态标签颜色', 'info=草稿/灰、primary=进行中、success=已完成/通过、warning=审批中、danger=已驳回/失败'],
          ],
          widths=[3.0, 13.0])

    h2(doc, '1.6 版本说明')
    para(doc, '本次文档版本为 V4.0，核对日期 2026-10-12，代码基线为 plus 分支 fe59e4ae。'
              '手册版本是文档版本，不代表新的软件发布编号。保留既有业务操作结构，更新当前流程图，'
              '补充工业决策、独立 Flow 编排、视觉缺陷检测、Station、生产履历、电子签章和仓储管理。')
    para(doc, '可见菜单由租户套餐、角色授权、环境菜单配置决定；页面存在不保证当前账户能操作。'
              '第三方模型、MQ、设备、定时任务和签章的代码实现不等于生产已部署或人工验收已完成。'
              '旧 V2/V3 记录仅作历史背景，当前操作以本手册的代码基线和实际运行环境为准。')


def build():
    doc = Document()
    for section in doc.sections:
        section.top_margin = Cm(2.0)
        section.bottom_margin = Cm(2.0)
        section.left_margin = Cm(2.2)
        section.right_margin = Cm(2.2)

    # 封面
    for _ in range(5):
        doc.add_paragraph()
    para(doc, 'NexusERP 用户操作手册', size=26, bold=True, color=ACCENT,
         align=WD_ALIGN_PARAGRAPH.CENTER, after=10)
    para(doc, 'User Operation Manual', size=14, color=RGBColor(0x80, 0x80, 0x80),
         align=WD_ALIGN_PARAGRAPH.CENTER, after=24)
    para(doc, '面向制造业与贸易型企业的一站式企业管理系统', size=12,
         align=WD_ALIGN_PARAGRAPH.CENTER, after=40)
    para(doc, '版本：V3.0', size=12, align=WD_ALIGN_PARAGRAPH.CENTER, after=6)
    para(doc, '日期：2026年8月', size=12, align=WD_ALIGN_PARAGRAPH.CENTER, after=6)
    para(doc, '作者：王欢', size=12, align=WD_ALIGN_PARAGRAPH.CENTER, after=6)
    doc.add_page_break()

    # 目录
    h1(doc, '目录')
    para(doc, '（请在Word中右键此处，选择“更新域”以生成自动目录）', size=9,
         color=RGBColor(0x80, 0x80, 0x80))
    toc_field = OxmlElement('w:fldSimple')
    toc_field.set(qn('w:instr'), ' TOC \\o "1-2" \\h \\z \\u ')
    p = doc.add_paragraph()
    p._p.append(toc_field)
    doc.add_page_break()

    # 章节
    ch1_overview(doc)
    ch2_bd(doc, h1, h2, h3, para, bullet, numbered, table)
    ch3_so(doc, h1, h2, h3, para, bullet, numbered, table)
    ch4_pu(doc, h1, h2, h3, para, bullet, numbered, table)
    ch5_req(doc, h1, h2, h3, para, bullet, numbered, table)
    ch6_prd(doc, h1, h2, h3, para, bullet, numbered, table)
    ch7_sch(doc, h1, h2, h3, para, bullet, numbered, table)
    ch8_mes(doc, h1, h2, h3, para, bullet, numbered, table)
    ch9_qc(doc, h1, h2, h3, para, bullet, numbered, table)
    ch10_eq(doc, h1, h2, h3, para, bullet, numbered, table)
    ch11_lt(doc, h1, h2, h3, para, bullet, numbered, table)
    ch12_iot(doc, h1, h2, h3, para, bullet, numbered, table)
    ch13_cv(doc, h1, h2, h3, para, bullet, numbered, table)
    ch14_ai(doc, h1, h2, h3, para, bullet, numbered, table)
    ch15_im(doc, h1, h2, h3, para, bullet, numbered, table)
    ch16_fin(doc, h1, h2, h3, para, bullet, numbered, table)
    ch17_pay(doc, h1, h2, h3, para, bullet, numbered, table)
    ch18_workflow(doc, h1, h2, h3, para, bullet, numbered, table)
    ch19_report(doc, h1, h2, h3, para, bullet, numbered, table)
    ch20_system(doc, h1, h2, h3, para, bullet, numbered, table)
    appendix(doc, h1, h2, h3, para, bullet, numbered, table)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    doc.save(OUT)
    print('OK:', OUT)


if __name__ == '__main__':
    build()
