# Nexus 本地工程连接器

本工具在Windows用户会话内运行，提供原生桌面应用和Node.js 24 CLI。Nexus通过HTTP或HTTPS分发任务，连接器使用本机受控MCP桥接调用嘉立创CLI，回传私有产物。首期面向集成验证，模板不具备生产发布资格。

## Windows双击启动（推荐）

下载：[Gitee发行版 engineering-connector-v0.1.0](https://gitee.com/NexusERP/nexus-erp/releases/tag/engineering-connector-v0.1.0)。ZIP内同时提供`快速开始.md`和`使用说明.md`，发行页也提供独立文档及SHA-256文件。

获取 `nexus-engineering-connector-0.1.0-win-x64-desktop.zip` 后完整解压，双击 `NexusEngineeringConnector.exe`。支持Windows 10 22H2 / Windows 11 x64和.NET Framework 4.8；包内自带Node.js 24，用户无需安装Node或npm，仍需自行安装嘉立创EDA专业版。

首次在窗口填写Nexus地址、选择EDA执行文件，保存设置并输入网页工程设备页生成的一次性配对码，点击“配对设备”和“启动连接器”。后续双击自动启动已配对设备；关闭窗口收至托盘，托盘可恢复窗口、停止和退出。

地址可填写站点根地址或完整API基地址。连接器在发送配对码或设备凭证前，用无认证GET识别本站点的直连、`/prod-api`、`/dev-api`路径；不会携带凭证跟随重定向。反向代理仅开放`/prod-api`时，CLI手动配置也可直接使用`http://站点/prod-api`。网页菜单路径与API路径不同。

配置与运行文件位于 `%LOCALAPPDATA%\Nexus\EngineeringConnector`，不写入分发目录；配对码不落盘，身份继续使用当前Windows用户DPAPI保护。修改Nexus地址后须重新配对。停止使用固定控制通道取消本次运行，工程文件保留；退出超时会明确提示核对任务。

构建机使用Windows x64 Node.js 24并保留Node安装目录内的LICENSE：

```powershell
npm ci
npm run package:desktop
```

产出在 `release/`：桌面ZIP、ZIP的SHA-256、可直接运行的 `NexusEngineeringConnector/` 目录。应用必须连同runtime、dist、node_modules、templates等一起分发，不能只复制EXE。原生启动器通过Windows Framework编译器生成，不依赖安装.NET SDK。当前产物未代码签名，正式发布及目标机器验收单独进行。详见 [桌面快速开始](https://gitee.com/NexusERP/nexus-erp/blob/engineering-connector-v0.1.0/ruoyi-extend/nexus-engineering-connector/desktop/快速开始.md)及[完整使用说明](https://gitee.com/NexusERP/nexus-erp/blob/engineering-connector-v0.1.0/ruoyi-extend/nexus-engineering-connector/desktop/使用说明.md)。旧应用运行时可使用 `npm run package:desktop -- --output-directory NexusEngineeringConnector-http` 输出到独立目录。

## 命令行使用

```powershell
npm ci
npm run build
Copy-Item config.example.json config.json
node dist/cli.js doctor --config config.json
node dist/cli.js pair --config config.json
node dist/cli.js run --config config.json
node dist/cli.js unpair --config config.json
```

配置serverUrl为Nexus地址，executable为已安装的嘉立创专业版exe，workspaceRoot为专属运行目录。serverUrl直接支持HTTP和HTTPS，可填写内网IP或域名；development字段仅为兼容旧配置保留，不再限制HTTP。凭证用Windows CurrentUser DPAPI加密；修改服务地址需重新配对。只从Nexus网页生成一次性配对码，不复制浏览器Token。

本机验证4.1.60返回MCP_NOT_SUPPORTED，故连接器提供独立STDIO MCP桥接（`node dist/cli.js mcp --config config.json`），只开放六个具名动作，由随包脚本调用官方CLI/API。没有任意JS、Shell、路径或公网监听入口，不宣称该版本的原生MCP已经可用。

生成任务使用随包温湿度演示模板，只参数化板框，固定四个器件和初步走线。**完整原理图、电源布线及电气审核未完成；DRC违规必须保留，严禁直接用于投产。** 首次3D导出可能需要嘉立创模型库网络。源工程保持在专属任务目录，用户可继续在嘉立创中编辑。

每台设备串行执行。网络中断后不自动重做设计写入；租约无法确认时停止并保留目录，由用户在Nexus核对任务后新建导出或重试任务。Ctrl+C停止领取；不关闭用户既有工程，不自动清理源文件。上传暂不提供分片续传，100MiB内可整文件重试。

验证：`npm test`、`npm run type-check`；CLI分发：`npm run package:win`；含运行环境的桌面分发：`npm run package:desktop`。release目录不提交Git。软件需在已授权的隔离环境配对验证，代码测试不代表现有数据库已经迁移或生产已部署。

## 可复现的本地能力验证

`node scripts/probe-easyeda.mjs --config config.json` 只读诊断。显式增加 `--create-demo` 后，脚本通过连接器自身的STDIO MCP与阶段确认，创建50×35mm和60×40mm两份隔离模板工程，导出真实产物并记录摘要到专属工作目录 `evidence/mcp-probe.json`。只关闭本次新建的编辑器会话，不配对Nexus、不上传文件。模板须工程师评审，DRC违规原样保留。


## 配对与本地开关诊断

- `[ENGINEERING_DISABLED]`：后端工程接口不存在或未启用。开发前端需 `VITE_APP_ENGINEERING_ENABLED=true`；后端需 `nexus.ai.engineering.enabled=true`，启动后才装配工程接口。修改后端开关需重启对应本地进程。
- `[API_NOT_FOUND]` / `INVALID_RESPONSE`：地址返回网页而非工程API，检查代理前缀。新版会自动尝试本站点固定的prod-api/dev-api前缀。
- `[PAIRING_INVALID]`：配对码无效、已使用或已过期，请在网页重新生成并在5分钟内兑换。连接检查不兑换配对码。 如果刚生成的新码也立即失败，应核对后端是否包含2026-10-19配对Redis租户键修复；仅更新桌面应用不能修复后端存取键不一致。
- `doctor`或“检查EDA”只验证本机编辑器；配对阶段的服务端接口诊断会单独输出。

运行日志只显示受控错误码和中文解释，不回显响应正文、配对码、设备凭证或任意异常原文。
