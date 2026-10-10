# Milvus 集合备份与恢复

导出日期：2026-10-10（Asia/Shanghai）。来源是本地 Docker Milvus `2.5.27`，数据库为 `default`。

这是 Milvus **逻辑数据备份**，以 JSON Lines + gzip 保存完整向量和实体数据，通过 Python 脚本重建集合及索引。请使用下面的脚本恢复。

| 集合 | 记录数 | 向量维度 | 索引 |
| --- | ---: | ---: | --- |
| `AiKnowledge_2065090090526457857` | 7 | 1024 | FLAT / COSINE |
| `AiKnowledge_2108204662140141569` | 6 | 1024 | FLAT / COSINE |
| 合计 | 13 | | |

备份保留每条记录的 `id`、`text`、`metadata`、`vector`，以及集合 schema、字段长度、分区、主键规则、分片数、原有一致性级别和索引参数。原始主键和向量数值保持不变，恢复不需要重新调用 Embedding 接口。

## 文件

- `manifest.json`：集合结构、索引参数、分区清单、记录数、SHA-256 和导出时间。
- `data/default/<集合名>/_default.jsonl.gz`：UTF-8 JSON Lines，包含全部实体字段。
- `restore.py`：先检查备份完整性，再在目标实例创建集合、插入数据、重建索引、加载集合，并回读逐条验证。
- `requirements.txt`：固定 `pymilvus==2.5.16`，本次使用该客户端对 Milvus 2.5.27 导出及试恢复。
- `verification.json`：本次隔离试恢复的验证结果。
- `SHA256SUMS`：整个目录内文件的校验值。

同目录旁提供 `milvus-backup-20261010.zip` 和 `.zip.sha256`；将 ZIP 发给其他人，解压后保留完整目录结构。

## 先校验备份

需要 Python 3.10 或更新版本。单独校验文件不需要安装 PyMilvus，也不连接数据库。

```powershell
cd E:\Nexus\nexus-erp\script\sql\milvus-backup-20261010
python restore.py --verify-only
```

应显示两个集合分别为 7、6 条记录。备份文件 SHA-256、内容校验、主键重复、向量维度或记录数不一致时，程序返回非零退出码。

## 恢复到目标 Milvus

先准备目标 Milvus；本次已在独立的 Milvus 2.5.27 临时实例上验证恢复。项目的本地部署模板位于 `script/docker/milvus/`。

```powershell
cd E:\Nexus\nexus-erp\script\sql\milvus-backup-20261010
python -m pip install -r requirements.txt
python restore.py --uri http://localhost:19530
```

将 `--uri` 改为接收方的 Milvus 地址。默认恢复到 `default` 数据库；可指定其他数据库：

```powershell
python restore.py --uri http://localhost:19530 --database nexus_vectors
```

不存在的目标数据库会被创建。启用认证时，通过环境变量 `MILVUS_TOKEN` 传入接收方自己的凭证。连接地址也可通过 `MILVUS_URI` 提供。

脚本在任何创建和插入前检查全部目标集合及别名，发现同名集合即退出，不覆盖已有集合。操作中断时，已创建的集合会保留；再次执行前请由目标数据库管理员检查，或使用新的目标数据库。

成功时应看到：

```text
Verified default.AiKnowledge_2065090090526457857: 7 rows
Verified default.AiKnowledge_2108204662140141569: 6 rows
Restored and verified default.AiKnowledge_2065090090526457857: 7 rows
Restored and verified default.AiKnowledge_2108204662140141569: 6 rows
```

在 Nexus-ERP 中使用时，还需恢复系统库中的知识库、文档及分块记录，保持知识库 ID 和 Embedding 模型配置一致。Milvus 的集合名包含知识库 ID，仅恢复本备份不会创建这些关系型数据库记录。

## 验证范围

每个集合使用 Strong 读取导出，导出前后记录数一致；备份是各集合的逻辑快照，不提供整个数据库跨集合的原子快照。详细试恢复结果见 `verification.json`。备份包含导出时可查询的记录、集合结构及索引参数，不包含 Milvus 的用户权限和服务部署配置。
