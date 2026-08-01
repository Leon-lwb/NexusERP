# common 公共库审查结果

> 来源: Claude 代码审查代理（2026-08-01），审查范围: ruoyi-common 各子模块（core/mybatis/redis/web/satoken/tenant/oss/json/translation/encrypt/security/mq/sse/websocket）
> 总计: 12 个问题（CRITICAL 0, HIGH 2, MEDIUM 10）

## HIGH

### 1. ruoyi-common-mq MessageCompensationTask.java:53 — 消息补偿任务永远无法反序列化消息体，补偿机制整体失效且无限空转
- **问题**: BaseMessage 是抽象类，用 `objectMapper.readValue(record.getMessageBody(), BaseMessage.class)` 反序列化——Jackson 无 @JsonTypeInfo 时对抽象类必然抛 InvalidDefinitionException，异常被 catch 后仅 log.error，retryCount 累加永远执行不到。即便反序列化成功，读成基类后 send() 也会丢失所有子类字段。
- **影响**: 任何停留在 SENDING 的消息每 60 秒被扫描一次，永远无法重投、无法标记 FAILED，无限错误日志循环；跨模块业务消息（收货、IQC 触发等）静默丢失。每次 send() 新建同 messageId 的 SENDING 记录，记录表成倍膨胀。
- **建议**: 存储消息体时保留类型信息（activateDefaultTyping / 写 @class），重投时按类型还原；补偿逻辑先 UPDATE SET status='RUNNING' 认领记录。

### 2. MybatisExceptionHandler.java:20 + RedisExceptionHandler.java:17 — 两个 @RestControllerAdvice 无 @Order，被 GlobalExceptionHandler @Order(1) 完全遮蔽
- **问题**: GlobalExceptionHandler @Order(1) 含 @ExceptionHandler(RuntimeException.class)，DuplicateKeyException/MyBatisSystemException/LockFailureException 均为其子类会被抢先捕获；两个 handler 默认 LOWEST 永不调用。
- **影响**: 1) 唯一索引冲突前端收到原始 SQL 报错（`Duplicate entry ...`，SQL 细节泄露）；2) Lock4j 锁失败返回原始信息而非 503；3) MyBatisSystemException 内嵌 NotLoginException 返回 401 的分支成为死代码。与 CLAUDE.md 已记录的 SaTokenExceptionHandler 处置不一致。
- **建议**: 给两个 handler 加 `@Order(Ordered.HIGHEST_PRECEDENCE + 1)`，核查仓库内其它无 @Order 的 @RestControllerAdvice。

## MEDIUM

### 3. TenantKeyPrefixHandler.java:45,76-79 — map/unmap 租户前缀判断不对称，租户 ID 互为字符串前缀时缓存键串租户
- **问题**: map() 用 startsWith(name, tenantId+"") 判断，unmap() 却按 (tenantId+":").length() 截断。租户 1 与 10/11/12 前缀关系时：map("12:xxx") 在租户 1 下因 startsWith("1") 不加前缀，与租户 12 键冲突；unmap 截出损坏键；通配扫描 pattern "1:*" 会匹配 "12:*" 误删。
- **影响**: 租户数 >9 后缓存键被错误识别、损坏或跨租户删除。
- **建议**: 判断统一 startsWith(name, tenantId + ":")，unmap 先校验再 substring。

### 4. PlusDataPermissionHandler.java:135 — buildDataFilter 对共享 LoginUser 对象写 roleId，并发请求互相污染
- **问题**: user.setRoleId(...) 直接改从 SaSession（Redis 跨请求共享）取出的 LoginUser，并发请求（多角色、不同 dataScope）读到对方刚写入的 roleId。
- **影响**: 并发下部门/自定义数据范围条件错乱，同一用户可能看到不应见的数据（越权）。
- **建议**: 改 SpEL 上下文局部变量（#roleId），或拷贝 LoginUser。

### 5. XssHttpServletRequestWrapper.java:97 — 对整段 JSON 调 cleanHtmlTag，字符串值中的 <...> 内容被整体删除并可能破坏 JSON
- **问题**: 正则 `(<[\s\S]*?>)` 在整段 JSON 上跨结构匹配，字符串值含 `<` 且其后有 `>` 时中间内容（含引号、逗号、其他字段）被一并删除。
- **影响**: 备注/描述/工艺说明等含 < > 文本被静默篡改或 JSON 解析 400；与 API 加密联动时数据不可恢复。
- **建议**: Jackson 流式解析后只清洗字符串节点，或白名单转义。

### 6. EncryptUtils.java:69,107,126 — AES/SM4 默认使用 ECB 模式
- **问题**: SecureUtil.aes / SmUtil.sm4 的 hutool 默认 ECB/PKCS5Padding 无 IV，固定 key 下相同明文产生相同密文。
- **影响**: 密文列可模式分析（相同值必相同密文，可关联/枚举用户）；敏感字段形同未随机化。
- **建议**: 显式 CBC/GCM + 随机 IV 版本化。

### 7. CryptoFilter.java:75 — 响应加密时对原始 response 调 reset()，清掉控制器设置的响应头/状态码
- **问题**: chain.doFilter 后 servletResponse.reset() 作用于原始响应，清掉 @ResponseStatus/setHeader 的状态码和响应头；已提交时抛 IllegalStateException。
- **影响**: @ApiEncrypt 非 200 状态或自定义响应头接口语义丢失，异常时响应失败。
- **建议**: 包装器记录并回放状态码/响应头，仅 !isCommitted() 时 reset。

### 8. WebSocketSessionHolder.java:28-31 + PlusWebSocketHandler.java:103-110 — 旧连接关闭回调会把同用户的新连接一并踢掉
- **问题**: addSession 先 removeSession(userId) 关旧会话；旧会话 afterConnectionClosed 再 removeSession(userId) 按用户移除并 close——此时关的是刚建立的新会话。
- **影响**: 用户重连/多端登录时旧连接关闭回调杀死新连接，反复掉线。
- **建议**: removeSession 比对 session 实例，或 afterConnectionClosed 里 remove(userId, session)。

### 9. CompensationServiceImpl.java:56-91 — 补偿执行无认领/加锁，多任务并发重复执行
- **问题**: executeCompensation 先 select 再执行再 updateById，无 FOR UPDATE/状态认领/分布式锁；CompensationTask 与 ReconciliationTask 可扫描同一批 PENDING。且无 @DS("master")+REQUIRES_NEW，在 @DS("erp") 事务内调用 insert 落业务库报"表不存在"。
- **影响**: 集群下同一补偿并发执行多次（非幂等重复扣款/入账）；retryCount 并发丢失更新。
- **建议**: UPDATE SET status='RUNNING' WHERE status='PENDING' 认领（1 行才执行），加 @DS("master")+REQUIRES_NEW。

### 10. ReliableMessageProducer.java:51 + RabbitMqAutoConfiguration.java:97-103 — Broker 发送确认 NACK 只打日志，消息仍标记 SENT 静默丢失
- **问题**: convertAndSend 后无条件置 SENT；confirmCallback 对 !ack 仅 log.error，不置回 SENDING/失败态；mandatory 退回也只打日志。
- **影响**: 路由失败/Broker 拒收时消息静默丢失且补偿按 SENDING 条件扫不到，无告警。
- **建议**: NACK/returns 回调置回 SENDING（限重试次数）或标记 FAILED 并告警。

### 11. BaseMessageConsumer.java:68-75 — 失败重试用 basicNack(requeue=true) 立即重投，毒消息无退避热循环
- **问题**: requeue=true 立即重投无退避；重试计数 Redis 计数器非原子化配对，并发重投下重复累加。
- **影响**: 毒消息以最大速率反复失败执行，占用连接线程拖慢队列。
- **建议**: 失败 Thread.sleep 退避或 TTL 延迟重试，计数用 Lua 原子增。

### 12. TenantHelper.java:194-215 — dynamic() 收尾的 clearDynamic() 会连带删除同用户此前设置的全局动态租户
- **问题**: clearDynamic() 在已登录时无条件 deleteObject(Redis) + SaHolder.getStorage().delete。若此前 setDynamic(tenantId, true) 设置了全局动态租户，任何一次 dynamic() 都会把它删除，后续请求回落登录租户。
- **影响**: 超管切换租户后，嵌套 dynamic() 悄悄抹掉切换状态，查询/写入回到原租户（数据串租户/写错库）。
- **建议**: setDynamic 用独立 key 区分临时/全局，clearDynamic 只清理本次临时值；或 dynamic() 进入时快照恢复。
