# Nexus 工程连接器使用指南

> 更新：2026-10-16；范围：嘉立创EDA模板草稿、私有产物、PNG及OBJ查看。
> 关联：[设计](../specs/2026-10-15-ai-嘉立创EDA-PCB草稿生成与产物预览设计规范.md)、[实施计划](../plans/2026-10-16-ai-嘉立创EDA-PCB草稿与产物预览实施计划.md)、[协议](../specs/2026-10-16-ai-工程连接器协议v1.md)、[客户端验证](../plans/2026-10-16-ai-嘉立创EDA-客户端能力验证报告.md)。

## 1. 使用边界

功能默认关闭。首次启用需在获授权的系统库安装工程三表、菜单和权限，核对Redis与专用私桶；本次开发不自动迁移现有库。新装定义在 `script/sql/ry-vue.sql`，存量增量为 `script/sql/update/2026-10-16-ai-engineering-pcb.sql`，不能把主初始化脚本导入存量库。

服务端显式配置：

```yaml
nexus:
  ai:
    engineering:
      enabled: true
      oss-config-key: engineering-private
```

`engineering-private` 是需由管理员配置的私有OSS配置键示例，不是仓库已配置的服务。必须使用PRIVATE访问策略，不隐式回落默认桶。前端构建设置 `VITE_APP_ENGINEERING_ENABLED=true` 才显示工程对话入口；后端权限始终独立校验。

当前应用全局multipart默认限制为单文件10MB、请求20MB；工程服务100MiB仅是内部单文件上限，实际还受HTTP入口限制。较大OBJ或源工程需在获授权的集成配置中同步核对 `spring.servlet.multipart.max-file-size`、`max-request-size` 及反向代理限制。当前演示产物均小于1MB，无需调整全局配置。

为测试用户授权 `ai:engineering:device`、`ai:engineering:task`、`ai:engineering:query`、`ai:engineering:download`。任务与产物默认仅所有者可见。工具管理中启用“嘉立创EDA工程工具”；它属于独立ENGINEERING类别，普通ERP查询不能使用写工程动作。

## 2. 安装连接器

Windows工程师电脑需Node.js 24 LTS及嘉立创EDA专业版。验证基线为客户端4.1.60；这个版本虽然在帮助中列出MCP，实际启动会返回MCP_NOT_SUPPORTED。本连接器提供本机STDIO MCP桥接，由固定脚本调用官方CLI/API，不需要电脑开放入站端口。

源码目录为仓库根目录下 `ruoyi-extend/nexus-engineering-connector/`，在该目录执行：

```powershell
npm ci
npm run build
Copy-Item config.example.json config.json
node dist/cli.js doctor --config config.json
```

只编辑 `config.json` 中的Nexus地址、EDA执行文件和专属工作目录。地址使用HTTPS；环回开发HTTP需显式 `development=true`。不在配置中填写浏览器Token、模型Key、设备凭证或任意脚本。

也可使用 `npm run package:win` 生成的分发包；解压后配置并执行 `node dist/cli.js`。分发包有SHA-256及逐文件清单，运行目录和凭证不在分发包内。

## 3. 配对与运行

1. 在Nexus工程设备页生成一次性配对码，5分钟内兑换。
2. 执行 `node dist/cli.js pair --config config.json`，按提示输入配对码。
3. 执行 `node dist/cli.js run --config config.json`；连接器主动心跳并领取当前用户设备的任务。
4. 在AI对话中选择自己的工程设备，使用例如“基于温湿度模板生成50×35mm双层PCB草稿，并展示线路和3D”。
5. 跟随任务卡片进入预览。工具结束只表示派发完成；以任务详情和已确认产物为准。

连接检查（`CHECK_CONNECTION`）的卡片显示“连接检查通过/失败”，通过“查看检查结果”读取实际 CLI 版本、编辑器版本和传输模式；它不生成线路图、BOM 或模型。结果随任务保存，刷新后仍可读取。旧任务没有保存详细诊断时明确显示“未保存版本等详细诊断”，不会用当前设备信息补写历史结果。需要生成 PCB 时应明确发起生成草稿请求。

任务终态提交后通过现有工程 SSE 事件更新卡片；当前显示的活动任务结束时给出一次结果提示。读取历史任务不会重放完成提示；SSE 不可达时仍由已有任务轮询读取持久化结果。

当前模板只有固定器件与初步走线，支持40–100mm × 30–100mm板框、双层。它是集成演示模板，不是已经电气审核的量产设计。生成保留真实DRC违规、未连接网络和未发布标识，不会更新ERP BOM、采购单或MES工单。

## 4. 查看与继续编辑

工程任务列表地址为 `/ai/chat/engineering/task`。详情页的“返回任务列表”与侧栏高亮均使用该地址；旧列表地址 `/ai/engineering/task` 自动跳转。已有任务详情 `/ai/engineering/task/<taskId>` 保持可用。结果页将任务概览、检查结论与产物查看分别呈现；产物齐全但 DRC 存在违规时，显示真实报告问题与查看入口，原始服务端代码仍保留在“任务记录”中。

预览页提供PNG缩放、多图选择、BOM、DRC、OBJ/MTL及源文件下载。三维视图支持旋转、缩放、平移、重置视角；只读取同任务产物清单中的材质和纹理，缺材质时降级为统一材质并提示。STEP/GLB转换和逐网络高亮不在首期范围。

“打开本地工程”通过设备任务执行，不在浏览器执行磁盘路径。工程保存在连接器专属目录 `tasks/<taskId>/<attempt>/project`，可由工程师在嘉立创继续编辑。派生打开、导出与DRC任务继承该工程引用。

## 5. 取消、断线与故障

- 每台设备串行执行，3秒领取、15秒心跳、90秒租约且30秒续约。
- 取消在步骤边界生效，不承诺撤销已经提交给编辑器的更改；不强制关闭用户原有会话。
- 本地日志记录动作开始/完成，租约与待确认事件另经DPAPI保护。重启先认证恢复已有任务，已确认执行只恢复上传；未确认的设计写入不会自动重放，按EXECUTION_UNCERTAIN保留文件供核对。
- 上传失败先查询服务端清单，哈希相同则复用，其他情况按短期票据重试。首期不做分片续传。
- 部分产物失败可显示已有文件，缺报告不显示DRC通过。无OBJ时不使用样例模型冒充。
- Ctrl+C停止领取；`unpair` 撤销设备并清理加密凭证，源工程保留。

刷新凭证采用Windows CurrentUser DPAPI保护并绑定Nexus地址。密码变化、租户/用户停用、撤销权限和解绑会阻止后续访问。不得把identity.dpapi、journal、真实工程或设备凭证上传到代码仓库。

## 6. 验证方式

连接器运行 `npm test`、`npm run type-check`；前端运行 `npm run test:engineering`、`npm run type-check`、`npm run build:prod`。服务端按子计划运行非零JUnit/H2测试。真实EDA导出、隔离浏览器渲染、服务端隔离接口测试和生产部署分别记载，互不替代。

2026-10-16连接结果及私有归档修复的本地验证：后端工程/存储20套、75例通过，无失败或跳过；此前连接器49例、前端75例及相关类型检查、构建通过。本次重启后已通过真实浏览器完成“正常登录→选择设备→AI对话→连接器生成→七类产物私有归档→线路/BOM/DRC/OBJ预览→刷新及源工程下载”，并点击实际聊天卡片的“查看检查结果”确认诊断可读。测试草稿有8项真实DRC违规，状态为部分成功，不能视为投产验收。发现并修正归档后缀超出既有sys_oss字段长度的问题，无需数据库迁移。完整范围、任务编号和证据见[修复计划及验证记录](../plans/2026-10-16-engineering-result-feedback-fix.md)。

## 合并与新环境初始化（2026-10-17）

工程功能及结果回传 / 预览导航修复已合并到 `plus`。其他开发者可使用[可共享系统库快照](../../../script/sql/snapshots/2026-10-17/README.md)初始化全新空库；快照包含工程三表、菜单和演示管理员，不包含本机设备身份、模型或 OSS 配置、工程任务和文件。按本指南配置自己的服务、启用内置工程工具并重新配对设备。现有环境仍应按已审核基线单独处理增量，不能重导主安装 SQL。

[合并与验证记录](../merge/2026-10-17-engineering-plus-integration.md)汇总真实任务、UI 截图、回归计数与数据库恢复范围。生产启用、工程设计修正和投产审核需另行完成。
