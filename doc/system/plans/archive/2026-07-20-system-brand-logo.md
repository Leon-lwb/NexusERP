# NexusERP 品牌 Logo 统一替换 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以定稿的 C2 六角蜂巢（四色加强版）设计统一替换登录页品牌头、侧边栏 Logo、浏览器 favicon 三处品牌标识。

**Architecture:** 手写主 SVG（唯一事实来源）→ Edge headless 透明背景截图栅格化 16/32/48/512 四档 PNG → Python Pillow 打包标准多尺寸 ICO 并读回校验 → 修改三处代码引用。设计规格见 `doc/system/NexusERP_品牌Logo设计方案.md`。

**Tech Stack:** SVG、Microsoft Edge headless（`C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe`）、Python 3.10 + Pillow 12.2.0（`C:/Python310/python.exe`）、Vite 7。

**关键事实（零上下文必读）：**
- 旧 `plus-ui/public/favicon.ico` 与旧 `plus-ui/src/assets/logo/logo.png` 是同一文件（MD5 相同）：804×640 PNG 伪装成 ico，421KB，直接覆盖替换。
- Vite 的 `vite-plugin-svg-icons-ng` 只处理 `src/assets/icons/svg` 目录；`src/assets/logo/logo.svg` 走默认资产管道，`import logo from '@/assets/logo/logo.svg'` 返回 URL 字符串，`<img :src="logo">` 可用。
- 全局仅 `Logo.vue:25` 一处 import logo.png；`index.html:8` 一处引用 favicon.ico；登录页品牌 SVG 为 `login.vue:17-33` 内联代码。
- 栅格化临时目录用系统临时目录 `$TEMP/nx-logo-build`，不入库、用完即删。

---

### Task 1: 主 SVG 与 favicon.svg

**Files:**
- Create: `plus-ui/src/assets/logo/logo.svg`
- Create: `plus-ui/public/favicon.svg`

- [ ] **Step 1: 写主 SVG 文件**

两个文件内容完全相同（favicon.svg 是主 SVG 在 public 的副本）。注意辐条组必须显式 `fill="none"`：

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
  <defs>
    <linearGradient id="nx-bg" x1="0" y1="0" x2="64" y2="64" gradientUnits="userSpaceOnUse">
      <stop stop-color="#1e2a52"/>
      <stop offset="1" stop-color="#0b0f24"/>
    </linearGradient>
    <linearGradient id="nx-ring" x1="13.8" y1="11" x2="50.2" y2="53" gradientUnits="userSpaceOnUse">
      <stop stop-color="#409EFF"/>
      <stop offset=".35" stop-color="#22d3ee"/>
      <stop offset=".7" stop-color="#8b5cf6"/>
      <stop offset="1" stop-color="#34d399"/>
    </linearGradient>
  </defs>
  <rect width="64" height="64" rx="16" fill="url(#nx-bg)"/>
  <path d="M32 11L50.2 21.5V42.5L32 53L13.8 42.5V21.5Z" fill="rgba(255,255,255,.04)" stroke="url(#nx-ring)" stroke-width="4.4" stroke-linejoin="round"/>
  <g fill="none" stroke="rgba(255,255,255,.4)" stroke-width="2.2" stroke-linecap="round">
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

- [ ] **Step 2: XML 合法性校验**

```bash
C:/Python310/python.exe -c "import xml.dom.minidom as m; m.parse('plus-ui/src/assets/logo/logo.svg'); m.parse('plus-ui/public/favicon.svg'); print('SVG_XML_OK')"
```

Expected: 输出 `SVG_XML_OK`

- [ ] **Step 3: Commit**

```bash
git add plus-ui/src/assets/logo/logo.svg plus-ui/public/favicon.svg
git commit -m "feat(brand): 新增品牌 logo 主 SVG 与 favicon.svg"
```

---

### Task 2: Edge headless 栅格化四档 PNG

**Files:**
- Create（临时）: `$TEMP/nx-logo-build/logo-{16,32,48,512}.png`

- [ ] **Step 1: 执行栅格化**

```bash
BUILD_DIR="$TEMP/nx-logo-build"
mkdir -p "$BUILD_DIR"
EDGE="C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
SVG="file:///E:/Nexus/nexus-erp/plus-ui/src/assets/logo/logo.svg"
for N in 16 32 48 512; do
  "$EDGE" --headless --disable-gpu --no-first-run --hide-scrollbars \
    --user-data-dir="$BUILD_DIR/profile-$N" \
    --default-background-color=00000000 \
    --window-size=$N,$N \
    --screenshot="$BUILD_DIR/logo-$N.png" \
    "$SVG" >/dev/null 2>&1
done
ls -la "$BUILD_DIR"/*.png
```

Expected: 生成 4 个 PNG 文件。若某尺寸缺失，重跑一次该尺寸命令（Edge 首次创建 profile 偶发慢/失败）。

- [ ] **Step 2: PIL 校验尺寸/透明角/白色中枢（失败测试）**

```bash
C:/Python310/python.exe - <<'EOF'
from PIL import Image
import os
build = os.path.join(os.environ['TEMP'], 'nx-logo-build')
for n in (16, 32, 48, 512):
    p = os.path.join(build, f'logo-{n}.png')
    im = Image.open(p).convert('RGBA')
    assert im.size == (n, n), f'{p} 尺寸错误 {im.size}'
    corner = im.getpixel((0, 0))
    assert corner[3] == 0, f'{p} 角部不透明 {corner}'
    center = im.getpixel((n // 2, n // 2))
    assert center[0] > 200 and center[1] > 200 and center[2] > 200 and center[3] == 255, f'{p} 中枢非白色 {center}'
    print(f'logo-{n}.png OK center={center}')
print('PNG_ALL_OK')
EOF
```

Expected: 输出 `PNG_ALL_OK`。
若报"角部不透明"：说明 `--default-background-color` 未生效（旧版 Edge），回退方案 —— 写 `$TEMP/nx-logo-build/wrap.html`（`<body style="margin:0">` 内嵌同内容 inline SVG 且 width/height 设为对应像素），对 wrap.html 截图后重跑本校验。

---

### Task 3: Pillow 打包标准 ICO

**Files:**
- Modify（覆盖）: `plus-ui/public/favicon.ico`

- [ ] **Step 1: 打包 ICO**

```bash
C:/Python310/python.exe - <<'EOF'
from PIL import Image
import os
build = os.path.join(os.environ['TEMP'], 'nx-logo-build')
i16 = Image.open(os.path.join(build, 'logo-16.png')).convert('RGBA')
i32 = Image.open(os.path.join(build, 'logo-32.png')).convert('RGBA')
i48 = Image.open(os.path.join(build, 'logo-48.png')).convert('RGBA')
target = r'E:/Nexus/nexus-erp/plus-ui/public/favicon.ico'
i48.save(target, format='ICO', append_images=[i32, i16])
print('ICO_SAVED', os.path.getsize(target), 'bytes')
EOF
```

Expected: 输出 `ICO_SAVED <几KB~几十KB> bytes`（远小于旧文件 421410 字节）。

- [ ] **Step 2: 读回校验（尺寸集 + 格式 + 像素）**

```bash
C:/Python310/python.exe - <<'EOF'
from PIL import Image
ico = Image.open(r'E:/Nexus/nexus-erp/plus-ui/public/favicon.ico')
sizes = ico.ico.sizes()
assert sizes == {(16, 16), (32, 32), (48, 48)}, f'尺寸集错误 {sizes}'
print('ICO_SIZES_OK', sizes)
EOF
file plus-ui/public/favicon.ico
```

Expected: 输出 `ICO_SIZES_OK {(16, 16), (32, 32), (48, 48)}`，且 `file` 显示 `MS Windows icon resource - 3 icons`。

- [ ] **Step 3: 覆盖 logo.png 为 512 高清版**

```bash
cp "$TEMP/nx-logo-build/logo-512.png" "E:/Nexus/nexus-erp/plus-ui/src/assets/logo/logo.png"
file "E:/Nexus/nexus-erp/plus-ui/src/assets/logo/logo.png"
```

Expected: `PNG image data, 512 x 512, 8-bit/color RGBA`

- [ ] **Step 4: Commit**

```bash
git add plus-ui/public/favicon.ico plus-ui/src/assets/logo/logo.png
git commit -m "feat(brand): favicon.ico 替换为标准多尺寸 ICO，logo.png 替换为新品牌 512px 版"
```

---

### Task 4: index.html 与 Logo.vue 引用更新

**Files:**
- Modify: `plus-ui/index.html:8`
- Modify: `plus-ui/src/layout/components/Sidebar/Logo.vue:25`

- [ ] **Step 1: index.html favicon 声明**

将：

```html
    <link rel="icon" href="/favicon.ico" />
```

替换为（ICO 兜底在前，现代浏览器优先取 SVG）：

```html
    <link rel="icon" href="/favicon.ico" sizes="32x32" />
    <link rel="icon" href="/favicon.svg" type="image/svg+xml" />
```

- [ ] **Step 2: Logo.vue 改引 SVG**

将第 25 行：

```ts
import logo from '@/assets/logo/logo.png';
```

替换为：

```ts
import logo from '@/assets/logo/logo.svg';
```

- [ ] **Step 3: Commit**

```bash
git add plus-ui/index.html plus-ui/src/layout/components/Sidebar/Logo.vue
git commit -m "feat(brand): 侧边栏与 favicon 引用切换为新品牌 SVG/ICO"
```

---

### Task 5: login.vue 品牌头内联 SVG 替换

**Files:**
- Modify: `plus-ui/src/views/login.vue:17-33`

- [ ] **Step 1: 替换内联 SVG**

将 `<div class="brand-logo">` 内的整个 `<svg>...</svg>`（渐变 id 为 `nx-grad` 的旧枢纽图案）替换为下述内容（渐变 id 使用 `nx-logo-*` 前缀避免文档内冲突；页面其余代码、`.brand-logo` 样式均不动）：

```html
          <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <linearGradient id="nx-logo-bg" x1="0" y1="0" x2="64" y2="64" gradientUnits="userSpaceOnUse">
                <stop stop-color="#1e2a52" />
                <stop offset="1" stop-color="#0b0f24" />
              </linearGradient>
              <linearGradient id="nx-logo-ring" x1="13.8" y1="11" x2="50.2" y2="53" gradientUnits="userSpaceOnUse">
                <stop stop-color="#409EFF" />
                <stop offset=".35" stop-color="#22d3ee" />
                <stop offset=".7" stop-color="#8b5cf6" />
                <stop offset="1" stop-color="#34d399" />
              </linearGradient>
            </defs>
            <rect width="64" height="64" rx="16" fill="url(#nx-logo-bg)" />
            <path d="M32 11L50.2 21.5V42.5L32 53L13.8 42.5V21.5Z" fill="rgba(255,255,255,.04)" stroke="url(#nx-logo-ring)" stroke-width="4.4" stroke-linejoin="round" />
            <g fill="none" stroke="rgba(255,255,255,.4)" stroke-width="2.2" stroke-linecap="round">
              <path d="M32 32L50.2 21.5M32 32L50.2 42.5M32 32L13.8 42.5M32 32L13.8 21.5" />
            </g>
            <circle cx="13.8" cy="21.5" r="7.2" fill="#409EFF" opacity=".25" />
            <circle cx="50.2" cy="21.5" r="7.2" fill="#22d3ee" opacity=".25" />
            <circle cx="50.2" cy="42.5" r="7.2" fill="#8b5cf6" opacity=".25" />
            <circle cx="13.8" cy="42.5" r="7.2" fill="#34d399" opacity=".25" />
            <circle cx="13.8" cy="21.5" r="4.6" fill="#409EFF" />
            <circle cx="50.2" cy="21.5" r="4.6" fill="#22d3ee" />
            <circle cx="50.2" cy="42.5" r="4.6" fill="#8b5cf6" />
            <circle cx="13.8" cy="42.5" r="4.6" fill="#34d399" />
            <circle cx="32" cy="32" r="9" fill="none" stroke="rgba(255,255,255,.3)" stroke-width="1.4" />
            <circle cx="32" cy="32" r="6" fill="#fff" />
          </svg>
```

- [ ] **Step 2: ESLint 校验改动文件**

```bash
cd plus-ui && npx eslint src/views/login.vue src/layout/components/Sidebar/Logo.vue
```

Expected: 无 error（warning 可接受，但此二文件改动不应引入新 warning）。

- [ ] **Step 3: Commit**

```bash
git add plus-ui/src/views/login.vue
git commit -m "feat(brand): 登录页品牌头替换为六角蜂巢新标识"
```

---

### Task 6: 冒烟验证、清理与交付确认

- [ ] **Step 1: dev server 冒烟测试**

```bash
cd plus-ui && npm run dev
```

（后台启动；若 80 端口已被占用说明用户已有 dev server 在跑，直接复用即可。）待 ready 后：

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/favicon.svg
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/favicon.ico
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/src/views/login.vue
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/src/layout/components/Sidebar/Logo.vue
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/src/assets/logo/logo.svg
```

Expected: 全部 `200`（模块 URL 若 500 说明 import/语法错误，需查看 vite 输出修复）。验证完关闭后台 dev server。

- [ ] **Step 2: 清理临时目录**

```bash
rm -rf "$TEMP/nx-logo-build"
```

- [ ] **Step 3: 最终状态确认**

```bash
git status --short
git log --oneline -5
```

Expected: 工作区仅剩用户既有的 3 个未提交改动（main.ts、auto-import.ts、components.ts），本计划相关文件全部已提交，共 5 个新 commit（含设计文档）。

- [ ] **Step 4: 请用户目视验收**

请用户打开 `http://localhost` 检查：① 登录页品牌头新标识；② 登录后侧边栏（深色/浅色主题、展开/折叠两种状态）；③ 浏览器标签页 favicon。如有细节微调（环粗细、圆角、节点大小），改主 SVG 后重跑 Task 2-3 即可同步所有栅格产物。

---

## Self-Review 记录

- **Spec 覆盖**：设计文档 §3 交付物 7 项 ↔ Task 1（logo.svg、favicon.svg）、Task 3（favicon.ico、logo.png）、Task 4（index.html、Logo.vue）、Task 5（login.vue）一一对应；§4 技术管线 ↔ Task 2/3；§5 旧资产处置 ↔ Task 3 覆盖替换；§6 验证 ↔ Task 6。无遗漏。
- **Placeholder 扫描**：全部步骤含完整代码/命令与预期输出，无 TBD/TODO。
- **一致性**：渐变 id 在独立 SVG 用 `nx-bg`/`nx-ring`，login.vue 内联版用 `nx-logo-bg`/`nx-logo-ring`（防文档内冲突），两处均已写明，不会混用；`$TEMP/nx-logo-build` 路径在 Task 2/3/6 一致。
