# 前端公共组件/基础设施审查结果

> 来源: Claude 代码审查代理（2026-08-01），审查范围: plus-ui/src 的 components/utils/store/layout/router/根文件
> 总计: 9 个问题（CRITICAL 2, HIGH 2, MEDIUM 5）

## CRITICAL

### 1. utils/im/im-cache.ts:232 + store/modules/im.ts:216 — 登出后 IM 聊天记录完全不清除，换用户直接泄露上一用户私聊内容
- **问题**: IndexedDB 库 `nexus-im-cache` 的 key 不按用户/租户隔离；`imCache.clearAllCache()` 已定义但**从未被调用**。`im.ts` 的 `init()` 在 layout 挂载时执行 `loadFromLocalCache()`，把上一用户会话灌入内存；登出（user.ts:61）只清 token。
- **影响**: 共用浏览器上登出后，下一个登录者立即看到上一用户全部私聊记录、草稿；刷新后 IndexedDB 再次加载。隐私数据泄露。
- **建议**: `user.ts.logout()` 中调用 `imCache.clearAllCache()`，重置 im store，缓存 key 加 userId 维度。

### 2. components/ai/AiChatDrawer.vue:24 — AI 回复经 v-html 渲染未净化的 LLM 输出
- **问题**: `v-html="renderMarkdown(msg.content)"` 直接 `marked.parse(content)`，marked 透传原始 HTML 不净化。项目自写 `sanitizeHtml()` 只被 assistant/index.vue 使用，抽屉没接。
- **影响**: 数据库内容（客户名、备注）经 AI 查询回显后，`<img src=x onerror=...>` 在登录态页面内执行——存储型 XSS 窃取 token/数据。
- **建议**: renderMarkdown 返回前调用 sanitizeHtml，加 DOMPurify 兜底，javascript: 协议拦截改大小写不敏感。

## HIGH

### 3. permission.ts:31-49 + store/modules/user.ts:61-67 — 登出不清理动态路由，低权限用户可直达上一用户页面
- **问题**: 无任何 router.removeRoute/resetRouter；守卫 addRoute 只增不删；登出只清 token。Vue Router 4 addRoute 按 name 覆盖，上一用户独有路由残留；守卫只校验 getToken 存在。
- **影响**: 管理员登出后普通用户登录，直接输地址栏即可加载管理员页面壳；keep-alive 缓存可能命中旧组件实例。
- **建议**: 登出时遍历 addRoutes 执行 removeRoute，清空 tagsView/dict/notice store。

### 4. utils/request.ts:39-63 + store/modules/user.ts:61-67 — token 过期时"重新登录"流程死循环
- **问题**: toReLogin → logout() → 调 /auth/logout 接口（不在 excludes 白名单）→ 401 → 响应拦截器再次 toReLogin → 无限弹窗循环，router.replace('/login') 永远执行不到；logoutApi 抛错时 removeToken 不执行。
- **影响**: 会话过期后所有接口 401 → 无限弹"重新登录"，用户被困无法退出；网络错误时导航中断白屏。
- **建议**: 先本地清理（removeToken + 重置 store）再调接口，logoutApi().catch(()=>{}) 容错后无条件跳 /login。

## MEDIUM

### 5. permission.ts:41 — 路由守卫 generateRoutes 无异常捕获，菜单接口失败导致导航挂起
- **问题**: generateRoutes（含 getRouters 网络请求）无 try/catch，isRelogin.show 失败也不复位。
- **影响**: 后端故障时用户卡空白页，NProgress 一直转。
- **建议**: getInfo + generateRoutes 整体 try/catch，失败引导至登录页。

### 6. utils/sse.ts:10 + utils/websocket.ts:21 — JWT token 明文拼在 SSE/WebSocket URL 查询串中
- **问题**: token 作为 query 参数随连接发出。
- **影响**: token 进 nginx/网关访问日志、代理转发记录，泄漏后冒用会话。
- **建议**: 改 header/子协议携带，SSE 用短期专用票据。

### 7. components/FileUpload/index.vue:155-157 — 上传网络失败时全局 loading 遮罩永不关闭
- **问题**: handleUploadError 不 number.value-- 也不 closeLoading。
- **影响**: 上传失败后全屏 loading 永久显示，页面无法操作。
- **建议**: 错误分支补 number-- + closeLoading + 清失败项。

### 8. components/ai/AiChatDrawer.vue:112-197, 205-222 — AI 流式消息无会话关联（跨会话串扰）+ ECharts/ResizeObserver 泄漏
- **问题**: onAiSseMessage 只按 detail.type 追加，payload 无 sessionId；newChat 不清 SSE 流；echarts.init 与 ResizeObserver 从不 dispose。
- **影响**: 上一会话迟到的消息拼进新会话；长时间对话内存增长。
- **建议**: 事件带会话 ID 过滤，newChat 时 Abort 流，onUnmounted dispose 图表与 RO。

### 9. store/modules/user.ts:61-67 — 登出不重置用户信息与业务 store
- **问题**: logout 只清 token/roles/permissions，name/avatar/userId/tenantId 及 tagsView/notice/dict 全保留。
- **影响**: 换用户后界面显示旧用户信息，字典缓存跨租户复用。
- **建议**: logout 时 $reset 用户 store + delAllViews + clearNotice + cleanDict。
