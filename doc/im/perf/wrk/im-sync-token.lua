-- P5-3: IM syncToken 增量同步 wrk 压测脚本
-- 目标 P95 < 150ms / P99 < 300ms（离线 300 条场景）
--
-- 使用方式：
--   wrk -t4 -c100 -d30s --timeout=30s -s im-sync-token.lua \
--       --header "Authorization: Bearer <TOKEN>" \
--       http://localhost:8080/im/app/messages/sync
--
-- 参数化（环境变量）：
--   SINCE_MESSAGE_ID=12345 wrk ...   -- 指定同步起点（不传则全量同步）

local since = os.getenv("SINCE_MESSAGE_ID")

-- setup 阶段：初始化（wrk 提供 wrk.method / wrk.path / wrk.body）
wrk.method = "GET"
wrk.headers["Accept"] = "application/json"

if since and since ~= "" then
    wrk.path = "/im/app/messages/sync?sinceMessageId=" .. since
else
    wrk.path = "/im/app/messages/sync"
end

-- 每次请求前的逻辑（可用于动态参数注入，此处保持简单恒定 URL）
request = function()
    return wrk.format()
end

-- 结束阶段：输出汇总
done = function(summary, latency, requests)
    io.write("\n--- P5-3 IM syncToken 压测结果 ---\n")
    io.write(string.format("总请求数:    %d\n", summary.requests))
    io.write(string.format("总耗时:      %.2fs\n", summary.duration / 1000000))
    io.write(string.format("QPS:         %.2f\n", summary.requests / (summary.duration / 1000000)))
    io.write(string.format("错误数:      %d (connect=%d, read=%d, write=%d, timeout=%d)\n",
        summary.errors.connect + summary.errors.read + summary.errors.write + summary.errors.timeout,
        summary.errors.connect, summary.errors.read, summary.errors.write, summary.errors.timeout))
    io.write(string.format("非 2xx 响应: %d\n", summary.responses[500] or 0))
    io.write("\n--- 延迟分布 (ms) ---\n")
    io.write(string.format("min:     %.2f\n", latency.min / 1000))
    io.write(string.format("avg:     %.2f\n", latency.mean / 1000))
    io.write(string.format("max:     %.2f\n", latency.max / 1000))
    io.write(string.format("stdev:   %.2f\n", latency.stdev / 1000))
    -- 分位线（latency:percentile 接收 0-100 浮点）
    io.write(string.format("P50:     %.2f\n", latency:percentile(50) / 1000))
    io.write(string.format("P90:     %.2f\n", latency:percentile(90) / 1000))
    io.write(string.format("P95:     %.2f  (目标 < 150ms)\n", latency:percentile(95) / 1000))
    io.write(string.format("P99:     %.2f  (目标 < 300ms)\n", latency:percentile(99) / 1000))
    io.write("-----------------------------------\n")
end
