# SOP 参考文档生成与专用私有存储补齐工具

本工具将57个初始化SOP占位主档关联到**新增待审批参考版本**，不替换旧文件、不写签名/查阅台账、不自动发布。适用于2026-10-08经用户授权、核对的本机业务基线与远程SOP专用存储；不是通用生产迁移工具。

## 模块与依赖

- `content.py`：57个工序的原创中文评审模板及公开参考来源。
- `pdf_document.py`：A4、5页PDF生成，文字可读性、文档身份/工序正文和SHA-256/SM3校验。
- `storage.py`：严格限定远程sop桶写入、空桶创建、旧ruoyi策略只读、上传前有效权限检查、条件写入和匿名HEAD/GET拒绝检查。禁止删除、修改ACL/策略或跨目标重定向。
- `provision.py`：正常认证的应用API创建独立sop配置，status1非默认；不得调用changeStatus、直接写配置SQL/缓存或伪造用户。
- `batch.py`：只读审计、私有备份/清单、独立清单审批摘要、上传回读验证、同连接跨库事务、历史完整性及重跑检查。
- `test_*.py`：离线测试，不访问真实数据库或对象存储。

依赖Python3.10+、PyMuPDF、PyMySQL、requests。数据库凭据只从`NEXUS_SOP_DB_USER`/`NEXUS_SOP_DB_PASSWORD`进程环境读取；不要把密码写进参数、源码或日志。MinIO凭据执行时读取系统库配置，不导出。应用会话只保存在忽略目录，GET验证当前身份/租户和权限后，通过普通API操作。

## 已授权目标与安全边界

- 本机MySQL `127.0.0.1:3306`（或localhost），系统库`ry-vue`、MES库`ry-mes`。
- 远程MinIO **`159.75.14.226:9005`、桶`sop`、配置key`sop`、PRIVATE0、非默认status1**。空前缀/域名，HTTP与现有服务相同，region为us-east-1。
- 既有`ruoyi`桶、其策略/对象和默认`minio`配置保持不变；旧目标仅可只读审计，不允许PUT。
- 业务仅租户000000、SOP ID1–57、编码SOP20260914001–SOP20260914057、旧V1.0/CURRENT/file_oss_id0的确定基线。其他情况中止，不扩大范围。

曾因旧ruoyi桶实际允许匿名读取而中止第一轮写入。用户随后批准独立私有桶/配置，本工具不通过修改既有共享桶权限来绕过检查。

## 版本与追溯规则

保留原57条V1.0版本、工序绑定、查阅及电子签名、其余SOP/版本。新增V1.1/PENDING后，主档按现有addVersion语义变为DRAFT；原current_version/file_oss_id/审批字段保留。已查阅/签署的主档不改旧类型，SOP1强制保护；页面仅将历史PROCESS显示映射为字典PRODUCTION。其余主档按工序合法分类。

对象按内容哈希命名，先完成上传和HEAD/GET/匿名拒绝验证，再落数据库引用。数据库异常回滚；提交确认丢失则在新只读连接核对完整批次，不能假称回滚或盲目重写。已上传未关联的对象不擅自删除，按清单审计。

## 离线验证

在仓库根目录执行：

```powershell
python -B -m unittest discover -s script/tools/sop -p 'test_*.py' -v
python -B -m unittest discover -s script/tools/rebuild -p 'test_sop_types.py' -v
node --test plus-ui/scripts/test-sop-document-display.mjs
```

## 执行与信任锚

批次目录必须在仓库忽略的`.codex`中，不能是符号链接；包含仅限本机保管的业务快照和清单，不得提交或输出正文。旧批次为`.codex/sop-reference-20261008`，新专用存储批次为`.codex/sop-private-20261008`。目标不同，两者清单审批摘要不可混用。

```powershell
# 默认audit不写对象/数据库。generate不覆盖既有文件，当前已准备批次不能再次generate。
python -B script/tools/sop/batch.py --mode audit --repo-root '<仓库绝对路径>'
python -B script/tools/sop/batch.py --mode apply --repo-root '<仓库绝对路径>' --artifact-dir '<新私有批次绝对路径>' --approved-manifest-sha256 '<独立复核记录的摘要>'
python -B script/tools/sop/batch.py --mode verify --repo-root '<仓库绝对路径>' --artifact-dir '<新私有批次绝对路径>' --approved-manifest-sha256 '<独立复核记录的摘要>'
```

清单必须一次读取：对相同字节缓冲区核对独立摘要再解析，保留重复JSON键、大小和路径检查。逐PDF核验编码、名称、工序、版本、章节及工序内容；文件或元数据替换需重新独立复核。写事务使用REPEATABLE READ与当前/范围锁，按主档、版本、绑定、查阅、签名、其他主档、其他版本固定顺序保护旧数据。

完整重跑apply只核验已有批次，不重复上传/插入。部分版本、元数据缺失、文件/哈希变化、历史台账变化或版本已审批等情况停止。工具不提供绕过权限、历史完整性或生产审批的开关。

## 工艺边界

每份文档明确“示例/待审核，未经现场审批不得用于生产”。内容是原创评审模板，不是设备手册节录或法规符合性声明。设备型号、图纸、温度/压力/扭矩等设定值、阈值、人员授权和现场风险控制须由批准资料确定。系统存在PDF不等于生产部署、工艺审核或人工验收通过。

本批次文件通过sys_oss.service=sop绑定专用私有存储。**未修改通用文件上传入口或全局默认上传配置**；其他模块仍按原默认配置工作。
