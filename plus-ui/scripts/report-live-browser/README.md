# 真实本机报表浏览器回归

在`plus-ui/`执行`npm run test:report:live`。该命令使用真实运行的应用和数据库，不安装Mock、不改写网络响应，不属于普通`test:report`或隔离`test:report:browser`的一部分。

时间显示的有限复验使用`REPORT_LIVE_SUITE=time`，`REPORT_LIVE_TIME_RUN`指定本线程已有完整运行的证据标识（本机为`lK4f8u`）。仅操作既有模板、批次和自身订阅的读取入口，以及MES工单/检验的只读样例，不创建资源、发布版本或改写业务数据。逐项将真实响应与页面的北京时间文本比较，原始时间只留在内存，缺少非空时间样例时明确记blocked。

```powershell
$env:REPORT_LIVE_ORIGIN = 'http://127.0.0.1'
$env:REPORT_LIVE_SUITE = 'time'
$env:REPORT_LIVE_TIME_RUN = 'lK4f8u'
$env:REPORT_LIVE_STOP_ON_FAILURE = '1'
$env:REPORT_LIVE_TIMEOUT_MS = '180000'
npm run test:report:live
```

默认访问`http://127.0.0.1:18081`；通过`REPORT_LIVE_ORIGIN`指定其他本机站点，只接受回环主机。实际前后端服务需要已经运行，脚本不会启动或停止用户服务。可使用`REPORT_TEST_BROWSER`指定Edge/Chrome。登录通过页面默认预填账号，或通过`REPORT_LIVE_USERNAME`、`REPORT_LIVE_PASSWORD`提供当前环境的测试账号；凭据仅用于内存键盘输入，不写日志。需要验证码时记录blocked，不绕过认证。

测试按侧栏菜单进入中心、数据集、设计器、历史、预览、导出、查询、交叉、图片、批次、仪表盘、订阅和交付。实际按钮、下拉选择、文件控件与键盘触发原有请求。会创建带`LIVE_RPT_`前缀的目录、模板、图片、查询、批次、仪表盘及自身订阅，基础模板仅走既有管理员增量按钮；已有定义、业务单据及通知开关不修改。使用专门本地测试环境运行，勿把回环代理指向远端生产。

报表查询独立SELECT数据源未启用时，验证`QUERY_SOURCE_DISABLED`和用户可见说明后记为blocked；不会借用业务写连接。实际PDF/Excel只在本次自有下载目录内解析签名/页数，结束删除，不把业务文件或内容提交到仓库。系统打印按钮可被点击，真实打印机和纸张效果仍需现场验收。

此前已确认同一环境的`QUERY_SOURCE_DISABLED`且要求不重复请求时，设置`REPORT_LIVE_QUERY_SOURCE_DISABLED=1`，仅将该预览步骤记为已知环境blocked，不再发起样例预览；这不代表本次重新验证了错误码或查询能力。其余全套步骤继续执行。

下拉收起仅向已展开且确认聚焦的原生combobox发送Escape，避免关闭所属对话框。模板导入先等待标题匹配的可见弹窗，再经其原生file控件选取本次下载；成功需同时满足真实导入POST的HTTP/业务成功、`/report/designer?id=完整字符串ID`、新草稿编码/标题匹配及已保存状态，不能仅凭已有标题输入框判定。

默认all还包含本次查询/数据集授权与状态、批次离开恢复/合并PDF、仪表盘六类组件与编辑授权扩展。`REPORT_LIVE_EXTENDED=0`仅用于优先排查关键主流程，证据会明确记录`extended:false`，不代表扩展按钮已验收。失败任务恢复、订阅取消或重试缺少真实适用状态时记blocked，不制造失败或绕过状态约束。

订阅操作只定位本次自有订阅和当前接收人，执行行需与已有真实响应的顺序、行数和页面状态一致。执行记录、交付及导出中心原有请求的任务状态只在内存观察；取消候选与R10页面一致，只接受`PENDING/QUEUED/PREPARING/RETRY_WAIT/BLOCKED`，有任务时还需确认实际任务处于`QUEUED/RUNNING/FAILED`，其中失败子作业仍要求所属执行允许取消。“已入队”不能证明实际任务尚未完成，已成功或已领取任务不能作为取消前提。有任务的执行重试还必须由真实导出响应确认`FAILED`且`canRetry:true`；无任务时只接受`FAILED/BLOCKED/RETRY_WAIT`。重试成功后检查原执行ID保持，有原任务时任务ID也必须保持。取消成功后核对同一完整执行ID的`CANCELLED`状态，要求取消/重试按钮均原生禁用，通过CDP鼠标点击其位置确认两者都不发请求，再刷新确认终态保持；不会要求取消后重试成功。缺少可取消或可恢复状态才记blocked，实际接口错误继续记FAIL，不将409放行；这些私有执行ID和状态Map不进入验证JSON。

取消链路另设一步：仅在本次自身执行已成功取消且已有子作业时，通过真实页面刷新普通导出，核对同一完整任务ID已被来源guard隐藏，或明确`canRetry:false`、未恢复排队/执行且没有可用下载和重试入口；再刷新自己的交付目录，确认本次单接收人单次订阅的交付映射为`CANCELLED`、`jobId:null`且领取按钮禁用，最后重新进入执行记录核对同一`CANCELLED`及禁用按钮。该步只读，不能发送重试、取消或领取请求。没有已取消子作业时明确记blocked；新响应缺失、入口重开或终态被覆盖均记FAIL。导出和交付列表仅存私有内存，不序列化；普通页面刷新不能确定性制造reconcile并发竞态，此竞争仍依赖后端并发回归，浏览器不主动调用维护任务、不启用调度或通知。

字典实测先在本次自有采购复制模板中通过字段栏加入币种可见明细列并保存发布，再在其运行期间被动观察原有page/layout响应；基础模板的页眉/页脚隐藏绑定不属于page投影，不能据此声称字典业务数据缺失。币种原码、显示名称和期望Set仅留内存。实际页面和下载PDF按响应名称核对；验证文件仅保存观察数、原码保持数、页面命中数和PDF命中数。满足可见列前提后真实单据仍未命中字典时记blocked，不写标签、业务行或PDF文本。

浏览器profile、下载及脱敏证据位于`node_modules/.tmp/report-live-run-*`。finally仅结束本次浏览器，验证绝对目标路径后回收profile/downloads，保留独立`*-evidence/verification.json`。证据只包含步骤、路由模板、HTTP/业务码和文件尺寸/页数，不含请求正文、业务行、令牌或密码。

每次下载前记录Browser下载事件游标和文件版本；只接受本次`downloadWillBegin`对应GUID的`downloadProgress=completed`，同时核对自有目录中的新文件或更新版本及完成字节数。同名覆盖不能复用旧文件，恢复批次再次领取还必须有不同GUID。最终证据只保存下载开始/完成/取消计数，不写GUID、文件名或路径。

默认全套超时15分钟，可用`REPORT_LIVE_TIMEOUT_MS`设置不超过30分钟。`REPORT_LIVE_SUITE=designer`跳过数据集样例阶段以调试后续链路；最终验收使用默认all。`REPORT_LIVE_STOP_ON_FAILURE=1`在首个失败后中断当前调试运行。全部通过返回0，失败返回1，仅环境blocked返回2；不能把退出2或局部子套当作全部通过。

首个失败触发中断后，后续未执行步骤不再追加FAIL；旧证据中的中断连带FAIL不会被重写。数据集状态步骤同时检查真实PUT的HTTP/业务成功和实际状态标签，不能把服务端拒绝伪装成纯等待问题。

`extensions`仅调试查询之后的扩展链路，使用已有供应商基础模板；`finish`在此基础上跳过依赖PDF工作线程的单张与批次领取；`exports`、`pdf`只用于观察已有任务或复现PDF失败。它们均不代表全模块验收。启用并行后端实例时必须确认队列上的所有工作线程已更新；旧实例可能领取新任务并继续按旧逻辑处理，本次曾明确复现此情况。

有限复验使用`REPORT_LIVE_SUITE=readonly`，复用本线程既有证据中的自有资源，不创建目录、模板、订阅、运行或导出任务。先从真实页面响应确认当前完整用户ID，再以原运行的`resourcePrefix`定位订阅，核对完整订阅ID、执行ID和自身recipient。检查既有取消行的原生禁用按钮与鼠标点击零POST、普通导出无下载/重试入口、交付取消且隐藏job，再刷新确认原取消终态。样式格式仅选择已有成功的`XLSX_LAYOUT`任务，通过产品支持的`id/runId`路由复用原运行，要求真实运行响应证明其属于指定自有模板，随后实际领取一次，核对本次下载事件、字节数与XLSX签名。所有报表请求必须是GET；合法失败重试没有状态前提时仍记blocked。

在`plus-ui/`下执行以下有限复验；`REPORT_LIVE_CANCEL_RUN`和`REPORT_LIVE_STYLE_RUN`必须是本线程既有证据目录的六位运行标识，默认分别为`KIEdMS`和`l9Gc7S`。脚本仅从相应原始JSON读取测试资源前缀，不将完整业务标识、响应或文本写入证据。

```powershell
$env:REPORT_LIVE_ORIGIN = 'http://127.0.0.1'
$env:REPORT_LIVE_SUITE = 'readonly'
$env:REPORT_LIVE_STOP_ON_FAILURE = '1'
$env:REPORT_LIVE_TIMEOUT_MS = '180000'
$env:REPORT_LIVE_CANCEL_RUN = 'KIEdMS'
$env:REPORT_LIVE_STYLE_RUN = 'l9Gc7S'
npm run test:report:live
```

2026-10-18已完成的extended all原件为`node_modules/.tmp/report-live-run-lK4f8u-evidence/verification.json`：58步、53通过、0失败、5项blocked，退出2；414个报表请求无HTTP/业务失败，9次下载全部完成。字典观察、原码保持、页面命中、PDF命中均为1；浏览器、profile和下载目录均已回收。五项未触发前提分别为：

- 独立SELECT查询源未启用，按既有确认不重复发起预览。
- 本次批次全部成功，没有失败或待入队项可恢复。
- 本次自身订阅已完成，没有已确认可取消的未完成执行。
- 该轮没有已取消子作业，取消后的跨入口检查未触发。
- 没有自身可恢复的FAILED/BLOCKED/RETRY_WAIT执行，不发送重试POST。

`safeSummary`已修正：上述固定前提说明重复脱敏保持原意；只有真实运行/信号中断或AbortError才归类为中断，包含“取消”的状态或断言不再被误判。lK4f8u原件中相关原因文本保留原样，上述列表是其步骤定义及零取消/重试POST对应的准确解释，不能据旧“运行已中断”摘要认定该轮实际中断。

重复全套`l9Gc7S`已按用户要求通过关闭本次自有浏览器停止，原件保留在`node_modules/.tmp/report-live-run-l9Gc7S-evidence/verification.json`：样式入队及文件GET均200、5060字节XLSX已验证、5次下载均完成，随后因CDP断连记录样式步骤FAIL；这是主动停止造成的未完成轮次，不是产品导出失败或下载超时。后续有限补测`node_modules/.tmp/report-live-run-Ek35V0-evidence/verification.json`已再次确认同一自有样式任务的原运行归属及5060字节文件实际领取，1次下载完成、零报表POST；该次后续已完成行的组合取证前提断言FAIL仍原样保留，不能把该次整体当作通过。

来源guard修复前的有限只读证据为`node_modules/.tmp/report-live-run-1rpaz0-evidence/verification.json`：5步、4通过、1失败，8个报表请求全为GET、零下载，清理完成。既有自身CANCELLED行的按钮原生禁用、点击零POST及普通导出无重试入口已通过；交付刷新仍投影为可领取状态而FAIL。该FAIL的旧原因摘要也曾被“取消”误归类为中断，实际是交付断言失败。静态定位为`ReportDeliveryService.list/claim`只按子job状态而未拒绝历史取消父执行，已交主线程修复；没有发送实际领取POST。恢复服务后只运行上述readonly套件复验此记录与既有样式文件，不重跑全套，不重写任何历史FAIL证据。

R11后端恢复后的有限复验已于2026-10-18 17:52:11–17:52:40（+08:00）完成：`node_modules/.tmp/report-live-run-RKpgzT-evidence/verification.json`，日志`node_modules/.tmp/report-live-r11-final-readonly-20261018.log`。共11项，10通过、0失败、1项blocked，退出2。既有自身CANCELLED行的两个按钮均原生禁用，实际鼠标点击零POST；普通导出没有该子作业的可用下载/重试入口；交付准确映射`CANCELLED`且`jobId:null`、领取禁用；跨入口刷新后完整订阅、执行和子作业ID保持，取消终态未复活。既有样式任务通过原运行响应确认属于本线程自有模板，再次实际领取5060字节XLSX，1次下载开始/完成、0取消。25个报表请求全部GET，零HTTP/业务失败、零页面或脚本异常、零新资源/任务。唯一blocked为没有自身可恢复的FAILED/BLOCKED/RETRY_WAIT执行，具体原因在JSON中稳定保留。浏览器、profile和下载已回收，证据无私有状态Map、显示名称、凭据或完整业务标识。本轮没有调用禁用的取消、重试或领取POST；原extended all及历史FAIL原件均保留。
