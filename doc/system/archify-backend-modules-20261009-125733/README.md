# Nexus-ERP 后端模块依赖图

日期：2026-10-09（Asia/Shanghai）。代码基线：`5b66fbeeff5a58028f248c68705e29b0bf968dd1`。

## 查看

用桌面浏览器打开 `backend-modules.html`，无需启动后端。
主图为分组后的重点关系总览，底部模块需要正常页面纵向滚动。`backend-modules-preview.png` 只是视口预览，不是整页导出。
精确审计某个模块时，优先查看 `module-dependencies.md` / `module-dependencies.json`；全部已提取声明不会因为图中分组而丢失。

## 图的语义

- 实线：**依赖方 → 被依赖方**，由源码 POM 的 project/dependencies 仓内直接声明支持。
- 虚线：构建聚合，不是调用链、运行加载或单独部署。
- 平台业务组含 System / IM / Pay / Report / Job；工作流工具组含 Flowable / Online / Generator。
- 三类公共库按职责分组；“成员引用”只表示存在成员级引用，不表示依赖该组每一个 JAR。库族不等于单一 Maven artifact。
- 图只选择主要直依赖；其他重复公共库引用、组内边、profile / dependencyManagement / plugin 条目保留在完整 JSON 中，按上下文区分。
- 未执行 Maven resolve/build；不能把父 POM、dependencyManagement 或构建聚合误当成直接运行依赖。

## 提取结果

- **54 个受跟踪 POM，268 条仓内 project/dependencies 直接声明**。
- 15 个业务模块；common 聚合 32 个子模块（含 common-bom 与 runtime-attestation-agent）；extend 聚合 2 个独立服务。
- ruoyi-admin 直接依赖 13 个业务模块；按静态非 optional compile/runtime 路径可达 14 个业务模块。
- Online 经 Flowable 间接引入；Demo 不在该 Admin 声明可达集。
- Flow → AI、Station → MES、ERP / MES → Flowable 有明确 POM 依赖。
- **MES → ERP、AI → ERP 没有直接 Maven 声明**。运行时桥接或工业数据端口是另一种关系，不因业务协作而伪造编译依赖。

这些结果来自固定提交的 XML，不等于成功解析全部第三方传递依赖、应用构建成功或生产已部署。

## 文件

| 文件 | 用途 |
|---|---|
| backend-modules.html | 最终交互式重点依赖总览 |
| backend-modules-preview.png | 浅色局部视口预览 |
| candidate.json | 最终可编辑图定义 |
| module-dependencies.md | 所有 54 个 POM 的仓内直接依赖表 |
| module-dependencies.json | 完整提取声明、scope、optional、POM 哈希和分组信息 |
| view-edge-evidence.json | 每条图示实线对应的实际成员级 POM 声明 |
| handoff.json | 当前规格 / 产物哈希与验收状态 |
| visual-review.json | 独立截图复核与密集线路限制 |
| repository-snapshot.json | 固定基线与工作区状态 |
| preview-derivation.json | 预览截图来源及裁切哈希 |
| review-3/ | **最终轮次**的自动回执、四张截图和联系表 |
| review-2/、candidate.before-placement.json | 失败的位置修订记录与通过版本备份 |

## 检查与视觉说明

- 最终 review-3 的 validate / deliver / check / browser-check 全部通过，**Showcase 9/9，0 错误，0 警告**。
- Edge 检查 1440×900、1600×1000、1920×1080、2048×1320；无横向溢出，接受正常页面纵向滚动。
- 浅色/深色端点截图捕获成功；Agent 查看了 1440 浅色及 2048 浅色/深色截图。
- 本图保留 **38 处已处理跨越**及部分绕线，不宣称线性、零交叉或审美完美。矩阵是完整精确审计入口。
- 按提示尝试过一次仅位置/尺寸的 DAG 重排，但新布局的标签/间距未通过；按 Archify 的恢复约定还原通过初稿，最终回执位于 review-3。没有继续盲目调坐标。

## 执行命令

工作目录：`E:\Nexus\nexus-erp`。

```powershell
$env:ARCHIFY_CHROME = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
node 'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs' finalize architecture 'doc/system/archify-backend-modules-20261009-125733/candidate.json' 'doc/system/archify-backend-modules-20261009-125733/backend-modules.html' --repo-root 'E:\Nexus\nexus-erp' --quality showcase --out-dir 'doc/system/archify-backend-modules-20261009-125733/review-3' --json
node 'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs' visual-check 'doc/system/archify-backend-modules-20261009-125733/backend-modules.html' --summary --require-provenance --out-dir 'doc/system/archify-backend-modules-20261009-125733/review-3'
```

本次只生成本目录的文档，不改业务代码、配置或数据库，不提交/推送，不启停服务；未声明 Maven / 应用测试 / 生产或真机验收通过。

## 交付前独立复核（2026-10-09）

- 完整浅色图面预览：`backend-modules-full-preview.png`。它只截取完整 SVG，不包含页尾说明卡片/导航控件；已使用图像查看工具检查，原生门禁回执保持不变。
- 补充预览来源与 SHA256：`supplemental-preview.json`。
- 独立源码及产物绑定复核：`independent-verification.json`；最后一次严格 `check --require-provenance --json`：`independent-final-check.json`，9/9、0 错误、0 警告。
- 观察到工作区 HEAD 已前进至 `2d8abf814df33c6f6433897264df072a202536a3`，本图仍固定到原绘制基线 `5b66fbeeff5a58028f248c68705e29b0bf968dd1`；已比对新增提交，被引用文件及全部 POM 未发生修改。新增的其他功能不声称已完整绘入本图。
- 本轮只补充预览及交接/验证记录，没有改写图定义或 HTML，没有应用构建、数据库连接、业务接口调用或生产验收。
