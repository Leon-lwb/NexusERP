#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第12章 数据采集与IoT + 第13章 视觉识别 + 第14章 AI智能助手
+ 第15章 即时通讯
供 gen_manual_v3.py 导入调用。
"""


def ch12_iot(doc, h1, h2, h3, para, bullet, numbered, table):
    h1(doc, '第12章 数据采集与IoT物联网')
    para(doc, '数据采集模块（MES dc）负责手工/业务采集，IoT物联网模块负责自动设备采集（MQTT/传感器数据）。'
              'IoT设备采集的数据可回写到MES生产数据记录。')

    h2(doc, '12.1 数据采集')
    para(doc, '操作入口：数据采集 > 采集点配置')
    para(doc, '表单字段：采集点编码*、采集点名称*、采集点类型*（手动MANUAL/自动AUTO；AUTO必须关联IoT设备）、关联IoT设备（AUTO必填）、IoT属性编码（如temperature，留空归档全部）、数据类型（数值/文本/枚举）、单位、采集频率(秒)、关联设备、关联工序、状态。')
    para(doc, '操作入口：数据采集 > 数据记录')
    para(doc, '录入数据：采集点*、数值（精度4，用于SPC）、文本值、关联工单/流转卡、采集时间（留空默认当前）。')

    h2(doc, '12.2 IoT产品管理')
    para(doc, '操作入口：IoT平台 > 产品管理')
    para(doc, '表单字段：产品编码*、产品名称*、产品类型（默认设备DEVICE）、协议类型（MQTT/OPC UA/Modbus/HTTP，默认MQTT）、状态、描述。')

    h2(doc, '12.3 IoT设备管理')
    para(doc, '操作入口：IoT平台 > 设备管理')
    para(doc, '表单字段：所属产品*（下拉）、设备编码*（即MQTT ClientID）、设备名称*、协议类型（默认MQTT）、IP地址、固件版本、位置、状态。')
    para(doc, '设备认证：设备编码+密钥（自动生成）鉴权，多租户访问控制。')
    para(doc, '连接状态：在线ONLINE/离线OFFLINE（15分钟心跳超时判离线）。')
    para(doc, '关联MES：IoT设备与MES设备双向关联（设备页“关联MES”，MES设备页“关联IoT”）。')

    h2(doc, '12.4 MQTT接入')
    para(doc, '内置MQTT Broker（Moquette内嵌），端口1883。')
    para(doc, 'Topic规范：按产品/设备组织；设备发布属性数据，系统订阅解析入库。')
    para(doc, '数据链路：MQTT消息→消息管道解析（Topic解析→属性提取→类型转换）→数据入库→实时缓存。')

    h2(doc, '12.5 实时数据')
    para(doc, '操作入口：IoT平台 > 设备管理 > 行内“实时”')
    para(doc, '实时监控页：设备在线状态、最新数值卡片（按属性逐张）、实时趋势图（SSE订阅，3秒刷新，滚动窗口60点，断线自动重连，连续5次失败停止）、历史数据（近1小时）。')

    h2(doc, '12.6 告警检测')
    para(doc, '基于配置规则的实时告警检测，异常数据触发告警通知（SSE推送）。')
    para(doc, 'MES安灯联动：设备数据异常自动触发安灯（IMesAndonService）。')

    h2(doc, '12.7 定时任务')
    bullet(doc, '设备心跳检测：15分钟超时置离线')
    bullet(doc, '历史数据清理：90天自动清理')
    bullet(doc, 'Redis Stream裁剪：100K上限')
    bullet(doc, '数据流归档：IoT自动归档到MES采集记录（SnailJob）')


def ch13_cv(doc, h1, h2, h3, para, bullet, numbered, table):
    h1(doc, '第13章 视觉识别')
    para(doc, '视觉识别模块对接独立Vision服务，提供人脸识别与物料OCR能力。')

    h2(doc, '13.1 人脸底库管理')
    para(doc, '操作入口：视觉识别 > 人脸底库')
    para(doc, '注册人脸：工号*（如EMP-1001）、姓名*、人脸照片*（拖拽上传，JPEG/PNG ≤10MB）、备注。')
    para(doc, '列表列：工号、姓名、照片（base64头像）、样本数、备注、更新时间。')
    para(doc, '工具栏：注册人脸、头像批量注册（按测试账号批量注册）、服务状态（CV服务可用性/模型列表）。')
    para(doc, '行内“重新注册”复用注册表单替换样本。')

    h2(doc, '13.2 人脸验证与考勤打卡')
    para(doc, '人脸1:1验证：单张照片身份核验（考勤打卡场景）。')
    para(doc, '人脸1:N识别：从底库识别匹配人员（操作台刷脸登录，top-3候选+1:1确认，阈值50%）。')
    para(doc, '考勤集成：打卡时异步人脸验证；视觉服务不可用时自动降级（考勤置NONE），由补跑任务兜底。')

    h2(doc, '13.3 物料OCR')
    para(doc, '操作入口：视觉识别 > 物料检测')
    para(doc, '上传标签照片（≤10MB）→勾选OCR/解析料号/批次/SN（交叉校验物料主数据）→“识别”（显示耗时）或“YOLO通用检测”。')
    para(doc, '结果四页签：标签解析（料号+主数据匹配标记）、OCR文本（置信度≥50%绿色）、YOLO检测（类别/置信度/边界框）、原始JSON。')
    para(doc, '标签拍照解析组件（LabelCapture）：收料/入库场景拍照快速录入（示范：采购收货上架弹窗“Camera”扫描）。')

    h2(doc, '13.4 FAIL复查')
    para(doc, '操作入口：视觉识别 > FAIL复查')
    para(doc, '人脸考勤FAIL记录需在24小时内复核：误判改PASS / 代打卡确认FAIL。')
    para(doc, '复查操作写入审计备注；照片经后端代理展示（私有桶）。')


def ch14_ai(doc, h1, h2, h3, para, bullet, numbered, table):
    h1(doc, '第14章 AI智能助手')
    para(doc, 'AI智能助手提供智能化的数据查询和业务辅助能力，基于LangChain4j框架，'
              '支持对话、数据查询Agent、知识库RAG、文生图等能力。')

    h2(doc, '14.1 使用方式')
    bullet(doc, '顶部工具栏AI按钮：打开全局对话抽屉')
    bullet(doc, 'AI页面独立对话界面（左侧会话列表+右侧聊天区）')
    bullet(doc, '业务页面嵌入：特定页面提供专属AI功能')

    h2(doc, '14.2 对话模式')
    para(doc, '四种模式：')
    table(doc,
          ['模式', '说明'],
          [
              ['自动', 'AI自动判断使用对话/Agent/知识库'],
              ['对话', '普通多轮对话（中英文混合）'],
              ['数据查询', '数据查询Agent：自然语言→SQL→结果'],
              ['知识库', '基于知识库的RAG问答'],
          ],
          widths=[3.5, 13.0])
    para(doc, '对话功能：流式响应（SSE）、深度思考开关（推理链折叠展示）、联网搜索开关（信息来源卡片）、'
              'MCP工具选择、工具调用过程展示、消息点赞/点踩反馈、Markdown渲染（含ECharts图表）。')

    h2(doc, '14.3 AI厂商与模型管理')
    para(doc, '操作入口：AI > 模型管理（厂商/模型两个页签）')
    para(doc, '厂商：名称*、API地址*、API Key（列表脱敏显示，编辑可见）、厂商类型*（OpenAI/阿里云DashScope/智谱/Ollama/DeepSeek）、状态。')
    para(doc, '模型：所属厂商*、模型名称*、模型类型*（对话CHAT/嵌入EMBEDDING/重排RERANK/文生图IMAGE）、最大Token、温度（0-2）、是否默认、输入/输出价格（元/百万Token）。')
    para(doc, '模型用途：EMBEDDING为知识库数据源；IMAGE供文生图；RERANK供知识库重排序；设为默认一键切换。')

    h2(doc, '14.4 知识库管理')
    para(doc, '操作入口：AI > 知识库')
    para(doc, '新增知识库：名称*、Embedding模型*（无可用模型时红字提示先到模型管理添加）、向量存储（Milvus/InMemory）、高级配置（分段分隔符默认#、重叠字符30、文本块大小500、检索条数5、相似度阈值0.5、重排序、混合检索权重）。')
    para(doc, '文档管理：支持pdf/docx/doc/xlsx/txt/csv/md/html，单文件≤50MB；状态：待解析→解析中→已完成/失败（可重试）；查看分段、检索测试。')
    para(doc, '知识图谱：构建（分阶段：实体抽取→社区检测→社区摘要，2秒轮询进度）、实体/关系/社区页签、全局检索、节点邻居、全屏。')

    h2(doc, '14.5 数据查询Agent')
    bullet(doc, '自然语言描述查询需求，AI自动生成SQL并执行')
    bullet(doc, '支持多表关联与复杂统计，结果可导出')
    bullet(doc, '安全限制：仅SELECT、危险关键词黑名单、表前缀白名单、自动注入租户隔离')
    bullet(doc, 'SQL示例库（Few-shot）提高准确率；Prompt模板（支持{{变量}}）')

    h2(doc, '14.6 A/B测试与Token统计')
    para(doc, 'A/B测试：名称、模型A/B*、测试问题（JSON数组）→执行（异步）→结果按问题并排对比（延迟/Token/星级评分1-5直接保存）。')
    para(doc, 'Token用量：时间范围+模型筛选；总Token/成本卡片、趋势图、模型/用户排行、明细表；支持回填历史数据。')

    h2(doc, '14.7 文生图')
    para(doc, '操作入口：AI > 文生图')
    para(doc, '提示词（≤500字）、模型（IMAGE类型）、尺寸（1024/1280/720*1280/1280*720）、生成→下载；历史记录缩略图可回填查看。')

    h2(doc, '14.8 MCP工具管理')
    para(doc, '操作入口：AI > MCP工具')
    para(doc, '工具类型：内置BUILTIN/本地STDIO（配置JSON：command/args）/远程HTTP·SSE（baseUrl）。')
    para(doc, '操作：同步内置工具（一键入库）、测试连接（非内置）、启用/停用。')
    para(doc, 'MCP市场：市场源管理（名称/URL/描述）+工具列表（版本/描述/已加载）+单个/批量加载（加载默认停用，需到工具管理启用）。')


def ch15_im(doc, h1, h2, h3, para, bullet, numbered, table):
    h1(doc, '第15章 即时通讯')
    para(doc, '即时通讯模块提供站内私聊、群聊与系统通知能力，支持图片/文件/@提及/转发/撤回。')

    h2(doc, '15.1 聊天窗口')
    para(doc, '操作入口：顶部工具栏消息 > 即时通讯')
    para(doc, '左侧四页签：聊天（会话列表+未读角标）、联系人（选择联系人发起私聊）、群聊（输入名称+选成员创建）、设置（快捷发送Enter/Ctrl+Enter）。')
    para(doc, '右侧会话窗口：')
    bullet(doc, '消息气泡：头像/发送人/时间、“@你”标记、发送状态（失败点击重试）')
    bullet(doc, '图片≤10MB、文件≤20MB（先传OSS再发）')
    bullet(doc, '@群成员：搜索成员多选，发送携带mentionUserIds')
    bullet(doc, '撤回（限时）、转发（选目标会话）、已读/未读（私聊）')
    bullet(doc, '上滑加载更多（保持滚动位置）')

    h2(doc, '15.2 群组管理')
    bullet(doc, '群组创建/解散，成员管理（群主可移除/添加成员）')
    bullet(doc, '群消息发送/接收')

    h2(doc, '15.3 系统通知')
    bullet(doc, '系统通知创建+标记已读（右上角铃铛）')
    bullet(doc, 'SSE实时推送（ImRealtimePush）')

    h2(doc, '15.4 移动端IM')
    para(doc, 'App端即时通讯：好友/群组/消息收发、通知接收。')
