# Nexus-ERP

## 平台简介

Nexus-ERP 是一个基于 **RuoYi-Vue-Plus v5.6.0** 二次开发的企业级 ERP 管理系统。项目最初在 RuoYi-Vue 框架上二开，后全面升级至 RuoYi-Vue-Plus 分布式多租户架构。

- **作者**: 王欢
- **仓库地址**: [Gitee](https://gitee.com/helloworld_wh18/nexus-erp/tree/plus/)
- **许可证**: MIT

---

## 演示地址
http://nexuserp.website/index

账号：admin
密码：admin123

## 业务功能

### 基础数据管理 (BD)

物料主数据、物料清单 (BOM)、客户档案、供应商档案、车间定义、工位管理、计量单位、物料分类等企业基础数据的统一维护。

### 生产管理 (PRD)

涵盖生产计划排程、工单下达与跟踪、工序报工、物料需求运算等核心生产流程，实现从计划到执行的生产全过程管控。

### 销售管理 (SO)

客户订单管理、订单头管理，支持订单全生命周期跟踪，对接生产与仓储模块实现业务闭环。

### 排产调度 (SCH)

班次与班组管理、工人档案、考勤记录、假期管理、生产调度排程，提供精细化的人员与产能调度能力。

### 仓储管理 (WM)

仓库定义、库区/储位管理、库存查询与盘点、收货与发货管理、到货通知，实现仓储作业的标准化与可视化。

### 工作流引擎 (Flowable)

集成 Flowable 7.0 工作流引擎，支持复杂审批流程、转办、委派、加减签、会签、或签、票签等功能，可与各业务模块深度联动。

### 积木报表 (JimuReport)

内置积木报表引擎，支持在线设计报表模板、拖拽式报表配置、数据可视化展示，降低报表开发门槛。

### 支付管理 (Pay)

集成支付网关，支持多支付渠道接入，提供统一的支付请求、回调处理、退款管理、账单对账等功能。

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5 + Maven |
| 前端框架 | Vue 3 + TypeScript + Vite 7 + Element Plus + Pinia |
| 权限认证 | Sa-Token + JWT 无状态认证 |
| ORM | MyBatis-Plus 3.5.x (多租户、分页、数据权限、加密、脱敏) |
| 数据库 | MySQL (主) + Oracle / PostgreSQL / SQLServer (可选) |
| 缓存 | Redis (Redisson + Spring Cache) |
| 工作流 | Flowable 7.0 |
| 报表 | 积木报表 (JimuReport) |
| 任务调度 | SnailJob |
| 文件存储 | Minio (S3 协议) |
| 接口文档 | SpringDoc (基于 Javadoc) |

## 项目结构

```
nexus-erp/
├── ruoyi-admin/              # Spring Boot 入口 (DromaraApplication)
├── ruoyi-common/             # 共享库 (26 个子模块: 核心、Web、认证、ORM、Redis、多租户、工作流等)
├── ruoyi-modules/            # 业务模块
│   ├── ruoyi-system/         #   系统管理 (用户、角色、菜单、部门、租户、字典等)
│   ├── ruoyi-erp/            #   ERP 业务 (基础数据、生产、销售、排产、仓储)
│   ├── ruoyi-pay/            #   支付管理
│   ├── ruoyi-flowable/       #   Flowable 工作流 REST API
│   ├── ruoyi-generator/      #   代码生成器
│   ├── ruoyi-job/            #   分布式任务调度
│   └── ruoyi-demo/           #   框架使用示例
├── ruoyi-extend/             # 扩展服务 (监控中心、SnailJob 控制台)
├── plus-ui/                  # 前端项目 (Vue 3 + TS)
└── script/sql/               # 数据库初始化脚本
```

## 快速开始

### 环境要求

- JDK >= 17
- Maven >= 3.8
- Node >= 20.19.0
- MySQL >= 8.0
- Redis >= 6

### 后端启动

```bash
# 克隆项目
git clone https://gitee.com/helloworld_wh18/nexus-erp.git
cd nexus-erp

# 执行初始化 SQL (按顺序)
# 1. script/sql/ry_vue.sql
# 2. script/sql/ry-vue-erp.sql

# 启动后端
mvn spring-boot:run -P dev
```

### 前端启动

```bash
cd plus-ui
npm install
npm run dev
```

前端开发服务器默认监听 `http://localhost:80`，通过 `/dev-api` 代理到后端 `http://localhost:8080`。

### 后端打包
mvn clean package -D maven.test.skip=true -P prod

### 前端打包
npm run build:prod

## License

MIT License. 保留原始 RuoYi-Vue-Plus 开源协议文件即可。
