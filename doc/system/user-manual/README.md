# NexusERP 用户操作手册

> 文档版本：V4.0；核对日期：2026-10-12；代码基线：`plus` / `fe59e4ae`。
> 本手册描述当前仓库代码中的操作与边界，不代表生产环境配置完成或人工端到端验收。

- [用户操作手册（Word）](./NexusERP_用户操作手册.docx)
- [当前流程图集](../../flowcharts/README.md)
- [逐图来源与使用条件](../../flowcharts/来源与使用边界.md)
- [本次更新与验证记录](./2026-10-12-流程图与手册更新记录.md)

## 内容组织

保留原有二十章的业务操作结构，逐模块修正状态、菜单与人工/自动联动描述。
新增第21章工位通道与生产履历、第22章电子签章、第23章仓储管理。
第14章补充工业决策、独立 Flow 编排、批量问题与配置；第13章补充工业缺陷检测及人工复核边界。
流程图按关联章节单独排图页，图后列明使用条件，附录C提供全图索引。

## 可重复维护

在仓库根目录执行：

```powershell
python -X utf8 doc/flowcharts/scripts/gen_current_flowcharts.py --check
python -X utf8 doc/flowcharts/scripts/gen_current_flowcharts.py
python -X utf8 doc/flowcharts/scripts/gen_manual_v4.py
python -X utf8 doc/flowcharts/scripts/gen_manual_html.py --output "$env:TEMP/NexusERP-manual-web"
python -X utf8 -m unittest discover -s doc/flowcharts/scripts -p "test_*.py" -v
```

依赖 Python 3.10+、Pillow、python-docx 和中文字体。`NEXUS_DOC_FONT` 可覆盖自动查找的字体。
完整回归还包含Windows COM保护测试，需pywin32；非Windows环境可单独运行`test_current_docs.py`做图集和Word结构检查。
章节内容在 `../../flowcharts/scripts/manual_ch*.py` 和 `manual_current*.py`，图定义在 `current_*.json`。
生成脚本检查每张图是否放入真实章节；不要只修改 Word 或 PNG 后声称已同步所有源文件。
网页生成器输出独立的 `index.html`、`index.html.gz`、`manifest.json` 和 `assets/`；检查后将整个输出目录同步到 `plus-ui/public/manual/`，生产环境只需原子替换 `/manual/`，不需要重新打包主前端。
当前交付文件的目录和页码已更新；以后重新生成时需再次使用Word/WPS更新域。
本机实际渲染证据见更新记录，不将结构校验当作纸面排版验收。
当前正式交付保留最后实际核验的174页版，第24页为仅页眉页脚的空白页。
生成器已修正章节分页；后续安全工具在本机WPS无法确认只读实例归属，故没有以未重新渲染的优化候选覆盖交付版，详见更新记录。

## 本机排版核验

Windows下可用`verify_document_layout.py`，需另外安装pywin32、PyMuPDF，并有支持独立会话的Word/WPS。
工具只打开临时副本，无法确认应用实例归属时拒绝继续；`--update-copy`仅保存副本，不自动替换源手册。

```powershell
$manual = (Resolve-Path 'doc/system/user-manual/NexusERP_用户操作手册.docx').Path
$artifacts = Join-Path $env:TEMP 'NexusERP-document-layout'
python -X utf8 doc/flowcharts/scripts/verify_document_layout.py --input $manual --output-dir $artifacts --update-copy
```

检查报告记录PDF页数、图片出现次数、文字/图片边界与抽查页面；最终交付前仍应查看实际页面。

`manual-outline.md`、`manual-outline-erp.md` 和旧 V2/V3 生成器是历史写作资料，不能作为当前状态或生产验收依据。
本次没有执行数据库迁移、写入业务数据、上传真实印章/合同或调用生产模型服务。
