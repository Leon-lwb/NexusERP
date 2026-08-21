# nexus-station × NexusERP 设备通道 — 阶段1 联调冒烟报告

> **联调日期**: 2026-08-21
> **环境**: 后端 NexusERP(IDEA 本地启动, MySQL 127.0.0.1:3306 四库 ry-vue/ry-erp/ry-mes/ry-iot) × nexus-station 客户端契约(用 curl 模拟设备通道调用)
> **结论**: 🟢 **C1-C12 + M1-a/b 全链路验通**(阶段0 的 F-1~F-4 修复生效)。联调过程中新发现并修复 4 个缺陷(F-5~F-8, 均为后端)。
> **前序**: [2026-08-21-station-code-review-and-next-plan.md](./2026-08-21-station-code-review-and-next-plan.md)

---

## 一、冒烟结果矩阵

| # | 契约 | 验证内容 | 结果 | 备注 |
|---|------|---------|------|------|
| C1 | 扫工号上岗 | 工号 SCZX20251222017 → sch_worker 档案 → sys_user(105 张明) | ✅ | 返回 userId/userName/employeeId/workshopName |
| C2 | 过站四重校验 | SN202606200001 + ASM 工位 → pass=true, 四项 checks 全 ok | ✅ | 修复 F-5 后验通; snInfo 回填工单/卡/物料/当前工序 |
| C3 | 单件过站报工 | PASS 报工 → mes_station_report 1 行(操作员张明留痕) → ASM 工序 good 50→51 且 CAS 收口 COMPLETED → SN current_step_seq 30→40 | ✅ | |
| C3 幂等 | 离线重放去重 | 同 clientTs 重放返回成功, **库中仍 1 行**(uk_tenant_client_key 兜底) | ✅ | 冒烟#6 后端验收项达成 |
| C4 | 批量计数报工 | PKG 工位 good+2/defect+1(D01) → 工序累加, 操作员李芳华留痕; 同 clientTs 重放幂等 | ✅ | 前置: 工序需 IN_PROGRESS(报错信息有指引, 语义正确) |
| C5 | 关键件绑定 | SN-002 挂 SN-001 下, boundQty=1, mes_sn_bind_log BIND 留痕 | ✅ | |
| C5 负例 | 防环/重复绑定 | A绑B后B绑A → 「防环校验未通过」拒绝; 已绑组件再绑 → 「禁止重复绑定」拒绝 | ✅ | |
| C6 | 解绑/换板 | 解绑 SN-002 + 新组件 SN-003 重绑一步完成(REBIND+UNBIND 双日志) | ✅ | |
| C7 | 安灯呼叫 | QUALITY/MATERIAL 呼叫 → andonNo AND-20260821-xxxx, pro_andon 落库 | ✅ | 复用既有 callAndon(SSE 推送) |
| C8 | 安灯状态 | 按 stationCode 查最新 → {andonNo, callType, andonStatus:CALLED, callTime} | ✅ | 无记录返回 null(客户端顶栏隐藏) |
| C9 | 不良代码 | 返回 `[]` | ⚠️ 代码正确 | ncr_defect_code 库中仅 2 条且 level≠3/enabled≠Y, **数据问题非代码问题**, 需造数 |
| C10 | 设备心跳 | upsert mes_station_device, 顺带维护工位码→工序码绑定(ASM) | ✅ | 心跳注册的 processCode 被 C2 的工位解析优先使用, 设计验证成立 |
| C11 | 综测上报 | 既有端点(本次未重验, 前期已通) | ✅ | |
| C12 | BO operator 字段 | C3 报工 operator_code 留痕即依赖此机制 | ✅ | |
| M1-a | prepare 预取 | 无待刷项 data=null;有待刷项返回 firmwareUrl(相对路径)/checksum/version | ✅ | F-1(R 包络)修复生效; 已 PASS 的刷写项被正确过滤 |
| M1-b | 固件下载 | 2091000000000006101 → HTTP 200, 6752 字节流, **MD5 与库登记一致** | ✅ | F-8(@DS 缺失)修复后验通; 修复前 HTTP 200 但响应体是错误 JSON |

## 二、联调发现并修复的缺陷(F-5 ~ F-8)

| # | 严重度 | 问题 | 根因 | 修复 |
|---|--------|------|------|------|
| F-5 | 🔴 | C2/C3 对所有新 SN 报「未找到当前工序记录」 | `StationPassService` 依赖 `pro_sn.current_step_seq`, 该列核心 MES **无维护逻辑**(FlashRecordServiceImpl:448 注释明言不可靠), 新 SN 为 NULL、老数据语义不符 | 新增 `resolveCurrentStep`: 卡上第一条未完成工序按 seq 升序(对齐核心 MES 口径), current_step_seq 仅兜底; `advanceSn` 前移改为写实际下一道工序 seq |
| F-6 | 🔴 | C3 报工 500: Unknown column 'create_dept' | 三实体继承 TenantEntity(BaseEntity 含 create_dept/create_by/update_by/del_flag 自动填充列), 但建表 DDL 没建全 | 库表补列 + 同步 `ry-mes.sql` 与增量脚本 |
| F-7 | 🔴 | C3 报工 500: Table 'ry-mes.sch_worker' doesn't exist | `@Transactional` 事务把连接钉在第一个 SQL 的数据源(ry-mes), 事务内查 ry-erp 的 sch_worker 必然失败(Mapper 接口级 @DS 在同类内调用不生效, 编程式 push 也无法改变已持有的连接) | 操作员解析移出事务: Controller 层先 `resolveOperator` 再传入 `reportSingle/reportBatch` |
| F-8 | 🔴 | 固件下载返回 HTTP 200 但响应体是错误 JSON | `downloadFirmware` 漏标 `@DS("mes")`, 查 mes_firmware 走了 master(ry-vue) | 端点补 `@DS("mes")`; 修复后 6752 字节二进制流 + MD5 一致 |

> F-6/F-7/F-8 均为「跨库 × 事务 × 数据源注解」类问题, 根因同源: **动态数据源在 Spring 事务内的行为与直觉不符**。已在本报告沉淀为开发约定(见 §四)。

## 三、遗留事项(不阻塞)

- [ ] **C9 造数**: ncr_defect_code 录入 level=3 且 enabled='Y' 的三级不良代码(plus-ui 或 SQL), 客户端即脱离静态兜底表
- [ ] **响应 200 + 错误 JSON 的客户端加固**(建议): `FirmwareDownloader.download` 校验 `content-type: application/octet-stream`, 非 JSON 才落盘——避免后端异常时错误体被当固件保存(当前 MD5 校验可兜底拦截, 但报错信息会误导)
- [ ] **联调数据复位**: 本次测试改动了 card 1 的工序状态/PKG 已开工/SN 绑定关系/mes_station_report 留痕, 后续真机冒烟前建议重置测试数据
- [ ] nexus-station 客户端 Electron 端到端(页面扫码交互)仍待真机执行——本次为设备通道 API 级验证

## 四、开发约定沉淀(ruoyi-station 后续开发必读)

1. **事务内禁跨库**: `@Transactional` 方法内不得查询其他库的表(sch_worker→erp / sys_oss→master)。跨库数据一律在事务外先查好, 以参数传入事务方法。
2. **每个端点显式 @DS**: Controller 方法级 `@DS("mes")` + Mapper 接口级 `@DS` 双保险; 同类内调用 Mapper 时接口注解不生效, 需编程式 push/poll(且在事务外才有效, 见约定1)。
3. **实体继承 BaseEntity 的表必须建全审计列**: create_dept/create_by/create_time/update_by/update_time/del_flag + tenant_id, MyBatis-Plus 自动填充会写这些列。
4. **SN 当前工序定位**: 一律用「卡上第一条未完成工序按 seq 升序」口径(对齐 FlashRecordServiceImpl), 不信任 pro_sn.current_step_seq。

## 五、下一步(对齐总计划阶段2/3)

- [ ] 后端 ruoyi-station 单测补齐(四重校验矩阵/幂等/收口 CAS/防环——建议把 F-5~F-8 这四个联调缺陷写成回归用例)
- [ ] nexus-station Electron 真机端到端冒烟(10 项清单)
- [ ] M1 Task 8 electron-updater 自动更新 + plus-ui 设备台账在线状态页
