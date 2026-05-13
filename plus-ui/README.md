## 平台简介

Nexus-ERP 前端项目，基于 [RuoYi-Vue-Plus](https://github.com/dromara/RuoYi-Vue-Plus) 前端框架二次开发，为 Nexus-ERP 企业管理系统提供用户界面。

- **作者**: 王欢
- **技术栈**: [Vue 3](https://cn.vuejs.org) + [TypeScript](https://www.typescriptlang.org/) + [Element Plus](https://element-plus.org/zh-CN) + [Vite](https://cn.vitejs.dev)
- **状态管理**: [Pinia](https://pinia.vuejs.org/zh/)
- **CSS 方案**: [UnoCSS](https://unocss.dev/)
- **许可证**: MIT

## 配套后端

| 项目名 | 仓库地址 |
|--------|----------|
| Nexus-ERP 后端 | [Gitee](https://gitee.com/helloworld_wh18/nexus-erp/tree/plus/) |

## 业务模块

| 模块 | 说明 |
|------|------|
| 基础数据 (BD) | 物料主数据、BOM、客户/供应商档案、车间/工位、计量单位 |
| 生产管理 (PRD) | 生产计划、工单、报工、物料需求 |
| 销售管理 (SO) | 客户订单管理 |
| 排产调度 (SCH) | 班次/班组、工人、考勤、生产调度 |
| 仓储管理 (WM) | 仓库/库区/储位、库存、收货/发货、到货通知 |
| 工作流 (Flowable) | 流程设计、审批流转、BPMN 流程编辑器 |
| 积木报表 | 在线报表设计、拖拽式配置 |
| 支付管理 | 多渠道支付、退款、对账 |

## 本地运行

```bash
# 安装依赖
npm install --registry=https://registry.npmmirror.com

# 启动开发服务器 (http://localhost:80)
npm run dev

# 构建生产环境
npm run build:prod

# 代码检查
npm run lint:eslint

# 代码格式化
npm run prettier
```

开发服务器通过 `/dev-api` 代理后端 API 到 `http://localhost:8080`。

## 环境要求

- Node >= 20.19.0
- npm >= 8.19.0

## 演示地址

http://nexuserp.website/index

账号：admin / 密码：admin123
