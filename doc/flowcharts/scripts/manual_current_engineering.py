"""2026-10-17 Windows EDA连接器与私有工程产物操作。"""


def extend_engineering(doc, h1, h2, h3, para, bullet, numbered, table):
    h2(doc, '14.14 工程设备与本地连接器')
    para(doc, '工程功能按2026-10-17 plus/b154279b核对。入口为AI智能助手下的“工程设计 > 工程设备/工程任务”。'
              '网页、服务端nexus.ai.engineering.enabled和前端VITE_APP_ENGINEERING_ENABLED均需启用。'
              'Windows连接器在用户本机会话运行，服务器不安装或远程操控用户现有的EDA工程。')
    numbered(doc, '在已安装嘉立创EDA专业版的Windows电脑准备Node.js 24连接器，配置serverUrl、嘉立创exe绝对路径与专属workspaceRoot。先运行doctor检查实际CLI、模板与导出能力。')
    numbered(doc, '在网页“工程设备”生成五分钟内有效的一次性配对码，选择允许的受控动作；在连接器pair命令输入，不复制浏览器Token或共享设备凭据。')
    numbered(doc, '运行连接器run命令，确认网页显示本人设备在线、心跳正常和实际能力。更换服务地址需重新配对；解绑会撤销该设备凭据。')
    para(doc, '连接器凭据使用Windows CurrentUser DPAPI保存。MCP为随包受控STDIO桥接，只开放具名动作；'
              '已验证的嘉立创4.1.60原生MCP返回MCP_NOT_SUPPORTED，不能据此宣称EDA已提供可用原生MCP服务。')
    bullet(doc, '正常远程接入使用HTTPS。当前网站未申请证书时，可使用SSH本地端口转发，把本机127.0.0.1:18080转发到新服务器127.0.0.1:80；'
                '连接器配置serverUrl=http://127.0.0.1:18080/prod-api并显式development=true。隧道必须保持连接，不能把公网HTTP地址当作环回开发地址。')
    bullet(doc, '连接器目录：ruoyi-extend/nexus-engineering-connector。常用命令为node dist/cli.js doctor、pair、run、unpair，各加--config指向本机配置；'
                'SSH身份和配对码属于私密材料，不写入手册示例或截图。')

    h2(doc, '14.15 创建与跟踪工程任务')
    numbered(doc, '先执行连接检查任务，核对所选设备、CLI版本和诊断结果；不把设备显示在线当作全部导出能力已可用。')
    numbered(doc, '选择已发布的受控模板、设备与允许参数，创建草稿/导出任务；也可由AI对话中的嘉立创工程工具发起受控任务。需当前用户的创建和业务权限。')
    numbered(doc, '在“工程任务”列表查看阶段、开始时间和终态，打开详情读取连接结果或工程产物。返回按钮回到同一规范任务列表，旧列表地址兼容跳转。')
    table(doc, ['任务结果', '核对方式'], [
        ['SUCCEEDED', '核对已确认产物、摘要及实际诊断，不仅看状态文字。'],
        ['PARTIAL_SUCCESS', '部分产物已归档，但DRC或导出仍需处理；保留原始违规与错误。'],
        ['失败/取消/超时', '查看任务记录、租约和阶段，核对本地工程是否已有写入后再决定重试。'],
    ], widths=[4, 12])
    bullet(doc, '每设备串行执行。网络中断后不自动重做设计写入；租约无法确认时停止，保留任务目录。取消领取不等于撤销已执行的EDA修改。')

    h2(doc, '14.16 线路、三维与DRC复核')
    numbered(doc, '打开任务详情，先看结果摘要和已确认文件。连接任务显示检查结论，PCB任务按实际产物显示线路/二维、DRC、三维和文件页签。')
    numbered(doc, '二维预览查看PNG；三维预览使用已确认OBJ/MTL，缺失时显示明确提示。下载源工程或BOM等文件必须经过本人、租户、任务及下载权限检查。')
    numbered(doc, '查看DRC错误与警告，逐项在EDA修正后重新检查；图像可显示、文件可下载或三维可渲染，都不等于电气正确或可投产。')
    para(doc, 'temperature-humidity-demo v1.0.0为温湿度演示模板，仅参数化板框、固定四器件和初步走线；'
              '完整原理图、电源布线与工程审核未完成。本地历史验证发现8个DRC错误，必须保留PARTIAL_SUCCESS，不能伪造通过。')
    bullet(doc, '工程文件存放在专用私有桶，经Nexus授权后下载，不回落公共附件桶。大文件受单文件、单任务大小和文件数限制；首次三维导出可能需要模型库网络。')
    bullet(doc, '本手册说明已实现操作；服务器代码上线、设备实际配对、真实EDA导出与人工工程审核分别验收。没有在线设备时任务无法在服务器替代执行。')
