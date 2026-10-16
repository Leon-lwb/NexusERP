# Nexus 本地工程连接器

本工具是Windows用户会话内运行的Node.js 24 CLI。Nexus通过HTTPS分发任务，连接器使用本机受控MCP桥接调用嘉立创CLI，回传私有产物。首期面向集成验证，模板不具备生产发布资格。

```powershell
npm ci
npm run build
Copy-Item config.example.json config.json
node dist/cli.js doctor --config config.json
node dist/cli.js pair --config config.json
node dist/cli.js run --config config.json
node dist/cli.js unpair --config config.json
```

配置serverUrl为Nexus地址，executable为已安装的嘉立创专业版exe，workspaceRoot为专属运行目录。环回开发HTTP必须显式设置development=true。凭证用Windows CurrentUser DPAPI加密；修改服务地址需重新配对。只从Nexus网页生成一次性配对码，不复制浏览器Token。

本机验证4.1.60返回MCP_NOT_SUPPORTED，故连接器提供独立STDIO MCP桥接（`node dist/cli.js mcp --config config.json`），只开放六个具名动作，由随包脚本调用官方CLI/API。没有任意JS、Shell、路径或公网监听入口，不宣称该版本的原生MCP已经可用。

生成任务使用随包温湿度演示模板，只参数化板框，固定四个器件和初步走线。**完整原理图、电源布线及电气审核未完成；DRC违规必须保留，严禁直接用于投产。** 首次3D导出可能需要嘉立创模型库网络。源工程保持在专属任务目录，用户可继续在嘉立创中编辑。

每台设备串行执行。网络中断后不自动重做设计写入；租约无法确认时停止并保留目录，由用户在Nexus核对任务后新建导出或重试任务。Ctrl+C停止领取；不关闭用户既有工程，不自动清理源文件。上传暂不提供分片续传，100MiB内可整文件重试。

验证：`npm test`、`npm run type-check`；分发：`npm run package:win`。release目录不提交Git。软件需在已授权的隔离环境配对验证，代码测试不代表现有数据库已经迁移或生产已部署。

## 可复现的本地能力验证

`node scripts/probe-easyeda.mjs --config config.json` 只读诊断。显式增加 `--create-demo` 后，脚本通过连接器自身的STDIO MCP与阶段确认，创建50×35mm和60×40mm两份隔离模板工程，导出真实产物并记录摘要到专属工作目录 `evidence/mcp-probe.json`。只关闭本次新建的编辑器会话，不配对Nexus、不上传文件。模板须工程师评审，DRC违规原样保留。
