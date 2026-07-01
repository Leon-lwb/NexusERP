# AI 模块知识库文档

本目录存放供 RAG 检索和知识图谱构建使用的知识内容源文档。

## 目录结构

```
/doc/ai/
├── README.md
├── knowledge-base-01-erp-master-data/
│   ├── 01-物料主数据.txt
│   └── 01-物料主数据.annotated.txt
├── knowledge-base-02-erp-supplier/
│   ├── 02-供应商档案.txt
│   └── 02-供应商档案.annotated.txt
├── knowledge-base-03-erp-procurement/
│   ├── 03-采购订单流程.txt
│   └── 03-采购订单流程.annotated.txt
├── knowledge-base-04-erp-sales/
│   ├── 04-销售订单流程.txt
│   └── 04-销售订单流程.annotated.txt
├── knowledge-base-05-erp-inventory/
│   ├── 05-库存管理规则.txt
│   └── 05-库存管理规则.annotated.txt
├── knowledge-base-06-erp-production/
│   ├── 06-生产计划与工单.txt
│   └── 06-生产计划与工单.annotated.txt
├── knowledge-base-07-mes-workshop/
│   ├── 07-车间派工与报工.txt
│   └── 07-车间派工与报工.annotated.txt
├── knowledge-base-08-mes-quality/
│   ├── 08-质量检验规范.txt
│   └── 08-质量检验规范.annotated.txt
├── knowledge-base-09-system-deploy/
│   ├── 09-系统部署架构.txt
│   └── 09-系统部署架构.annotated.txt
├── knowledge-base-10-system-troubleshoot/
│   ├── 10-故障排查手册.txt
│   └── 10-故障排查手册.annotated.txt
└── knowledge-base-11-system-integration/
    ├── 11-系统集成接口.txt
    └── 11-系统集成接口.annotated.txt
```

## 使用方式

### RAG 知识库
1. 在系统前端创建知识库，名称对应各文件夹标题
2. 上传对应 `.txt` 原始文档
3. 系统自动完成解析、分段、向量化
4. 对话时携带 `knowledgeId` 参数即可触发 RAG 增强检索

### 知识图谱构建
1. 先上传原始 `.txt` 文档完成 RAG 入库
2. 在知识库详情页触发"构建知识图谱"
3. 系统逐 chunk 调用 LLM 抽取实体与关系
4. 完成后执行社区检测与摘要生成

### 标注版说明
`.annotated.txt` 文件使用行内标记语法，用于人工核验图谱抽取准确性：

```
[[ENTITY:实体类型|实体ID]]          定义实体
[[ATTR:属性名|属性值]]               标注属性（不影响抽取，仅人工核验用）
[[RELATION:关系类型|源ID|目标ID]]   标注关系
[[COMMUNITY:社区名|实体列表]]       段落后标注预期社区归属
```

实体类型仅使用系统内置值：物料、客户、供应商、订单、采购订单、工单、BOM、车间、工序、设备、仓库、产品、其他

关系类型仅使用系统内置值：供应、采购、包含、生产于、存储于、隶属于、关联

## 阅读顺序

1. ERP 业务流程类（01–06）：了解企业核心数据流与规则
2. MES 制造执行类（07–08）：了解车间层数据与质量标准
3. 系统运维类（09–11）：了解部署架构、故障排查与模块间集成

## 内容说明

每份文档均包含：
- **原始版**（无标注）：纯结构化文本，含表格、流程描述、规则条款，适合 Apache Tika 解析后直接进入 RAG 管线
- **标注版**（`.annotated`）：在原始版基础上叠加实体/关系标注，用于验证知识图谱抽取准确率

文档长度控制在 1500–3000 字，对应 3–8 个文本 chunk，适合观察图谱抽取质量与 RAG 检索效果。
