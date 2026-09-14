# T1/T2 工具集：物料生图 → 商城接入

配套报告：`doc/system/2026-09-14-物料图片生成与商城接入执行报告.md`

**解释器**：一律用 `D:\workspace\nexus-vision-service\.venv\Scripts\python.exe`
（Python 3.10，含 `cv2` / `pymysql`；系统 Python 与 hermes venv 都没有这两个包，也**没有 pip**）。

**前置**：商城按下面参数启动（缺一不可，详见报告 §1.2）：

```bash
cd D:\CRMEB\crmeb_java
java -jar ruoyi-admin\target\ruoyi-admin.jar \
     --server.port=20400 --snail-job.enabled=false \
     --spring.profiles.active=ci --api-decrypt.enabled=false
```

## 脚本

| 脚本 | 作用 |
|---|---|
| `build_manifest.py` | 从 `ry-erp` 读 37 个产成品 → 生成 `doc/assets/_t1_work/manifest.json`（含提示词） |
| `collect_images.py` | 按提示词里的**物料编码**从 `~/.dsh/dsh-imagegen/index.json` 回捞生成结果 → 去水印 → 落到 `doc/assets/product-images-2026-09-15/` |
| `wm_check.py` | 单车验证：打印水印区暗像素计数（RAW vs STRIPPED），`0px` 即干净 |
| `minio_selftest.py` | 用纯 stdlib SigV4 直连 MinIO 做 PUT/GET，验证凭据/端点/bucket |
| `minio_put.py` | 把 37 张图 SigV4 直传 MinIO bucket `shop`，object key 与商城约定一致 |
| `fix_oss_default.py` | 修正商城 OSS 默认通道（`qcloud` → `minio`）+ Redis 指针 |
| `mall_client.py` | 商城 admin API 客户端（Sa-Token `/auth/login` → `Authorization: Bearer`） |
| `t2_build_catalog.py` | 建 46 个分类 + 建 37 个商品 + 上架 + 下架 7 个存量品 + 修站点配置 |
| `fix_and_verify_store_info.py` | 回填商品简介，并回归验证 `store_info` 清空缺陷已修 |
| `verify_t2.py` | 全量验收，退出码 0/1（可接 CI） |

改端口 / 改库时的运维三件套：

| 脚本 | 作用 |
|---|---|
| `audit_config_urls.py` | 审计 `eb_system_config` 里内嵌本机端口（8080/8090/9090/20400）的行——**任何端口变更后先跑这个** |
| `apply_migration.py <sql>` | 执行 `crmeb_upgrade/*.sql` 并逐条打印影响行数；重放时行数应为 0（验证幂等） |
| `clear_mall_cache.py [--dry-run]` | 选择性清商城 Redis 业务缓存：**保留**登录令牌、`online_tokens`、`global:sys_oss*`、redisson 内部键 |

分类重构（顺序 / 名称 / 图标）：

| 脚本 | 作用 |
|---|---|
| `diag_category_order.py` | 诊断分类顺序：打印索引、后端谓词的原始顺序、`sort ASC` 对照与 EXPLAIN |
| `build_category_manifest.py` | 生成 `category_manifest.json`：短名 + 显示顺序（按 `sort DESC` 反推）+ 46 条图标提示词 |
| `collect_category_icons.py [--report-only]` | 按**精确 prompt** 回捞生成结果 → 去水印 → 缩到 200×200 → `doc/assets/category-icons-2026-09-14/`；`--report-only` 只报缺口 |
| `apply_category_rework.py [--dry-run]` | 图标传 MinIO + 更新 `eb_category` 的 name/sort/extra |
| `verify_categories.py` | 验收：顺序 / 名称 / `extra` 绝对化 / 子类顺序 / 46 张图可下载，退出码 0/1 |
| `emit_category_sql.py` | 由 `category_result.json` 生成商城侧 `crmeb_upgrade/08_category_rework.sql` |

执行顺序见报告 §6。

> ⚠️ **不要直接 `FLUSHDB`**。商城 DB3 里的 `global:sys_oss:default_config` 是承重的：
> `NexusFrontImagePathFilterConfig:85` 会走 `getCdnUrl()` → `OssFactory.instance()`，
> 该指针缺失时直接抛异常，**所有 C 端商品图会挂到下次重启为止**。
> 另外 DB3 里还有登录令牌，清了会把正在用的后台踢下线。

## 两个必须知道的坑

1. **`cv2.imwrite` 在 Windows 上遇到中文路径会静默失败**（返回 `False` 不抛异常）。
   必须 `cv2.imencode` + `Path.write_bytes()`。症状：脚本报「cleaned 37/37」但目录里只有 22 个文件。
2. **长连接读库会看到旧快照**。分类/商品是商城应用用另一条连接写的；脚本若在同一个
   REPEATABLE READ 事务里反复 `SELECT` 校验，会一直读不到新行并误判「创建失败」。
   读连接必须 `autocommit=True`。

## 接口要点（本仓库是 CRMEB on RuoYi-Vue-Plus，认证是 Sa-Token）

- 登录：`POST /auth/login`，`{"tenantId":"000000","username":"admin","password":"admin123",
  "clientId":"e5cd7e4891bf95d1d19206ce24a7b32e","grantType":"password"}` → `data.access_token`
- 之后带 `Authorization: Bearer <token>`（**不是**原版 CRMEB 的 `Authori-zation` 头）
- `POST /api/admin/category/save` 用**表单参数**、不返回 id
- `POST /api/admin/upload/image` 的 multipart 字段名是 **`multipart`**，不是 `file`
- `POST /api/admin/store/product/save` 用 JSON；**不返回 id**、强制 `isShow=false`、`content` 为 null 会 NPE
- `attr[].optionList[].optionName` 的 JSON 键名是 **`value`**（`@JsonProperty("value")`），
  传 `optionName` 会报「属性名不能为空」
- 单规格（`specType=false`）**不读** `eb_store_product_attr_result`，可规避该表的已知缺口
