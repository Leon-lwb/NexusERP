# Windows 工程连接器桌面应用实施计划

> 日期：2026-10-19。仅制作可审阅的一键启动应用；不提交、不推送，不操作业务数据库或真实设备绑定。

**Goal:** Windows 用户解压后双击 EXE，首次通过图形界面配置、配对，后续双击自动启动已配对连接器。

**Architecture:** C# Windows Forms 启动器管理现有 Node.js CLI，沿用设备认证、DPAPI、任务租约、MCP 和上传协议。分发包内置经过版本检查的 Windows x64 Node.js 24 及其许可证；配置和运行数据写入当前用户 LocalApplicationData/Nexus/EngineeringConnector。

**Tech Stack:** Windows Forms/.NET Framework 4.8（Windows 10/11）、C#、Node.js 24、TypeScript、fflate。

## 设计决策

- 批处理启动仍需要命令行配置；Electron 需要引入 Chromium。选择小型原生 Windows Forms 启动器，复用 Windows Framework 并携带 Node。
- 提供 Nexus 地址、EDA EXE 选择、HTTP/HTTPS地址、保存、检查、配对、启动、停止、解绑和有限长度日志。
- 已配置且有加密身份则自动启动；关闭窗口收至托盘；托盘退出先停止工作进程。同一 Windows 用户会话只运行一个界面实例。
- 停止使用专属 stdin 固定 stop 命令；父通道断开也停止。超时兜底只终止本次 Node 工作者，不杀 EDA 或其他 Node。
- GUI 不读取浏览器 Token，不保存配对码或访问凭证；修改服务地址需重新配对。
- 只用临时配置、假工作进程及静态窗口渲染验证，不自动绑定真实 Nexus、不生成工程。

## Task 1：桌面退出控制

- [x] 新增 `test/desktop-control.test.mjs`，先验证固定指令、无效输入、父通道关闭和释放监听。
- [x] 运行定向测试并确认缺失功能时失败。
- [x] 新增 `src/desktop-control.ts`，在 `src/cli.ts` 的 `run --desktop` 接入 AbortController，并覆盖认证、MCP、轮询与清理。
- [x] 定向测试通过。

## Task 2：原生桌面应用

- [x] 新增 `desktop/ConnectorSettings.cs`：配置校验、每用户路径、安全命令行参数。
- [x] 新增 `desktop/ConnectorProcess.cs`：固定 Node/CLI、标准输入配对、正常停止、退出结果。
- [x] 新增 `desktop/MainForm.cs` 与 `desktop/Program.cs`：图形操作、托盘、STA、会话单实例。
- [x] 新增 `desktop/SelfTests.cs`：配置校验、中文空格路径往返、参数边界、假工作进程生命周期及不联网的窗口预览。
- [x] 新增 `scripts/build-desktop.ps1`：用 Windows Framework 编译器构建，不依赖本机过期 .NET 5 SDK。

## Task 3：免安装分发

- [x] 先补 `test/desktop-distribution.test.mjs`：校验 EXE、Node、许可证、逐文件摘要、配置与身份排除。
- [x] 抽取 `scripts/distribution.mjs` 共用固定白名单，保留原 `package:win`。
- [x] 新增 `scripts/package-desktop.mjs` 与 `package:desktop`：生成 ZIP、SHA-256、可运行目录。
- [x] 新增 `desktop/快速开始.md` 并更新 README。

## Task 4：交付验证

- [x] `npm test`：原 49 例及新增回归，零失败/跳过。
- [x] `npm run type-check` 和 `npm run package:desktop`。
- [x] 解压至含中文空格的临时目录，执行 GUI 自测，不访问真实服务或 EDA。
- [x] 核对窗口预览、`git diff --check`、修改范围及包内容，交付 ZIP 和 EXE 路径。


## 2026-10-19 本机验证结果（首次桌面包，HTTP调整前）

- 基线：原有连接器49例全部通过。最终 `npm test`：56例，56通过，0失败、0跳过、0取消。
- `npm run type-check`：退出码0。
- `npm run package:desktop`：退出码0，产出3771个文件的Windows x64桌面包，41,337,406字节（约39.4MiB）。
- 分发集成测试将真实ZIP解压至含中文、空格的隔离临时目录，验证EXE/Node/许可证/逐文件哈希/ZIP摘要及用户配置、身份和运行文件排除；实际执行原生自测。
- 最终分发目录的原生 `--self-test`：19项配置、互斥和假进程检查通过，failed=null；另通过窗口非空渲染、控件容器边界、WindowsShutDown事件、停止中退出等待等专项断言。最终截图为 `release/desktop-self-test.png`，未显示真实配置信息。
- 代码复核发现的六项问题已逐项复现、修复并复核：停止期间退出等待、系统关闭允许退出、诊断取消传播、readline错误、互斥句柄存续和机器NODE_OPTIONS/NODE_PATH干扰。
- 额外用合成DPAPI凭证验证父进程Restricted策略；助手仅对自身子进程指定执行策略，不修改用户或机器策略，组策略仍优先。
- 最终ZIP：`release/nexus-engineering-connector-0.1.0-win-x64-desktop.zip`；逐文件清单3770项，归档另含清单自身。内含Windows x64 Node.js v24.21.0及许可证。
- SHA-256：`c62a31861896495c16a879a4a53b5e7bf3e56ae69804b13b75f5ef1564ff9891`，已与同名`.sha256`核对。
- 签名状态：NotSigned，当前为未签名开发便携分发。
- 修改范围：连接器桌面启动器、退出/诊断/凭证助手的相关运行兼容、分发脚本、定向测试、README及工程使用指南。没有改动他人的数据库变更登记和其他未提交文件；未提交、未推送。
- 未验证范围：未自动配对任何真实Nexus、未调用真实EDA生成工程、未操作业务数据库或启停既有服务；正式发布签名与其他目标机器的人工验收未在本次执行。


## HTTP支持调整（2026-10-19 用户补充）

用户实际使用HTTP服务，要求桌面应用直接支持HTTP。此前仅支持HTTPS/显式环回HTTP的限制已由本次明确需求替换。

- [x] 桌面设置与Node配置统一只限制协议为HTTP/HTTPS，允许普通内网IP、域名、环回及IPv6，不依赖development。
- [x] 去掉界面“仅环回开发”复选框，显示HTTP/HTTPS支持提示；旧development字段与已有配置保留兼容。
- [x] 先补HTTP地址回归，确认原Node测试5例失败、原生自测以截图中的校验提示失败，再修改实现。
- [x] 保留URL凭证/查询/片段、非HTTP协议、未知配置和工作目录的既有校验。
- [x] 打包增加release子目录名参数，使用独立HTTP分发目录，避免覆盖用户正在运行的首次桌面包。
- [x] 完整回归、类型检查、重新打包与原生HTTP自检；核对新ZIP摘要，交付新路径。


### HTTP版本验证与交付

- `npm test`：60例，60通过，0失败、0跳过、0取消；包括4个新增HTTP地址案例及完整桌面ZIP的中文空格路径解压运行验证。
- `npm run type-check`：退出码0。
- `npm run package:desktop -- --output-directory NexusEngineeringConnector-http`：退出码0。
- 最终新版EXE的 `--self-test`：22项，failed=null，覆盖普通HTTP内网IP、域名、环回、IPv6及既有退出/凭证边界；窗口截图已核对，原环回开发复选框已替换为HTTP/HTTPS说明。
- 新启动目录：`release/NexusEngineeringConnector-http/`。原 `release/NexusEngineeringConnector/` 未覆盖，用户既有进程未停止，用户配置和配对身份未修改。切换版本需用户从旧应用托盘退出后启动新版。
- 更新后的ZIP：`release/nexus-engineering-connector-0.1.0-win-x64-desktop.zip`，41,337,592字节，3771个文件。
- 当前ZIP SHA-256：`53fb1889cdc0bf2065ab583b8d9cd2d640a2cc67342fc4ce82a5807562cadac5`，与同名`.sha256`匹配；首次包摘要仅作历史记录。
- 此次只验证隔离HTTP任务服务、配置和假工作进程，未访问用户实际HTTP服务或数据库，未自动配对真实设备。未提交、未推送。


## 后续API识别与本地开关修复

同日增加工程API前缀识别、安全诊断、桌面配对取消保护及dev/local开关；最新归档、摘要与验证范围以[配对API诊断记录](2026-10-19-engineering-pairing-api-diagnosis.md)为准。当前独立启动目录为`release/NexusEngineeringConnector-api/`，先前目录保留。
