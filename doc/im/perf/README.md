# P5-3 IM 性能压测基线

> **任务**: [P5-3 性能压测基线 + P95/P99 延迟告警](../plans/archive/2026-07-19-im-p5-development-plan.md#p5-3-性能压测基线--p95p99-延迟告警)
> **日期**: 2026-07-19
> **分支**: `feature/im-p5-stability`
> **依赖**: P5-1（推送线程池监控）、P5-2（慢 SQL + 缓存命中率监控）

---

## 一、压测脚本清单

| 类型 | 文件 | 接口 | 默认并发 | 默认轮次 | 总请求数 |
|---|---|---|---|---|---|
| JMeter | [jmeter/im-list-conversations.jmx](./jmeter/im-list-conversations.jmx) | `GET /im/chat/conversations` | 100 | 10 | 1000 |
| JMeter | [jmeter/im-send-group.jmx](./jmeter/im-send-group.jmx) | `POST /im/chat/group/send` | 20 | 5 | 100 |
| wrk | [wrk/im-sync-token.lua](./wrk/im-sync-token.lua) | `GET /im/app/messages/sync` | 100 | 30s | ~6000 |

## 二、基线指标（P5 目标）

| 接口 | 场景 | P95 目标 | P99 目标 | 备注 |
|---|---|---|---|---|
| `GET /im/chat/conversations` | 缓存命中 | < 30ms | < 50ms | ImConversationCache TTL=60s |
| `GET /im/chat/conversations` | 缓存未命中 | < 200ms | < 500ms | 6 步批量化（P2-1）+ DB 侧去重（P4-10） |
| `POST /im/chat/group/send` | 1000 人群 | < 500ms | < 1000ms | P4-1 批量 INSERT + 2 次 publishToUsersAfterCommit |
| `POST /im/chat/private/send` | 单聊 | < 100ms | < 200ms | — |
| `GET /im/app/messages/sync` | 离线 300 条 | < 150ms | < 300ms | App syncToken 增量同步 |
| `GET /im/app/messages/search` | 7 天内 LIKE | < 500ms | < 1000ms | LIKE 查询（无 ES 索引） |

## 三、执行说明

### 3.1 环境准备

1. **服务端**: 后端服务运行在 `localhost:8080`，配置 MySQL + Redis + RabbitMQ
2. **测试账号**: 准备一个已登录的账号，获取 `Authorization: Bearer <TOKEN>`
3. **测试群组**: 1000 人群（`groupId=1001`，需预先创建并加入 1000 成员）
4. **测试消息**: 群内已有最近 7 天的消息历史（用于 syncToken / search 压测）

### 3.2 JMeter 执行

```bash
# 安装 JMeter 5.6.x（https://jmeter.apache.org/）

# listConversations 压测（默认 100 并发 × 10 轮）
jmeter -n -t doc/im/perf/jmeter/im-list-conversations.jmx \
  -Jtoken=<YOUR_TOKEN> \
  -Jthreads=100 -Jloops=10 \
  -l doc/im/perf/results/list-conversations-$(date +%Y%m%d-%H%M%S).jtl \
  -e -o doc/im/perf/results/list-conversations-$(date +%Y%m%d-%H%M%S)/

# sendGroup 压测（默认 20 并发 × 5 轮，1000 人群）
jmeter -n -t doc/im/perf/jmeter/im-send-group.jmx \
  -Jtoken=<YOUR_TOKEN> -JgroupId=1001 \
  -Jthreads=20 -Jloops=5 \
  -l doc/im/perf/results/send-group-$(date +%Y%m%d-%H%M%S).jtl \
  -e -o doc/im/perf/results/send-group-$(date +%Y%m%d-%H%M%S)/
```

**参数说明**:
- `-Jtoken`: 必填，已登录账号的 Bearer Token
- `-Jthreads`: 并发线程数（默认 100/20）
- `-Jloops`: 每线程循环次数（默认 10/5）
- `-JgroupId`: 群 ID（仅 sendGroup，默认 1001）
- `-l`: 结果文件（.jtl）
- `-e -o`: 测试结束后生成 HTML 报告到指定目录

### 3.3 wrk 执行

```bash
# 安装 wrk（https://github.com/wg/wrk）

# syncToken 全量同步压测（4 线程 × 100 连接 × 30s）
wrk -t4 -c100 -d30s --timeout=30s \
  -s doc/im/perf/wrk/im-sync-token.lua \
  --header "Authorization: Bearer <YOUR_TOKEN>" \
  http://localhost:8080/im/app/messages/sync

# 增量同步（指定 sinceMessageId）
SINCE_MESSAGE_ID=12345 wrk -t4 -c100 -d30s --timeout=30s \
  -s doc/im/perf/wrk/im-sync-token.lua \
  --header "Authorization: Bearer <YOUR_TOKEN>" \
  http://localhost:8080/im/app/messages/sync
```

### 3.4 监控指标采集

压测过程中同步观察 `/actuator/prometheus` 端点，重点关注：

| 指标 | 含义 | 期望范围 |
|---|---|---|
| `http_server_requests_seconds{uri="/im/chat/conversations"}` | 接口延迟分布 | P95 < 30ms（命中）/ < 200ms（未命中） |
| `im_push_success_total` / `im_push_failure_total` | 推送成功/失败计数 | 失败率 < 1% |
| `im_push_executor_rejected_total` | 推送线程池拒绝计数 | 0（队列未满） |
| `im_push_latency_seconds{type="group"}` | 推送延迟分布 | P95 < 100ms |
| `im_cache_hit_total{type="conversation"}` / `im_cache_miss_total` | 缓存命中/未命中 | 命中率 > 80%（稳态） |
| `im_db_slow_total` | 慢 SQL 计数（> 200ms） | 增量 0 |
| `im_db_query_latency_seconds` | DB 查询延迟分布 | P95 < 100ms |

## 四、基线数据（待填充）

> 压测完成后将结果填入下表，作为 P5 之后的回归基线。

### 4.1 listConversations

| 日期 | 并发 | 总请求 | QPS | P50 | P95 | P99 | 错误率 | 缓存命中率 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| YYYY-MM-DD | 100 | 1000 | — | — | — | — | — | — | 首次基线 |

### 4.2 sendGroup（1000 人群）

| 日期 | 并发 | 总请求 | QPS | P50 | P95 | P99 | 错误率 | 推送成功率 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| YYYY-MM-DD | 20 | 100 | — | — | — | — | — | — | 首次基线 |

### 4.3 syncToken 增量同步

| 日期 | 并发 | 时长 | QPS | P50 | P95 | P99 | 错误数 | 备注 |
|---|---|---|---|---|---|---|---|---|
| YYYY-MM-DD | 100 | 30s | — | — | — | — | — | 首次基线 |

## 五、告警规则

Prometheus 告警规则定义在 [alerts/im-alerts.yml](./alerts/im-alerts.yml)，包含以下告警：

| 告警名 | 触发条件 | 严重级别 |
|---|---|---|
| `im_list_conversations_p95_warn` | P95 > 60ms 持续 2min | warn |
| `im_list_conversations_p95_crit` | P95 > 150ms 持续 2min | critical |
| `im_send_group_p95_warn` | P95 > 1s 持续 2min | warn |
| `im_send_private_p95_warn` | P95 > 200ms 持续 2min | warn |
| `im_cache_hit_rate_low` | 命中率 < 50% 持续 5min | warn |
| `im_slow_sql_spike` | 5min 增量 > 100 | warn |
| `im_db_query_p95_warn` | DB P95 > 500ms 持续 2min | warn |
| `im_push_failure_rate_high` | 失败率 > 10% 持续 3min | warn |
| `im_push_executor_rejected` | 拒绝计数 > 0 持续 1min | warn |

**部署方式**: 见 [alerts/im-alerts.yml](./alerts/im-alerts.yml) 文件头部注释。

## 六、回归测试

每次 IM 模块重大改造（P6+）后，需重新执行压测并对比基线：
1. P95/P99 退化 > 20% → 阻塞合并，需排查根因
2. P95/P99 退化 < 20% → 记录在新基线表的"备注"列
3. P95/P99 改善 → 更新基线表，作为新基线

## 七、已知限制

1. **JMeter 脚本未参数化多用户**: 当前所有线程共用同一 Token，无法模拟真实多用户并发场景。后续可扩展 CSV Data Set Config 注入多账号
2. **wrk 脚本未模拟 WS 长连接**: syncToken 是 HTTP 短连接，未覆盖 WS 推送延迟。WS 延迟需通过独立工具（如 Artillery）测试
3. **未包含消息搜索压测**: search 接口依赖 7 天数据量，需先填充测试数据
4. **基线数据待首次压测填充**: 表格中 "—" 待首次执行后填入实测值
