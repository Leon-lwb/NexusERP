# ai/iot/im 模块审查结果

> 来源: Claude 代码审查代理（2026-08-01），审查范围: ruoyi-ai、ruoyi-iot、ruoyi-im、ruoyi-common-ai
> 总计: 11 个问题（CRITICAL 1, HIGH 4, MEDIUM 6）

## CRITICAL

### 1. ExecuteSqlQueryTool.java:110-116 — 租户隔离条件可被注释/列名绕过，跨租户数据泄露
- **问题**: `if (sql.toUpperCase().contains("TENANT_ID")) return sql;` 跳过租户注入，该检查作用于**原始 SQL（含注释）**，而执行的第 5/7 步用的也是带注释的原始 SQL。构造 `SELECT * FROM wm_material_stock /* tenant_id */` 即完全跳过注入；`-- tenant_id` 结尾时 LIMIT 也会被注释吞掉。第二重绕过：injectTenantCondition 用 `upperSql.indexOf("WHERE")` 在原始 SQL 上定位，若 WHERE 出现在注释中（如 `-- WHERE x=1`），租户条件注入成死代码。ErpToolHelper.java:52 存在同样的早退模式。
- **影响**: 用户在 AI 对话中诱导模型生成带 `/* tenant_id */` 注释的 SQL 即可无租户过滤读取 ry-erp 库全部业务数据，多租户隔离形同虚设。
- **建议**: 租户跳过判断改用清洗注释后的 SQL；执行也统一用清洗后 SQL；注入用 jsqlparser 解析定位真正的 WHERE。

## HIGH

### 2. AppImController.java:314-418 — 好友请求处理无接收者归属校验（IDOR），可代他人接受好友请求
- **问题**: handleFriendRequest 的 messageId 路径与 notificationId 路径都未校验当前用户 == 接收方；通过后 addFriendRelation 双向写入好友关系。
- **影响**: 任意登录用户拿到好友请求 messageId/notificationId 即可代他人接受/拒绝请求并伪造好友关系。
- **建议**: 处理前强制校验 me.equals(toUserId)/me.equals(notification.getUserId())。

### 3. KnowledgeImportService.java:65-144 — URL 批量导入无 SSRF 防护
- **问题**: importFromUrls/importSingleUrl 对用户提交的任意 URL 发 HTTP GET，无 scheme/主机/IP 过滤、无内网/环回/链路本地拦截。同模块 WebSearchTool.crawlWebpage 已有完整防护，此处遗漏。
- **影响**: ai:knowledge:add 用户可访问 `http://169.254.169.254/`（云元数据拿临时凭证）、内网服务；30s 超时 + NORMAL 重定向可做内网端口探测。
- **建议**: 复用 WebSearchTool 的 sanitizeUrl（协议白名单 + 拒绝私网/环回/链路本地/组播），禁用或限制重定向。

### 4. AiServiceFacade.java:150-166 — 会话归属未校验，可向他人会话注入消息并窃取历史上下文
- **问题**: chat() 中 sessionId 非空时直接复用（跳过创建），随后以该 sessionId 插入 USER 消息并加载全部历史（getMessages 无归属过滤）。会话创建、历史查询都有校验，唯独发送入口没有。
- **影响**: 得知受害者 sessionId 后可注入自己的问题，AI 基于受害者私有业务上下文作答 → 私有上下文泄露；污染受害者会话。
- **建议**: sessionId 非空时 selectById 校验 session.getUserId().equals(userId)；getMessages 同样按归属过滤。

### 5. AppImController.java:743-751 — 群消息搜索无群成员校验，可跨群读取消息内容
- **问题**: searchMessages 的 GROUP 分支只 eq(groupId) 无 isGroupMember 校验；"全部"分支正确用 groupIds 限定；PC 端 groupHistory 有校验。
- **影响**: 任意登录用户得知 groupId 即可 LIKE 搜索并读取该群近 7 天全部消息。
- **建议**: GROUP 分支先做 isGroupMember 校验。

## MEDIUM

### 6. IotStreamTrimTask.java:41-52 — XTRIM 可删除未消费的 pending 消息，造成数据丢失
- **问题**: `stream.trim(StreamTrimArgs.maxLen(100000).noLimit())` 近似裁剪按到达顺序删头，不区分已 ACK 与 pending；注释声称"已 ACK 修剪不影响数据"但 XTRIM 不看 ACK 状态。
- **影响**: 消费端停摆或积压超 10 万条时未处理设备数据被静默删除。
- **建议**: 裁剪前检查 XLEN 与 XPENDING，pending 超阈值跳过；或 MINID 按时间裁剪预留滞后窗口。

### 7. IotPipelineProperties.java:31 + IotStreamConsumer.java:141-147 — 默认固定 consumerName，多实例部署重复消费
- **问题**: consumerName 默认 "iot-consumer-1"（注释说多实例要改）；双机部署未改配置则两实例同 consumerName 执行 XREADGROUP → 各自收到同一批消息各自落库。
- **影响**: iot_device_data 重复插入、数据双倍膨胀。
- **建议**: consumerName 默认拼接本机标识/随机后缀。

### 8. AiImageController.java:106-111 — 删除文生图记录无归属校验
- **问题**: remove 直接 deleteByIds 未校验记录属于当前用户（list 有 eq(userId)）。
- **影响**: ai:image:remove 用户可删除任意用户历史生成记录。
- **建议**: 删除前逐条校验 record.getUserId().equals(LoginHelper.getUserId())。

### 9. AiTokenUsageServiceImpl.java:127-141 — 历史回填 tenant_id 硬编码 0
- **问题**: backfillUsage 全部 setTenantId(0L)，查询在管理员当前租户上下文内执行；upsert 唯一索引含 tenant_id，真实数据与回填行并存。
- **影响**: token 用量统计回填归属错乱（集中租户 0），排行榜/趋势失真。
- **建议**: 按 ai_message 行实际 tenant_id 回填，或全租户扫描逐租户回填。

### 10. AppImController.java:887-922 — groupInfo/groupMembers 无群成员校验，泄露群信息与成员名单
- **问题**: groupInfo 与 groupMembers 不校验当前用户是否群成员；PC 端 listGroupMembers 有校验。
- **影响**: 任意登录用户可查任意群名称、群主、成员列表，配合群消息越权搜索形成完整攻击链。
- **建议**: 两接口统一加 isGroupMember 校验。

## 确认无问题的点
- AiRequestContext.set/clear 在异步路径均在 finally 清理，无租户上下文泄漏
- IoT MQTT 认证（常量时间比较、租户 Topic 授权、topic tenant 与设备交叉校验）与消息管道幂等/毒消息隔离设计正确
- IM 私聊/群聊历史、已读、删除、转发、撤回、通知归属校验基本到位（除 App 端三处）
- SQL 工具参数化、黑名单（含大小写、注释剥离）、表名白名单动态校验正确，问题集中在租户注入一处
