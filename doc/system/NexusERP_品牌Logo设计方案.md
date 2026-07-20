# NexusERP 品牌 Logo 设计方案

日期：2026-07-20　状态：已定稿（C2 四色加强版，经两轮可视化评审选定）

## 1. 背景与目标

当前三处品牌标识完全不一致：

| 位置 | 现状 | 问题 |
|------|------|------|
| 登录页品牌头（login.vue） | 内联 SVG（蓝紫渐变圆角方块 + 枢纽网络） | 与其他两处风格割裂 |
| 侧边栏 Logo.vue | `src/assets/logo/logo.png`（804×640 等距 3D 齿轮图，421KB） | 3D 复杂图在 32px 下糊成一团 |
| 浏览器 favicon | `public/favicon.ico` | 实为改名的 PNG（与 logo.png 同一文件），非标准 ICO，421KB |

目标：一套统一的品牌标识，覆盖登录页、侧边栏、favicon 三处，主题契合 ERP · MES · IoT · AI 一体化平台定位。

## 2. 最终方案：C2 · 六角蜂巢（四色加强版）

**概念**：六边形 = 工业螺母/蜂巢（制造业基因），内置四向枢纽网络 = 设备互联、数据汇聚（呼应品牌名 Nexus）。白色核心 = 一体化平台中枢，四色节点 = 四大模块。

**风格**：扁平几何 + 深色渐变容器 + 四色模块语言。

### 2.1 几何规格（64×64 viewBox）

| 元素 | 参数 |
|------|------|
| 容器 | 圆角矩形 64×64，rx=16，填充渐变 `#1e2a52` → `#0b0f24`（左上→右下） |
| 六边形环 | 顶点 (32,11) (50.2,21.5) (50.2,42.5) (32,53) (13.8,42.5) (13.8,21.5)；描边 4.4，圆角连接；填充 rgba(255,255,255,.04) |
| 环渐变 | 四色：`#409EFF` → `#22d3ee` → `#8b5cf6` → `#34d399`（沿左上→右下对角线，offset 0/.35/.7/1） |
| 辐条 | 中心 (32,32) → 四个斜向顶点，rgba(255,255,255,.4)，宽 2.2，圆头 |
| 节点光晕 | 四个斜向顶点处 r=7.2，各色 opacity .25 |
| 四色节点 | 四个斜向顶点处 r=4.6：左上 `#409EFF`、右上 `#22d3ee`、右下 `#8b5cf6`、左下 `#34d399` |
| 中枢 | 白色核心 r=6 + 外环 r=9（rgba(255,255,255,.3)，宽 1.4） |

### 2.2 色彩语义（沿用登录页既有四色体系）

| 颜色 | 色值 | 模块 |
|------|------|------|
| 蓝 | `#409EFF` | ERP 企业资源 |
| 青 | `#22d3ee` | MES 制造执行 |
| 紫 | `#8b5cf6` | IoT 物联网 |
| 绿 | `#34d399` | AI 智能助手 |

### 2.3 主 SVG（矢量源，作为唯一事实来源）

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
  <defs>
    <linearGradient id="nx-bg" x1="0" y1="0" x2="64" y2="64" gradientUnits="userSpaceOnUse">
      <stop stop-color="#1e2a52"/><stop offset="1" stop-color="#0b0f24"/>
    </linearGradient>
    <linearGradient id="nx-ring" x1="13.8" y1="11" x2="50.2" y2="53" gradientUnits="userSpaceOnUse">
      <stop stop-color="#409EFF"/><stop offset=".35" stop-color="#22d3ee"/>
      <stop offset=".7" stop-color="#8b5cf6"/><stop offset="1" stop-color="#34d399"/>
    </linearGradient>
  </defs>
  <rect width="64" height="64" rx="16" fill="url(#nx-bg)"/>
  <path d="M32 11L50.2 21.5V42.5L32 53L13.8 42.5V21.5Z" fill="rgba(255,255,255,.04)" stroke="url(#nx-ring)" stroke-width="4.4" stroke-linejoin="round"/>
  <g stroke="rgba(255,255,255,.4)" stroke-width="2.2" stroke-linecap="round">
    <path d="M32 32L50.2 21.5M32 32L50.2 42.5M32 32L13.8 42.5M32 32L13.8 21.5"/>
  </g>
  <circle cx="13.8" cy="21.5" r="7.2" fill="#409EFF" opacity=".25"/>
  <circle cx="50.2" cy="21.5" r="7.2" fill="#22d3ee" opacity=".25"/>
  <circle cx="50.2" cy="42.5" r="7.2" fill="#8b5cf6" opacity=".25"/>
  <circle cx="13.8" cy="42.5" r="7.2" fill="#34d399" opacity=".25"/>
  <circle cx="13.8" cy="21.5" r="4.6" fill="#409EFF"/>
  <circle cx="50.2" cy="21.5" r="4.6" fill="#22d3ee"/>
  <circle cx="50.2" cy="42.5" r="4.6" fill="#8b5cf6"/>
  <circle cx="13.8" cy="42.5" r="4.6" fill="#34d399"/>
  <circle cx="32" cy="32" r="9" fill="none" stroke="rgba(255,255,255,.3)" stroke-width="1.4"/>
  <circle cx="32" cy="32" r="6" fill="#fff"/>
</svg>
```

## 3. 交付物与落点

| 交付物 | 路径 | 说明 |
|--------|------|------|
| 矢量主文件 | `plus-ui/src/assets/logo/logo.svg` | 唯一事实来源，侧边栏直接引用 |
| 登录页品牌头 | `plus-ui/src/views/login.vue` | 替换现有内联 SVG（渐变 id 改为 `nx-logo-*` 避免文档内冲突） |
| 侧边栏 | `plus-ui/src/layout/components/Sidebar/Logo.vue` | import 由 logo.png 改为 logo.svg |
| 高清 PNG | `plus-ui/src/assets/logo/logo.png` | 512×512，覆盖旧 3D 齿轮图（供文档/移动端等栅格场景备用） |
| SVG favicon | `plus-ui/public/favicon.svg` | 现代浏览器 favicon |
| 标准 ICO | `plus-ui/public/favicon.ico` | 真 ICO 容器，内嵌 16/32/48 三尺寸 PNG |
| 入口声明 | `plus-ui/index.html` | `<link rel="icon" href="/favicon.ico" sizes="32x32">` + `<link rel="icon" href="/favicon.svg" type="image/svg+xml">` |

## 4. 资产生成技术管线

1. 以主 SVG 为源，用 **Edge headless**（`--headless --screenshot --window-size=N,N`，透明背景）栅格化 16/32/48/512 四档 PNG
2. 用 **Python Pillow**（已装 12.2.0）将 16/32/48 三档打包为标准多尺寸 ICO
3. 栅格产物与 SVG 目视比对（16px 下环与节点仍清晰为准）

## 5. 旧资产处置

- 3D 等距齿轮图（logo.png / favicon.ico，MD5 相同的同一文件）直接覆盖替换，不保留
- 登录页旧枢纽 SVG 由新设计替换，页面其余部分（四色标签墙等）不动

## 6. 验证

- `npm run dev` 启动后目视检查：登录页品牌头、侧边栏（深色/浅色主题、展开/折叠）、浏览器标签页 favicon
- favicon.ico 用 Pillow 读回校验包含 16/32/48 三尺寸且为真 ICO 格式
