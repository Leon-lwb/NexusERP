# system 模块审查结果

> 来源: Claude 代码审查代理（2026-08-01），审查范围: ruoyi-modules/ruoyi-system（190 文件）
> 总计: 6 个问题（CRITICAL 0, HIGH 2, MEDIUM 4）

## HIGH

### 1. SysRoleServiceImpl.java:499-519 — 角色授权接口可给任意用户授予内置超管角色（提权）
- **问题**: `insertAuthUsers`（及 selectAuthUserAll）只做 checkRoleDataScope(roleId)，**缺 checkRoleAllowed(roleId)**。对比用户侧 SysUserServiceImpl.insertUserRole 会显式剥离 SUPER_ADMIN_ID(roleId=1)，角色侧完全无保护。deleteAuthUser/deleteAuthUsers 同样未校验 roleId=1。
- **影响**: 任一持有 system:role:edit 的租户管理员可调用 `PUT /system/role/authUser/selectAll?roleId=1&userIds=[目标]` 把内置 admin 角色授予任意用户。被授予者获得 SUPER_ADMIN_ROLE_KEY 后可过 `@SaCheckRole(SUPER_ADMIN_ROLE_KEY)` 门禁（SysTenantController 全部接口、SysMenuController 增删改）→ 跨租户提权；反向可撤销其他管理员超管角色。
- **建议**: insertAuthUsers/deleteAuthUsers/deleteAuthUser 增加 checkRoleAllowed(roleId)，禁止非超管操作 roleId=1。

### 2. AutoCodeUtil.java:136-146 — 自动编码流水号并发生成重复，无锁跨实例/无唯一约束兜底
- **问题**: genSerialCode 仅靠 synchronized + @Transactional(REQUIRES_NEW)：① 集群部署下 synchronized 只对单 JVM 有效；② 单 JVM 下事务在方法返回后才提交，锁已释放但提交未发生，另一线程读到旧 lastSerialNo → 相同流水号。sys_auto_code_result 表无任何唯一索引（rule_id/gen_date/last_input_char 均无唯一约束），重复静默落库。
- **影响**: 高并发创建销售/采购/物料单据时两个请求拿到相同编号（如 SO20260801001 出现两次），按编号查询/对账错乱。
- **建议**: 用分布式锁（Lock4j/Redisson）串行取号，或将流水号更新改为数据库原子操作（UPDATE SET last_serial_no = last_serial_no + step WHERE rule_id=? AND gen_date=? 返回行数），并给 (tenant_id, rule_id, gen_date) 加唯一索引兜底。

## MEDIUM

### 3. AutoCodeUtil.java:184-208 / PartTypeSerialNoHandler.java:91-101 — 流水号游标行错位，混用不同传入字符产生重复编码
- **问题**: cycleY/cycleN 按周期维度查游标行计算 lastSerialNo+step，但 saveAutoCodeResult 的 else 分支按请求的 inputChar 重新查询——inputChar 为 null 时 list() 跳过 lastInputChar 条件，orderByDesc(genDate) 取最新行更新，与计算流水号时命中的行可能不同。
- **影响**: 同规则混用 null 与字符调用后游标被更新到错误行，旧行流水号不前进 → 回到旧字符取号返回已用编号，业务单据编号重复。
- **建议**: saveAutoCodeResult else 分支复用与 cycleY/cycleN 完全相同的查询条件定位同一行。

### 4. SysAutoCodeController.java:35-55 — 自动编码生成/预览接口缺少权限注解
- **问题**: getAutoCode/previewAutoCode 无 @SaCheckPermission/@SaCheckRole，仅靠全局登录检查。
- **影响**: 任意登录用户可对任意规则（含 ERP 单据规则）调用 GET /system/autocode/get/{ruleCode}，批量消耗流水号制造跳号/耗尽序列（DoS），可预测他人单据编号。
- **建议**: 加 @SaCheckPermission("system:autocode:get")，genSerialCode 接口对 ruleCode 白名单校验。

### 5. SysDeptServiceImpl.java:309-318 / SysDeptController.java:113 — 部门新增/修改时 parentId 缺失或无效触发空指针
- **问题**: SysDeptBo.parentId 无 @NotNull；insertDept 中 selectById(null) 后 info.getStatus() NPE；edit 中 parentId null 时 .equals NPE；updateDept 移动部门同理。
- **影响**: POST /system/dept 省略 parentId → 500 崩溃；对已删除部门建子部门同样 500。
- **建议**: BO 加 @NotNull，insertDept/updateDept 先判空抛业务异常。

### 6. SysTenantServiceImpl.java:391 — 同步租户套餐时角色列表为空触发 NegativeArraySizeException
- **问题**: new ArrayList<>(roles.size() - 1)，目标租户无角色时 size-1 == -1 → NegativeArraySizeException。
- **影响**: 超管 syncTenantPackage 同步时接口 500，事务回滚。
- **建议**: new ArrayList<>(Math.max(roles.size() - 1, 0)) 或空角色时跳过。

## 确认无问题的点
- 密码全部 BCrypt 且 SysUserVo.password @JsonIgnore 不泄露
- 用户/角色/部门删除均有关联清理与占用校验
- SQL 均走 MyBatis-Plus 参数化
- 字典/配置/用户缓存经 TenantSpringCacheManager 按租户加前缀，无跨租户串缓存
- 租户创建显式设置实体 tenantId 可被租户插件保留，无租户串写
