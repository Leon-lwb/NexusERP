#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Current operation supplements, verified against plus HEAD fe59e4ae on 2026-10-12.

The caller supplies the same document helpers as manual_ch8_ch11.py. Importing
this module does not generate files, contact services, or change business data.
Repository evidence is also listed in current_mes_iot_cv.json.
"""


def ch21_station(doc, h1, h2, h3, para, bullet, numbered, table):
    h1(doc, '第21章 工位通道与生产履历')
    para(doc, '本章按2026-10-12的plus HEAD fe59e4ae核对PC页面和Station后端契约。'
              'Station是独立Electron客户端环境，本仓库没有其可执行包和硬件运行现场。'
              '后端实现、历史报告或本地测试不能证明工位机已部署、离线重放已验收或真机生产已可用。')

    h2(doc, '21.1 PC入口与设备通道分工')
    table(doc, ['对象', '当前入口与权限', '操作边界'], [
        ['生产准备', 'MES工单/流转卡/工序派工；mes:pro:*', 'PC维护和执行可见按钮，先核对工艺、快照、数量与IPQC'],
        ['SOP', 'SOP文档/工序绑定/查看器；mes:sop:*', 'PC上传、审批、发布、绑定与显式已阅确认'],
        ['工位终端', 'mes/station/device/index；mes:station:device:list', 'PC查询台账、统计和工作中心设备，不是远程配置/启动Station'],
        ['DHR批记录', 'mes/dhr/record/index；mes:dhr:record:*', 'PC生成、复核、放行、驳回、归档、校验及签名链'],
        ['组件追溯', 'MES工单详情；mes:pro:trace:query', '页签查询应装组件、实际组件SN和组件去向'],
        ['Station操作', '/mes/station/**及心跳设备接口', '独立客户端按契约调用，不是PC新增过站/上料菜单'],
    ], widths=[3.0, 6.5, 7.0])
    para(doc, '上述是已有页面/权限，不保证某个部署已经同步菜单或给当前角色授权。'
              '工位终端PC页只读显示设备码、绑定工位、工序、版本、当前操作员、最后心跳和在线状态；'
              '“工作中心设备”是按工序默认工作中心反查，不是手工编辑终端绑定。')

    h2(doc, '21.2 生产前配置与独立环境核对')
    numbered(doc, '在PC维护产品、工艺路线、MBOM及工位/工作中心；核对工单工序、关键工序、固件/配置绑定与发布包快照。'
                   '手工或返工工单无工序、FLASH无绑定时禁止下达。')
    numbered(doc, '下达并创建/下达流转卡，按现场方案派工或开工；SN作业先核对SN、工单和流转卡归属，'
                   '批量计数作业核对唯一在制工序和计划量，不能用后台改状态代替执行。')
    numbered(doc, '维护SOP发布版本与工序绑定，必要时配置SOP_VERSION和SOP_ACKNOWLEDGED；'
                   '后者要求对应操作员完成PC查看器“我已阅读并确认”，单纯打开文件不满足。')
    numbered(doc, '维护工人档案employeeId并关联系统userId。设备通道扫工号时会拒绝不存在、已离职、未关联系统账号或账号停用的人员。'
                   '人员身份通过不等于所有工序资质门禁已实现。')
    numbered(doc, '由现场负责人核对独立Station客户端版本、安装环境、后台地址、设备/工位码、扫码枪、相机、串口/驱动、'
                   '刷写设备、私有文件下载及本地离线队列。此项需在独立客户端与真机完成，本手册不宣称已验收。')

    h2(doc, '21.3 密钥、租户及过站幂等')
    para(doc, '设备通道使用X-API-KEY，请由环境变量MES_API_KEY/mes.api-key安全注入；未配置、空头或不匹配均拒绝。'
              '免PC登录不等于免认证，密钥不得写入手册、截图、日志或提交。当前Station控制器在TenantHelper.dynamic("000000")'
              '中执行，并非任意租户可由请求头切换；跨租户接入须另行设计、授权及验收，不能复用此密钥混入其他租户数据。')
    table(doc, ['步骤', '设备通道', '门禁/结果'], [
        ['上岗', 'GET /mes/station/operator?code=工号', '查工人档案与有效系统账号，返回人员标识'],
        ['预校验', 'POST /mes/station/pass/validate', 'PREV_STEP/STATION_MATCH/DUPLICATE/QUALITY_HOLD四重检查'],
        ['单件报工', 'POST /mes/station/pass/report', '同事务再次守卫，SN+工位+clientTs及载荷指纹幂等'],
        ['批量计数', 'POST /mes/station/pass/report-batch', '工位+clientTs幂等，定位唯一在制工序，非负且正合计'],
        ['关键件', 'POST /mes/station/sn/bind或unbind', '组件挂父SN，防环；解绑/换板保留记录'],
    ], widths=[2.5, 7.0, 7.0])
    numbered(doc, '上岗后扫描SN与工位，检查四重结果；前序未完成、工位不符、重复过站、最新综测FAIL、未关闭NCR或SN状态异常时先处理原因。'
                   '预校验通过只代表查询时点，不保证之后报工一定成功。')
    numbered(doc, '提交真实结果、操作员、缺陷和clientTs，核对返回及履历。单件写入会再次检查状态/并发条件；'
                   '同幂等键只允许相同载荷重放，变更结果或操作员不能当作“重试成功”。')
    numbered(doc, '遇503或提交结果不确定时保持原时间戳和原载荷，恢复网络后核查并重放；'
                   '不得改时间戳制造新业务动作。客户端离线队列是否正确保存及重放须真机验证。')
    para(doc, '单件与批量通道不可混用数量口径。SN绑定只证明装配关系，不自动证明批次消耗、质量放行或ERP出库。')

    h2(doc, '21.4 SOP拉取、下载与查阅边界')
    numbered(doc, 'PC先上传、审批发布并绑定工序；Station按工位调用GET /mes/station/sop/current获取已发布文档，'
                   '版本查询用version-check，文件经/{sopId}/download代理下载，均需密钥。')
    numbered(doc, '核对返回SOP编号、版本和实际文件；下载失败、版本不符或缺文档时先排查发布状态、工序绑定、OSS与网络，不用旧缓存冒充当前版。')
    para(doc, 'Station current按operatorName登记普通查阅日志，不能等同于PC显式已阅确认产生的SOP_VIEW签名。'
              '要求签名门禁时须核对operatorId、版本和signatureId，不能把轮播/拉取成功当作已签署。')

    h2(doc, '21.5 上料防错与换料')
    numbered(doc, '先核对工位当前唯一在制工序。设备通道POST /mes/station/material/load扫描B批次码或RC条码实例；'
                   '来源从ERP wm_batch/wm_barcode解析，不是直接扫描任意MES批次主档编号。')
    numbered(doc, '系统按工单MBOM快照及替代料清单比对。无唯一在制工序、无快照或物料不匹配时查看返回原因/允许清单，处理后重试。')
    numbered(doc, '匹配时登记mes_material_load，同工单/工位/物料旧ACTIVE置REPLACED；用GET /mes/station/material/current核对应上物料及当前上料。')
    para(doc, '这是上料台账和物料防错，不等同于ERP库存扣减或MES批次消耗。'
              '未证实的质量、有效期、替代料授权和实物数量仍须按既有业务流程核对；PC无独立上料执行页。')

    h2(doc, '21.6 心跳、点检与安灯')
    para(doc, 'Station心跳真实路径为POST /mes/flash/report/station/heartbeat，并非/mes/station/heartbeat。'
              '携带deviceCode、stationCode、appVersion及当前操作员等；服务端upsert台账并解析工位/工序。'
              'PC在线阈值为最后心跳3分钟内且shutdownFlag不为1；正常退出shutdown=true立即置离线并清操作员。'
              '这与IoT MQTT设备15分钟最后在线时间检测是不同口径。')
    numbered(doc, '在“工位终端”刷新核对心跳、版本、人员与工作中心设备；离线先检查终端进程、网络、密钥和工位码，'
                   '不要修改台账假装上线。在线也不证明设备驱动、生产执行或扫码通过。')
    numbered(doc, 'Station点检走inspection/tasks及inspection/submit，核对真实检查项、结果和照片；不合格可联动设备FAULT及安灯。'
                   '安灯呼叫走andon/call，PC看板按响应→解决留痕，轮询latest不代表异常已处理。')

    h2(doc, '21.7 DHR批记录生成与复核')
    numbered(doc, 'PC“DHR批记录”选择“生成批记录”（mes:dhr:record:add），选择工单，按需填批次号/SN及备注；核对生成范围和重复范围提示。')
    para(doc, '固定十个分册：工单信息、生产过程、物料批次、设备记录、工装记录、作业指导书、质量检验、数据采集、SN追溯、NCR。'
              '系统聚合现有事实并计算分册/快照哈希；空分册仍会生成，完整的分册集合不等于每类业务都有完整数据。')
    numbered(doc, '“详情”逐册核对记录与空数据；DRAFT可“重建”（mes:dhr:record:edit），按当前事实重算快照。'
                   '重建前先纠正源记录，不在已冻结快照中补造生产数据。')
    numbered(doc, 'DRAFT“提交复核”（mes:dhr:record:submit）须二级认证，冻结快照；SUBMITTED由授权人“放行”或填写原因“驳回”，也须二级认证。'
                   'LOGIN_ONLY和认证失败不能降级放行。')
    numbered(doc, 'RELEASED可“归档”（mes:dhr:record:archive）；“校验”与“签名链”（mes:dhr:record:query）核对哈希和签名。'
                   '当前PC未提供REJECTED单重开按钮，不能从状态设计说明推导出可点击重建。')
    para(doc, '提交后快照不可重建；签名在系统库独立事务落台账，随后MES状态CAS失败可能留下“有授权记录、无业务流转”，'
              '需核对状态后重试。哈希校验通过只证明快照一致性/签名链，不证明真实生产全部合格，也不等于人工端到端验收。')

    h2(doc, '21.8 履历与追溯入口边界')
    table(doc, ['入口', '当前可查对象', '不能据此推定'], [
        ['PC SN追溯树', 'SN概要、工序链、子SN、父SN组装链', '客户物流和售后完整闭环'],
        ['PC工单组件追溯', '应装MBOM组件、实际组件SN、组件去向工单', '独立全履历看板已经存在'],
        ['Station trace/{snCode}', '刷写、综测、过站、关键件绑定四源时间线', 'PC已有同款时间线页面或全量无限历史'],
        ['PC DHR详情', '已构建范围的十类事实快照及签名链', '实时自动采集、自动质量放行或现场已验收'],
    ], widths=[3.5, 6.5, 6.5])
    para(doc, '“生产履历与追溯看板”流程图组织上述现有入口，不新增或承诺独立同名菜单；'
              'Station时间线有单源查询上限，空白或缺数据应追查来源、租户、权限和生成范围。')


def extend_iot(doc, h1, h2, h3, para, bullet, numbered, table):
    h2(doc, '12.8 当前接入与运行边界')
    para(doc, '核对基线为2026-10-12 plus HEAD fe59e4ae。MqttBrokerConfig实际import io.moquette.broker.Server并启动Moquette；'
              'AGENTS中的Vert.x描述与当前实现不一致，此处按代码保留Moquette。')
    h3(doc, '12.8.1 Broker与设备身份')
    table(doc, ['属性', '代码默认值', '核对事项'], [
        ['nexus.iot.mqtt.enabled', 'true', 'false不启动Broker，配置覆盖与端口监听须现场核对'],
        ['host / port', '0.0.0.0 / 1883', '默认值不是公网暴露授权，核对防火墙与传输保护'],
        ['allow-anonymous', 'false', '生产保持禁匿名；仍须设备启用与密钥校验'],
        ['websocket-port', '8083', '<=0不启用WebSocket；PC实时页实际使用SSE'],
        ['status-ttl-seconds', '3600', '缓存TTL不是设备离线判定阈值'],
    ], widths=[6.0, 3.0, 7.5])
    para(doc, 'Broker当前显式关闭持久化，不能承诺Broker重启后保存离线队列或保留消息。'
              '设备ClientID必须为deviceCode，username可空、非空必须与ClientID一致；password为deviceSecret，设备须ACTIVE。'
              '密钥只由授权人员在安全环境配置，禁止写入本手册及截图。')
    h3(doc, '12.8.2 Topic及异步数据链路')
    para(doc, '实际Topic为iot/{tenantId}/{deviceCode}/{bizType}/{action}，不是产品编码/设备编码两层路径。'
              '租户和设备段必须属于已认证设备；设备不能订阅通配符租户/设备段或其他设备Topic。'
              '消费端仍交叉核验Topic租户与设备记录，不能靠改Topic切换身份。')
    table(doc, ['Topic后两段', '当前处理', '注意事项'], [
        ['property/post', 'Redis数据Stream→IotStreamConsumer→IoT数据入库', '负载至少含property与value，timestamp为可选毫秒值'],
        ['event/post', '事件Stream→IotEventStreamConsumer→事件/联动处理', '事件已经分流，不能沿用“仅日志”旧注释'],
        ['property/set', '平台下行属性指令', '不是设备属性采集，核对实际指令权限与设备执行'],
        ['property/set_reply', '事件Stream→设备指令回执', '核对commandId与原指令，发送不等于设备已执行'],
    ], widths=[4.0, 7.0, 5.5])
    numbered(doc, '先在PC产品/设备页面维护并启用设备，核对对应角色的iot:product:*、iot:device:*权限与真实设备身份。'
                   '协议下拉出现OPC UA/Modbus/HTTP不证明这些原生接入驱动已实现，当前链路应按MQTT核对。')
    numbered(doc, '使用已授权测试设备发送property/post，依次核对Topic授权、Redis Stream、消费组、IoT数据记录与最新缓存。'
                   'Broker解析/XADD异常只记日志，不把MQTT发送返回当作业务已落库。')
    numbered(doc, '入库以Stream消息ID去重，失败消息保留pending重试，达到maxRetry（默认3）后ACK跳过并记录ERROR。'
                   '需排查毒消息与积压，不承诺无损全量采集；多实例须核对消费者名称和回收策略。')
    para(doc, '设备timestamp超前默认5分钟或滞后默认7天范围之外时改用服务端时间；最新缓存/SSE快照时间不等于原始采样时间。'
              'PC设备“实时”（iot:device:query）为SSE每3秒快照、60点滚动窗口及历史查询，不能当作硬实时控制。')
    h3(doc, '12.8.3 告警、心跳与MES归档')
    para(doc, '告警优先匹配数据库设备/产品/全局规则，无匹配才用YAML阈值兜底，支持持续时长、去抖和恢复。'
              '告警先落库，通知按SSE/MES安灯渠道保留SUCCESS/FAILED/SKIPPED结果；无人在线或Noop适配器不会保证收到安灯。')
    numbered(doc, '在现有告警规则/告警记录页面按权限配置、查询、确认/恢复并核对通知留痕；恢复IoT告警不等于PC安灯已经解决。')
    para(doc, 'IoT心跳任务按ONLINE且lastOnlineTime早于15分钟扫描，代码有每5分钟调度入口；'
              '最近上报时间是另一个缓存字段，不能声称每个MQTT数据包都会刷新lastOnlineTime。'
              'Station终端3分钟心跳阈值属于另一通道。')
    table(doc, ['任务', '当前实现', '上线核对'], [
        ['iotDeviceHeartbeatTask', '15分钟最后在线阈值，每5分钟本地兜底', 'Broker连接回调、任务日志及最后在线口径'],
        ['iotDataCleanupTask', '90天采集数据保留，每日02:00本地兜底', '真实保留策略、备份与删除权限'],
        ['iotStreamTrimTask', '数据Stream按MAX_LEN=100000裁剪，每小时本地兜底', '裁剪前仍可增长，不是消息已消费的证明；核对积压'],
        ['iotDataArchiveTask', 'MES逐租户AUTO采集点归档，每5分钟本地兜底', '采集点ACTIVE、iotDeviceId、属性筛选、游标与记录'],
    ], widths=[5.0, 6.5, 5.0])
    para(doc, '上述任务既有SnailJob执行器也有本地Scheduled兜底：snail-job.enabled=true时本地让位；'
              'false时通过SchedulerFallbackUtils分布式锁运行，仍需核对调度启用、Redis锁、实例配置和真实日志。'
              '有执行器或初始化种子不等于生产调度已成功。')
    para(doc, 'IoT归档从采集点lastArchiveTime游标拉取，首次约6分钟窗口，超120分钟分段，超过1440分钟缺口截断并告警；'
              '按属性筛选并去重写MES dc_data_record。不是同步回写每个MQTT包，也不能保证超过24小时的断档自动补齐。'
              '真实上线仍须核对库表基线、MQ/Redis/SSE、采集量、租户归属与调度，不运行初始化或清库来证明可用。')


def extend_cv(doc, h1, h2, h3, para, bullet, numbered, table):
    h2(doc, '13.5 工业缺陷检测与图片上传')
    para(doc, 'PC现有“工业缺陷检测”页面cv/defect/index，菜单/按钮权限cv:detect:query；'
              '前后端核对基线为2026-10-12 plus HEAD fe59e4ae。菜单种子存在不证明当前角色或存量环境已同步。')
    numbered(doc, '选择金属表面缺陷defect_metal或PCB缺陷defect_pcb；只选与场景对应的单张本地图片，不使用通用YOLO替代工业场景模型。')
    numbered(doc, '拖拽或选择JPEG/PNG/BMP/WebP，非空且≤10MB；等待原图成功解码与尺寸加载。'
                   '无效格式、过大、空文件或解码失败时先换有效图片，按钮不可用不等于模型异常。')
    numbered(doc, '具权限时点击“开始检测”。前端通过ERP代理POST /cv/detect/defect，multipart字段为file与scenario，'
                   '不要改成image或浏览器直连Vision。')
    numbered(doc, '查看原图检测框、类别汇总、最高置信度、缺陷数、总推理耗时及完成时间；bbox是原图像素[x1,y1,x2,y2]，'
                   '同类最高置信度不是平均值，耗时不是各框独立耗时。结合实物/原图人工复核。')
    numbered(doc, '换图片/换场景会清旧结果，失败显示错误且不沿用上次成功；'
                   '“未检出缺陷”仅是本次模型输出，不是质量合格、零风险或自动放行结论。')
    para(doc, '10MB及图片格式主要是当前页面校验；后端明确校验非空和场景，网关/上传限制须另核对。'
              '此页没有批量上传、摄像头连续检测、结果保存、质检单/NCR自动创建或自动业务回写按钮。')

    h2(doc, '13.6 场景模型、模板与服务边界')
    para(doc, '工业缺陷代理固定enableOcr=false，只支持defect_metal/defect_pcb；Vision返回success和effectiveScenario必须确认所选场景，'
              '不允许静默降级通用检测。模型未部署、场景未确认、服务不可用或响应非法时应处理错误，不把失败解释为“零缺陷”。')
    para(doc, '当前仓库没有独立CV模型上传/训练/阈值模板配置PC页；工业页只有两种场景选择。'
              '人脸底库“服务状态”展示可用性及模型列表，不是模型管理后台；权重、类别、推理阈值与版本须在独立Vision环境核对。')
    para(doc, 'MES“检验标准模板”（mes/qc/template/index）是IQC/IPQC/OQC项目、标准及判定快照来源，'
              '不是CV训练模板，不能把模型置信度自动转成模板最终判定。OCR标签解析规则也不等于视觉缺陷质量标准。')
    bullet(doc, '部署核对：nexus.vision启用及地址、独立服务就绪、对应工业模型、响应effectiveScenario、网络与上传链路。'
                '仓库代码或模型列表不能证明真机/产线精度、性能及人工验收。')
    bullet(doc, '质量复核：检验员对原图和实物按标准确认，必要时在已有质检/NCR入口另行登记，保持来源、人员、时间与依据。'
                '不能在此页点击推理后自动签署或自动写业务。')

    h2(doc, '13.7 检测结果与生产追溯')
    para(doc, '工业缺陷接口返回本次检测结果，不持久化图片、缺陷、SN或批次关联；页面完成时间是浏览器展示，刷新后不是可查询的业务历史。'
              '人工另行登记时应核对工单、流转卡、SN/批次、图片来源、场景/模型版本、检验标准、复核人及结论；未建立关联就不能宣称自动追溯。')
    para(doc, '当前追溯入口分别为PC SN树、工单组件追溯、批次追溯及DHR；Station trace聚合刷写/综测/过站/绑定记录，'
              '不自动包含此页面的临时缺陷输出。无独立“CV缺陷历史/复核台账”PC页，不能把后端推理接口当已可操作的追溯看板。')

    h2(doc, '13.8 FAIL复查与身份验证边界')
    para(doc, '现有“FAIL复查”cv/face-review.vue针对人脸考勤sch_attendance，不是金属/PCB缺陷复核页。'
              '列表查询需sch:attendance:list，误判/代打卡动作及私有照片代理需sch:attendance:review。')
    numbered(doc, '按日期和工人姓名查询FAIL考勤记录，查看相似度、验证时间及可用存证照片；无照片时不得编造复核证据。')
    numbered(doc, '管理员核实后“误判”改PASS，或“代打卡”保持FAIL，填复核依据后确认；后端追加FACE-REVIEW审计备注并更新验证时间。')
    para(doc, '页面提示“24h内复核”，但当前markFaceVerified服务没有24小时拒绝门禁，不能写成系统强制超时不可复核。'
              'NONE降级补跑任务的24小时选取窗口是另一口径，须核对任务实际运行；补跑不自动替代人工复查。')
    para(doc, '派工“刷脸登录”实际是1:N候选+1:1确认的操作员上下文，不签发登录Token；'
              '检测工业缺陷、考勤FAIL与刷写/综测FAIL是不同对象，不得共用一个“FAIL即NCR”自动结论。')
