# generator/online/pay/admin 模块审查结果

> 来源: Claude 代码审查代理（2026-08-01），审查范围: ruoyi-generator、ruoyi-online、ruoyi-pay、ruoyi-admin、ruoyi-report、ruoyi-flowable、ruoyi-demo、ruoyi-job
> 总计: 11 个问题（CRITICAL 5, HIGH 3, MEDIUM 3）

## CRITICAL

### 1. UnifiedGenController.java:355 — /tool/gen/design/createTable 与 importToGen 允许任意登录用户执行任意 SQL
- **问题**: createTable 直接把请求体 sql 原样交给 jdbcTemplate.execute；无 @SaCheckPermission 仅要求登录。generateCreateTableSql 拼接 engine/charset/数值型 defaultValue 无白名单校验，overwriteIfExists 还会 DROP TABLE；executeCreateTableSql 按 ";" 分句执行。
- **影响**: 任何已登录普通账号即可对 master 数据源执行 CREATE/DROP/ALTER/UPDATE/DELETE，数据完全失控。
- **建议**: 加 @SaCheckPermission（仅管理员），SQL 白名单校验（仅建表语句、拒绝多语句与 DROP），design JSON 字段名过 SAFE_IDENTIFIER。

### 2. TableDesignController.java:104 — /online/db/design/create-table 同款任意 SQL 执行
- **问题**: 与上条同根因（TableDesignServiceImpl→UnifiedGenServiceImpl.executeCreateTableSql），无权限注解、无校验，独立暴露面。
- **建议**: 与上条一并治理。

### 3. OnlineMapper.xml:7 — 动态数据 CRUD 全部缺少 tenant_id 条件，跨租户 IDOR
- **问题**: dynamicUpdate/deleteDynamicTable/selectDynamicTableById/selectSubTableByFk/deleteSubByForeignKey 的 WHERE 只含主键/外键，无 tenant_id；调用链 FormServiceImpl(627/586/494-512)、UnifiedGenServiceImpl(589/699/745) 同样。列表接口加了 tenant_id 但详情/更新/删除绕过。
- **影响**: 任意登录用户枚举自增主键即可读取/覆盖/删除其他租户业务数据。
- **建议**: 所有动态 SQL WHERE 强制拼接 tenant_id = ?。

### 4. AppAuthController.java:71 — /app/auth/smsCode 明文返回短信验证码，可接管任意账号
- **问题**: @SaIgnore 无登录、无 @RateLimiter、无环境开关，验证码写入 Redis 并明文返回调用方。SmsAuthStrategy 只比对 Redis 值，App 端 /app/auth/smsLogin 即可用该验证码登录。
- **影响**: 生产环境任何人可对任意手机号获取验证码并登录该账号——账户全量接管。
- **建议**: 加 profile 开关（仅 local/dev），限流；生产走真实短信下发，严禁返回明文。

### 5. WxPayServiceImpl.java:110 — 回调未校验 out_trade_no/金额与订单一致
- **问题**: notify 直接 setOrderStatus + setActualAmount(报文金额) + updatePayOrder，未校验 outTradeNo == orderNumber、金额与本地订单一致；/pay/{channel}/notify 免登录。
- **影响**: 攻击者用自己一笔真实小金额已支付订单的合法回调报文重放，把受害订单标记为已支付且金额被覆盖。
- **建议**: 校验 outTradeNo 与路径 orderNumber 一致、金额一致、trade_state SUCCESS，任一不符返回 "fail"。

### 6. AliPayServiceImpl.java:110 — 回调验签失败仍返回 "success"，且无幂等/无金额与状态校验
- **问题**: (1) verifyNotify false 时不走异常分支直接 return "success"——验签失败必须拒绝让渠道重试；(2) 无幂等：订单已"已支付"仍会再次 updatePayOrder + sendPayReceived，重复回调重复发 MQ；(3) 未校验 trade_status 非终态与 total_amount 一致性。
- **影响**: 合法支付因验签瞬时失败被吞掉且支付宝不重试（掉单不可恢复）；重复回调导致财务重复入账。
- **建议**: 验签失败/状态非终态/金额不符一律 return "fail"；成功处理前用状态机+唯一消费键幂等。

## HIGH

### 7. SQBServiceImpl.java:309 — 回调验签通过后不做任何业务处理，支付状态永不更新
- **问题**: notify 验签通过后仅 log 并 return "success"，不更新订单状态、不发收款 MQ；query() 也只记录日志。验签失败抛 ServiceException 未被 catch（只 catch IOException）→ 500。
- **影响**: 收钱吧渠道支付成功后订单永远"待支付"（掉单），返回 success 不重试无法自愈。
- **建议**: 验签通过后解析 client_sn/order_status/金额，更新订单状态（幂等/金额比对）；失败统一返回 "fail"。

### 8. PayController.java:146 — 退款无状态机校验，且 /pay 业务接口无权限注解
- **问题**: refund 只检查 payType，未校验订单状态；AliPay/WxPay refund 直接 updateStatus 覆盖为"已退款"无状态机过渡；支付宝侧未传 out_request_no 幂等参数；refund/query/url 无 @SaCheckPermission。
- **影响**: 任意登录用户对已知 orderNumber 可重复退款/对未支付订单退款。
- **建议**: 退款前校验 orderStatus==已支付 + 分布式锁 + 唯一退款单号幂等；refund/query/url 加权限注解。

### 9. JimuReportTokenService.java:147 — 对所有已登录用户注入积木报表 SQL 管理权限
- **问题**: getPermissions 无条件把 drag:analysis:sql、drag:datasource:saveOrUpate/delete/testConnection、drag:dataset:save 等追加给每个登录用户。
- **影响**: 任何普通员工可打开报表设计器在数据源上执行任意 SELECT（可达业务库全部数据）、创建/修改数据源。
- **建议**: 权限码挂 sys_menu 按角色下发，或仅管理员注入。

## MEDIUM

### 10. PayController.java:110 — 未知 channel 或订单不存在时回调/查询直接 500
- **问题**: channel 无效时 payServiceMap.get() 返回 null → NPE；订单不存在时抛 ServiceException → 500。
- **影响**: 回调接口返回 500/异常体，渠道侧重试失真，NPE 暴露堆栈。
- **建议**: 未注册 channel / 订单不存在返回 "fail"。

### 11. ProcessCompleteListener.java:74 — 流程事件监听器依赖请求线程登录上下文，非请求线程触发流程完成会 NPE
- **问题**: processCompleted 与 TaskCreateListener.onEvent 调用 getLoginUser() 后直接 .getUserId()；ProcessCompleteListener 默认 isFailOnException=true，异常会中止 PROCESS_COMPLETED 分发。
- **影响**: MQ/Job 线程驱动的流程走完时 NPE，通知丢失或流程完成失败回滚，流程状态与业务状态不一致。
- **建议**: getLoginUser() 判空后才发通知；ProcessCompleteListener 覆写 isFailOnException=false 或将通知异步化。

### 12. UnifiedGenController.java:504 — /tool/gen/online/tableData/{tableName} 固定传 null tableId，接口必然报错
- **问题**: listTableData 传 null tableId，实现 assertAllowedPopupDictTableByContext 抛"tableId不能为空"。
- **影响**: 该接口 100% 失败。
- **建议**: 实现仅表名白名单的分支，或删除入口。

## 模块结论
- **flowable**: 其余为上游代码，无本项目引入缺陷
- **demo/job**: 演示代码，无问题
- **admin**: AuthController 等为上游正确实现，唯一新增问题为 AppAuthController.smsCode
- **pay 其余**: MoneyUtil 换算正确，密钥无硬编码，PayOrder/PayInvoice 权限齐全
