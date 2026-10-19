# Nexus 工程连接器 v0.1.0 · Windows x64 桌面版

发布日期：2026-10-19；发行标签：`engineering-connector-v0.1.0`。

这是Nexus工程连接器的Windows免安装桌面发行包，用于把Nexus中的工程任务连接到本机嘉立创EDA专业版。完整解压后双击 `NexusEngineeringConnector.exe`，无需安装Node.js或npm。

## 下载文件

| 附件 | 用途 |
| --- | --- |
| `nexus-engineering-connector-0.1.0-win-x64-desktop.zip` | 完整Windows x64桌面应用，含EXE、Node.js 24.21.0、运行依赖、模板及两份说明书 |
| `nexus-engineering-connector-0.1.0-win-x64-desktop.zip.sha256` | ZIP的SHA-256校验文件 |
| `使用说明.md` | 完整操作、管理员准备、故障排查、升级与卸载 |
| `快速开始.md` | 首次配置和配对的简明步骤 |

ZIP为 **41,356,567 字节**、**3,776 个文件**，包含逐文件 `distribution-manifest.json`。SHA-256：

```text
424b61d38e9de9e8df9941383d8d6350c764bbbe0e97c74530d23b88169527a3
```

## 首次运行

1. 使用Windows 10 22H2或Windows 11 x64，准备.NET Framework 4.8，另行安装嘉立创EDA专业版。
2. 下载ZIP和校验文件，用PowerShell的 `Get-FileHash -Algorithm SHA256` 核对摘要，完整解压到固定目录。不要只复制EXE，也不要在压缩包预览中直接运行。
3. 双击EXE，填写Nexus地址并选择已安装的EDA专业版EXE，保存设置。支持HTTP和HTTPS，地址可用内网IP、域名或IPv6。
4. 点击“检查EDA”；它只检查本机编辑器，服务端状态仍以网页与接口诊断为准。
5. 登录同一Nexus环境的工程设备页面，生成五分钟一次性配对码，粘贴到应用并点击“配对设备”，成功后点击“启动连接器”。
6. 在网页确认设备在线，在AI对话中选择该工程设备，再发起连接检查或PCB草稿任务。

服务地址示例：`http://nexuserp.website` 或 `http://nexuserp.website/prod-api`；本机前端代理可用 `http://localhost:80/dev-api`，直连后端可用 `http://localhost:8080`。网页菜单路径与API地址不同。连接器会用不带凭据的GET识别同站点直连、`/prod-api`、`/dev-api`前缀，不携带身份跟随重定向。

完整操作说明见附件，或[仓库中的桌面使用说明](https://gitee.com/NexusERP/nexus-erp/blob/engineering-connector-v0.1.0/ruoyi-extend/nexus-engineering-connector/desktop/使用说明.md)。

## 本版功能与修复

- 提供Windows原生设置窗口、EDA诊断、配对、启动/停止、解绑、工作目录入口和系统托盘。后续双击会自动启动已绑定设备；关闭窗口收至托盘，完全退出需选择“退出应用”。
- 直接支持HTTP/HTTPS并自动识别工程API代理前缀，服务端错误以受控中文诊断码显示，便于区分地址、开关、配对、权限和协议问题。
- 身份采用Windows CurrentUser DPAPI加密，配置与工程保存在 `%LOCALAPPDATA%\Nexus\EngineeringConnector`；一次性配对码不落盘。停止只管理本应用的Node进程，保留源工程。
- 修复后端配对码生成与匿名兑换时Redis租户键不一致的问题。配对仍固定所有者、五分钟有效且单次消费，上传票据继续保持租户隔离。
- 同步开发前端与后端dev/local工程开关；生产仍须显式启用并配置专用私有OSS、用户权限和内置工程工具。

**服务端更新要求**：处理配对的全部后端实例都须更新到本标签代码并完成重启，再重新生成配对码。新旧版本混流仍可能出现新码立即失效；仅更换桌面包不能修复旧后端。配对键修复无需SQL迁移。

## 数据库备份与源码

按仓库所有者确认，系统、ERP、MES主SQL保留现有本地测试数据并原样提交；另提供四库2026-10-19完整压缩快照、SHA-256与manifest。请查阅[SQL入口](https://gitee.com/NexusERP/nexus-erp/blob/engineering-connector-v0.1.0/script/sql/README.md)及[日期快照说明](https://gitee.com/NexusERP/nexus-erp/tree/engineering-connector-v0.1.0/script/sql/snapshots/2026-10-19)。这些备份用于隔离新库恢复，不能当作存量库增量迁移；安装Windows连接器不需要导入SQL。本次发布不执行数据库写入或生产部署。

## 验证与适用范围

- 连接器完整回归76例通过，0失败/跳过；类型检查通过。
- 增加完整说明书入包断言，先确认缺失时失败，再修复并完成桌面包专项测试；原生EXE自测和逐文件摘要核验通过。
- 后端工程模块18类65例通过，包含真实租户键映射、匿名配对与上传隔离回归。
- 前端工程回归81例通过；此前本轮功能的前端类型检查、隔离生产构建及后端生产打包已通过。SQL完成静态结构和压缩解压核验，没有执行真实恢复。
- 用户已确认本地配对测试通过。该结果不替代其他Windows机器的运行验证或服务器部署验收。

当前包未数字签名；出现SmartScreen提示时先核对发布来源、摘要及单位软件策略。模板用于工程草稿与集成验证，DRC错误须由工程师审核，不作为可直接制造的生产设计。ZIP不包含EDA、后端服务、用户配置、配对身份或真实工程目录。
