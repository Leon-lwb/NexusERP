# 自研报表 E2–E7 实施计划

> 日期：2026-10-14；分支：codex/report-module-plan；基线：bed171ae。
> 用户已批准E2至E7开发，并要求此前生成代码及本轮新增代码采用中文文档注释。按现有仓库路径组织，执行过程逐项更新；本机验证不等于生产与现场签收。

## 设计选择和边界

保留common-report业务契约、正常认证、租户/资源ACL/业务/字段权限以及固定执行器。E2只接受逻辑实体/字段/关系/参数，数据库账户必须只有SELECT权限，默认不复用ERP写账户。E3/E4引入schema2/engine2.0.0并保留1.0.0；精确十进制由服务器计算。E5使用自有组件定义和现有ECharts。E6采用独立私有S3命名空间及本机暂存、数据库硬配额、租约围栏和双节点验证。E7提供SnailJob触发适配，接收人分别执行固定版本，默认不开启真实通知。

## 分阶段任务与文件

| 阶段       | 状态                                 | 核心落点                                                                                                                                            | 完成条件                                                                                                                                 |
| ---------- | ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| C0中文注释 | 完成中文文档审计与编译               | common-report、ruoyi-report、ERP/MES/System报表适配器；plus-ui/report与报表测试                                                                     | Java类型/公开契约/方法中文Javadoc，前端JSDoc；转换已有英文注释；定向注释审计与编译检查                                                   |
| E2受控取数 | 实现与独立审查/回归完成              | common/report/catalog；erp/report/catalog；report/query/QueryModel、QueryPlanner、QueryDefinitionService、ReportDatasetResolver；views/report/query | 目录版本/逻辑ID/最多2关系40字段20筛选3维度；各实体独立范围及LEFT JOIN；发布查询/固定数据集版本；真实权限/SQL攻击语料/只读账户验证        |
| E3交叉分析 | 实现与独立审查/回归完成              | report/pivot/PivotDefinition、PivotPlanner、AggregationExecutor；version/V2ReportEngineExecutor；views/report/pivot                                 | 精确SUM/COUNT/MIN/MAX/AVG、小计/总计、空维度、TopN其他、200列/20000单元格；固定快照钻取及统一导出                                        |
| E4打印增强 | 实现与回归完成                       | report/asset、print、batch；domain/ReportDsl、engine/RenderPlan、PDF/XLSX渲染；components/report                                                    | 私有图片版本/像素/checksum/ACL；QR/Code128；分组/重复页脚/方向；批量固定单据及逐项权限；自动解码与坐标回归，现场扫码另验                 |
| E5仪表盘   | 实现与回归完成                       | report/dashboard/DashboardDefinition、WidgetRegistry、DashboardService；views/report/dashboard                                                      | 固定组件版本、精确服务器指标/变化率、受控图表投影、个人收藏/移动布局/刷新暂停/参数映射；隔离及组件交互回归                               |
| E6共享存储 | 实现与隔离双JVM/强杀/重启验证完成    | report/storage/S3ReportArtifactStore、ArtifactMaterialization、ReportQuotaService；mapper及私有存储配置                                             | 逻辑storeId、认证代理流式读写、checksum、私有暂存/孤立回收；全局租户硬配额、任务公平、快照索引和字体去重；双节点/网络/清理/旧attempt回归 |
| E7定时交付 | 实现与合成通知/执行恢复验证完成      | report/subscription/SubscriptionDefinition、SubscriptionScheduleJob、SubscriptionService、ReportDeliveryService；views/report/subscription          | cron/时区/夏令时/补跑、持久schedule/execution/delivery、接收人固定版本、幂等/重试/领取再鉴权；合成通知，不发真实通知                     |
| I0集成交付 | 验证/服务恢复完成，随本次Git提交交付 | SQL增量/主安装、迁移登记、指南、E2–E7验证报告                                                                                                       | 非零后端/前端回归、完整类型/构建、独立审查、实际API/UI、服务恢复和Git提交                                                                |

## 统一契约示例

查询客户端提交逻辑ID，物理标识只在代码目录内：

```json
{
    "formatVersion": 1,
    "catalogVersion": 1,
    "entity": "stock",
    "joins": ["stock.material"],
    "fields": [
        { "field": "stock.quantity", "alias": "quantity", "aggregate": null },
        {
            "field": "stock.materialCode",
            "alias": "materialCode",
            "aggregate": null
        }
    ],
    "filters": [
        {
            "field": "stock.materialCode",
            "operator": "EQ",
            "parameter": "materialCode"
        }
    ],
    "parameters": [
        { "name": "materialCode", "type": "STRING", "required": false }
    ],
    "groupBy": [],
    "sort": []
}
```

客户端不能提供SQL、表名、连接串或URL。发布绑定QueryModel/catalog/checksum；代码provider和查询provider都从固定datasetVersion解析。任何参与实体或隐藏筛选/排序字段撤权都使读取与交付失败。

## 逐项开发流程

- [x] C0：扫描此前三次报表提交涉及的现存源文件，整理英文注释及声明；转换中文文档注释并保留原义；类型、方法、参数/返回值按真实职责补充。
- [x] E2：先写双租户/部门、右表不可见、非法标识/操作、敏感筛选、聚合粒度与参数绑定回归；运行确认失败；实现固定目录和planner；接查询发布/数据集resolver/API/UI；验证版本与账户边界。
- [x] E3：先写稀疏/空值、AVG总计、TopN其他桶、单位/币种、容量和快照钻取回归；实现交叉聚合和v2执行；复用固定计划、表格和三格式导出并验证v1不变。
- [x] E4：先写图片解码/像素/租户/版本、条码字符/容量、分组分页/重复区域、批量撤权回归；实现私有资源、打印区域、服务端渲染及批次；验证重启与固定坐标。
- [x] E5：先写组件schema/权限/精确变化率/投影、组件隔离和可见性轮询回归；实现仪表盘发布/收藏/数据服务和原生界面；验参数联动与移动布局。
- [x] E6：先写跨节点读写、配额竞争、材化释放、网络失败、孤立游标/删除重试和字体引用回归；实现S3私有适配与硬配额；验证真实对象协议及双节点围栏。
- [x] E7：先写时区/夏令时/重复触发/接收人降权/固定发布/重试和通知幂等回归；实现持久订阅、SnailJob适配、领取及原生管理；联调只用合成接收人。
- [x] I0：核对增量结构和适用环境，现有库未获本轮写入授权，不执行迁移；Maven始终串行；每阶段获得非零测试证据后再集成；最后完整前端单测/类型/生产构建、审查、浏览器及服务恢复。

后端入口沿用：`mvn -pl ruoyi-modules/ruoyi-report,ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-mes -am test -DskipTests=false -Dprofiles.active= -Dtest=*Report*Test,*Query*Test,*Pivot*Test,*Dashboard*Test,*Subscription*Test,*Asset*Test,*SharedStore*Test,DocumentLayoutTest,PrivateArtifactStoreTest,QualityTraceDatasetProviderTest -Dsurefire.failIfNoSpecifiedTests=false`。核查Tests run非零，打包跳测不计测试证据。前端执行test:report、test:unit、type-check和build:prod，ESLint/Prettier仅针对修改文件。

2026-10-15历史阶段证据（最终统计见最新验证报告）：E2原10项及后续父字段范围/原维度别名/日期回归；E3聚合5项、真实固定运行1项、旧执行器兼容4项；E4/E5当前集成8项通过；前端86项旧回归与查询模型通过，完整type-check通过。最终验收统计以集成交付记录为准。

最终完成度及实际环境边界见[2026-10-15 E2–E7验证报告](2026-10-15-system-report-e2-e7-verification-report.md)。原生模块继续ruoyi-report；扩展SQL只在隔离H2运行，现有开发库/生产库未迁移，真实通知/现场打印另验。
