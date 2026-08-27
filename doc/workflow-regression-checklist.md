# 工作流模块回归测试清单

> 适用范围：Flowable 相关改动（安全修复 `55c69779`、`0492f0b7`、`8d45e3e2`，前端 `5cb5999e`，
> 以及后续阶段 1+ 稳定性/性能修复）合并后的手工回归。
> 每次发版前按此清单走一遍；发现问题先跑下方「自动化测试」确认是否已被单测覆盖。

## 一、自动化测试（CI/本地必跑）

| 测试 | 位置 | 命令 | 覆盖点 |
|------|------|------|--------|
| FindNextNodeUtilTest | ruoyi-common-flowable | `mvn test -pl ruoyi-common/ruoyi-common-flowable -am -DskipTests=false -Dtest=FindNextNodeUtilTest -Dsurefire.failIfNoSpecifiedTests=false` | 网关条件求值（FEL 双引号、空格表达式、未知变量失败安全）、**递归环防护（visited 集合，防 StackOverflowError）**、子流程/默认分支遍历 |
| ProcessAccessGuardTest | ruoyi-flowable | `mvn test -pl ruoyi-modules/ruoyi-flowable -DskipTests=false -Dtest=ProcessAccessGuardTest -Dsurefire.failIfNoSpecifiedTests=false` | 详情越权拦截：管理员/发起人/办理人/候选人/**候选组成员（ROLE{id}/DEPT{id}）**/被抄送人放行，无关人拒绝，租户过滤 |
| WfFormDdlGuardTest | ruoyi-flowable | `mvn test -pl ruoyi-modules/ruoyi-flowable -DskipTests=false -Dtest=WfFormDdlGuardTest -Dsurefire.failIfNoSpecifiedTests=false` | 动态建表 DDL 注入：标识符白名单、字面量转义（引号/反斜杠翻倍）、COMMENT/DEFAULT 拼接 |
| safeExpression 单测 | plus-ui | `npm run test:unit` | 前端四则运算表达式安全求值（防 eval 注入），28 断言 |

> 注意：两个后端模块的 pom 已各自覆盖根 pom surefire 的 `<groups>` 过滤，无需激活 profile。

## 二、流程全生命周期

- [ ] 发起 → 审批 → 办结：普通串行流程（两节点以上）全流程走通，办结后实例进入「已办」并可查看详情
- [ ] 驳回/退回：驳回至发起人后，发起人修改重新提交，流程继续推进
- [ ] 收回（撤回）：发起人在首节点未办理前可收回，收回后可再次发起
- [ ] 终止/挂起/激活：管理员终止运行中实例；挂起的流程定义不可发起新实例，激活后恢复
- [ ] 会签（多实例）串行 + 并发：全员办理完成后才进入下一节点；串行按顺序逐人办理
- [ ] 加签/减签：办理中任务加签后新办理人出现在待办；减签后任务办理人减少
- [ ] 转办/委托：转办后原办理人待办消失、新办理人待办出现；委托任务完成后回到委托人

## 三、权限与越权（安全回归重点）

- [ ] 候选组正反向：按 `ROLE{id}`/`DEPT{id}` 候选组分配的任务，组内用户**待办可见、详情可开、可办理**；组外用户打开详情被拒（提示「不是该流程的参与人」）
- [ ] 越权访问详情：未参与流程的普通用户直接以他人 procInsId 调用详情接口，返回业务异常而非 200
- [ ] 超级管理员/租户管理员：可查看任意流程详情
- [ ] 跨租户隔离：A 租户用户访问 B 租户流程实例 ID，行为与「实例不存在」一致
- [ ] 按钮权限：`workflow:instance:updateState`、`workflow:process:myCopyList` 两个新菜单权限只对授权角色可见
- [ ] 表单 DDL：新建表单时字段名填 `x;DROP TABLE sys_user`、反引号、中文等非法标识符被拒绝；合法字段名/带引号的标签正常建表

## 四、审批记录与抄送

- [ ] 流程详情审批记录：每个节点显示办理人昵称、办理时间、审批意见；**办理人已被删除/停用的历史节点不报 500**（昵称降级显示用户名或空）
- [ ] 候选组节点办理前记录显示「候选组名称」而非空白
- [ ] 抄送：办结/审批时选择抄送人，对方在「抄送我的」列表可见
- [ ] 抄送已读：打开抄送详情后状态变已读（乐观锁下并发打开不报错）
- [ ] 我的抄送列表：菜单权限 `workflow:process:myCopyList` 授权后入口可见

## 五、流程设计与部署（钉钉设计器）

- [ ] 模型新增/编辑/删除按钮权限：`workflow:model:add/edit/remove` 按钮权限正常（若菜单 SQL 未导入则按钮不可见）
- [ ] 钉钉设计器条件分支：条件含**中文变量/中文比较值**（如 `${状态 == "通过"}`）可保存、部署、流转正确
- [ ] 设计器条件含空格（`${count > 3}`）与裸表达式（`approved`）两种写法均能正确判断分支
- [ ] 流程图含环（非用户任务构成，如网关互指）时，办理页「下一步办理人」提示不卡死不 500
- [ ] 部署列表查询：按名称关键字过滤后**分页正确**（总数与页内条数一致，不因过滤后分页错乱）
- [ ] 发起流程列表：按分类/名称过滤后分页正确

## 六、表单渲染

- [ ] 自定义业务表单：详情页正常渲染 `wf_custom_form.route_name` 指向的组件（列表接口晚于详情返回时也不空白）
- [ ] 表单必填拦截：必填字段为空提交时被拦截并提示
- [ ] 数字/小数/文本类型字段：输入校验与显示正确（整数型拒绝小数输入）
- [ ] 审批中表单：可编辑字段按节点配置开放编辑，其余只读

## 七、IoT/设备联动（如启用）

- [ ] 设备告警触发的流程实例正常创建并可被绑定办理人处理
- [ ] SSE 推送：新待办到达时右上角通知实时出现

## 八、历史问题速查

| 症状 | 根因 | 修复位置 |
|------|------|----------|
| 办理页「下一步」StackOverflowError | 网关环递归无 visited 集合（曾修复但递归调用未传参，**由单测抓获**） | `FindNextNodeUtil.next()` 5 参重载 |
| 候选组用户被误拦「无权查看」 | `taskCandidateUser` 不解析用户所属组 | `ProcessAccessGuard` 增加 `taskCandidateGroupIn` |
| 历史节点办理人已删除 → 详情 500 | `selectUserByUserName(...).getNickName()` 链式 NPE | 阶段 1 修复项 |
| 列表过滤后分页总数错误 | 先分页后过滤 | 阶段 1 修复项 |
| 模型保存后元数据丢失 | `buildMetaInfo` 条件反转（`isNotEmpty` 应为 `isEmpty`） | 阶段 1 修复项 |
| 流程实际在跑但业务状态显示"已通过" | complete() 在办结前按节点预测判定结束，网关条件求值失败即误判 pass | 阶段 2：办结后按真实活动任务回写 |
| 发起人收回中途失败后，该流程定义所有新实例都被导回首节点 | recallProcess 突变共享 BpmnModel 缓存无 try/finally，异常路径不恢复出线 | 阶段 2：finally 恢复 + 多活动节点重定向 |
| 业务删除时引擎实例删除异常 → 整个外层事务 UnexpectedRollbackException | 同事务内 catch 无法对抗 rollback-only 标记 | 阶段 2：delete(3参) 改 REQUIRES_NEW 独立事务 |
