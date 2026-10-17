import assert from 'node:assert/strict';

/** 在真实页面连续采样，等待字体、图片、加载层及活动动画收尾，保留正常打开的弹窗。 */
export async function waitForVisualReady() {
  const started = performance.now();
  let changedAt = started;
  let previous = '';
  let samples = 0;
  const seenAnimations = new Set();
  const seenBlockers = new Set();
  const observer = new MutationObserver(() => {
    changedAt = performance.now();
  });
  observer.observe(document.body, { subtree: true, childList: true, attributes: true, characterData: true });

  /** 隐藏副本不参与截图，但透明的入场元素仍须等待其动画结束。 */
  function laidOut(element) {
    return element?.isConnected && element.getClientRects().length && getComputedStyle(element).visibility !== 'hidden';
  }

  /** 仅记录视口中的可见样式，避免把纸张之外的未显示副本作为画面证据。 */
  function visible(element) {
    if (!laidOut(element)) return false;
    const rect = element.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0 || rect.bottom <= 0 || rect.right <= 0 || rect.top >= innerHeight || rect.left >= innerWidth)
      return false;
    for (let ancestor = element; ancestor; ancestor = ancestor.parentElement) {
      if (Number(getComputedStyle(ancestor).opacity) === 0) return false;
    }
    return true;
  }

  /** 类名和文本只用于诊断，不修改产品DOM或样式。 */
  function describe(element) {
    return { element: element.tagName.toLowerCase() + '.' + [...element.classList].join('.'), text: element.textContent.trim().slice(0, 100) };
  }

  /** 透明背景按祖先层记录，供Node侧计算实际文本对比度。 */
  function style(element) {
    const computed = getComputedStyle(element);
    const rect = element.getBoundingClientRect();
    const backgroundLayers = [];
    for (let ancestor = element; ancestor; ancestor = ancestor.parentElement) backgroundLayers.push(getComputedStyle(ancestor).backgroundColor);
    return {
      ...describe(element),
      color: computed.color,
      backgroundColor: computed.backgroundColor,
      backgroundLayers,
      opacity: computed.opacity,
      transform: computed.transform,
      display: computed.display,
      rect: [rect.left, rect.top, rect.width, rect.height].map((value) => Math.round(value * 1000) / 1000)
    };
  }

  try {
    while (true) {
      samples++;
      const animations = document
        .getAnimations()
        .filter((animation) => (animation.pending || animation.playState === 'running') && laidOut(animation.effect?.target))
        .map((animation) => ({
          ...describe(animation.effect.target),
          playState: animation.playState,
          pending: animation.pending,
          property: animation.transitionProperty || animation.animationName || 'WebAnimation',
          duration: animation.effect.getComputedTiming().duration,
          iterations: animation.effect.getComputedTiming().iterations
        }));
      const blockers = [
        ...document.querySelectorAll(
          '.el-loading-mask, .el-button.is-loading, .el-skeleton.is-animated, [aria-busy="true"], [class*="-enter-active"], [class*="-leave-active"]'
        )
      ]
        .filter(laidOut)
        .map(describe);
      for (const animation of animations) seenAnimations.add(animation.element + ':' + animation.property);
      for (const blocker of blockers) seenBlockers.add(blocker.element);
      const root = getComputedStyle(document.documentElement);
      const state = {
        dark: document.documentElement.classList.contains('dark'),
        viewport: { width: innerWidth, height: innerHeight },
        fonts: document.fonts.status,
        images: [...document.images].filter((element) => laidOut(element) && !element.complete).map((element) => element.getAttribute('src')),
        animations,
        blockers,
        body: style(document.body),
        theme: Object.fromEntries(
          ['--el-bg-color', '--el-bg-color-page', '--el-fill-color-blank', '--el-text-color-primary'].map((name) => [
            name,
            root.getPropertyValue(name).trim()
          ])
        ),
        cards: [...document.querySelectorAll('.el-card')].filter(visible).map((element) => ({
          ...style(element),
          headings: [...element.querySelectorAll('h2, h3, .report-section-title, .report-section-description')].filter(visible).map(style)
        })),
        overlays: [...document.querySelectorAll('.el-overlay, .el-popper')].filter(visible).map(style),
        drawers: [...document.querySelectorAll('.el-drawer .business-print-content')].filter(visible).map((element) => ({
          ...style(element),
          padding: getComputedStyle(element).padding,
          variables: Object.fromEntries(
            ['--report-surface', '--report-muted', '--report-line'].map((name) => [name, getComputedStyle(element).getPropertyValue(name).trim()])
          ),
          headings: [...element.querySelectorAll('.report-section-heading')].filter(visible).map(style)
        }))
      };
      const signature = JSON.stringify(state);
      if (signature !== previous || animations.length || blockers.length || state.images.length || state.fonts !== 'loaded')
        changedAt = performance.now();
      previous = signature;
      const quietMs = performance.now() - changedAt;
      const ready = samples >= 3 && quietMs >= 160;
      if (ready || performance.now() - started > 12000)
        return {
          ready,
          elapsedMs: Math.round(performance.now() - started),
          samples,
          quietMs: Math.round(quietMs),
          waitedFor: { animations: [...seenAnimations], blockers: [...seenBlockers] },
          ...state
        };
      await new Promise((resolve) => requestAnimationFrame(resolve));
    }
  } finally {
    observer.disconnect();
  }
}

/** 将浏览器实际计算的RGB颜色解析为带透明度的通道，不猜测主题默认值。 */
function channels(color) {
  const match = color.match(/^rgba?\(([^)]+)\)$/);
  assert.ok(match, '无法解析实际计算颜色：' + color);
  const values = match[1]
    .trim()
    .split(/[,\s/]+/)
    .map(Number);
  assert.ok(values.length === 3 || values.length === 4, '无效计算颜色：' + color);
  return [...values.slice(0, 3), values[3] ?? 1];
}

/** 透明前景按实际背景合成，避免透明卡片或文字得到虚假的高对比度。 */
function composite(foreground, background) {
  return foreground.slice(0, 3).map((value, index) => value * foreground[3] + background[index] * (1 - foreground[3]));
}

/** 依照sRGB相对亮度计算普通文字的4.5:1对比度。 */
function luminance(color) {
  const linear = color.map((value) => {
    const normalized = value / 255;
    return normalized <= 0.04045 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4;
  });
  return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722;
}

/** 对稳定后的深色卡片和可见标题留存计算样式、亮度及对比度证据。 */
export function assertVisualReady(state) {
  assert.ok(state.ready, '截图等待超时，活动动画或遮罩仍未收尾：' + JSON.stringify(state));
  const results = { darkCards: [], drawerHeadings: [] };
  if (state.dark) {
    assert.ok(state.cards.length > 0, '深色截图缺少实际可见卡片');
    for (const card of state.cards) {
      const texts = [card, ...card.headings].map((text) => {
        const background = [...text.backgroundLayers].reverse().reduce((under, layer) => composite(channels(layer), under), [255, 255, 255]);
        const foreground = composite(channels(text.color), background);
        const foregroundLuminance = luminance(foreground),
          backgroundLuminance = luminance(background);
        const contrast = (Math.max(foregroundLuminance, backgroundLuminance) + 0.05) / (Math.min(foregroundLuminance, backgroundLuminance) + 0.05);
        const result = {
          element: text.element,
          text: text.text,
          color: text.color,
          backgroundColor: text.backgroundColor,
          effectiveBackground: background,
          backgroundLuminance,
          contrast
        };
        assert.ok(backgroundLuminance < 0.18 && contrast >= 4.5, '稳定深色卡片背景或文字对比度失败：' + JSON.stringify(result));
        return result;
      });
      results.darkCards.push({ element: card.element, texts });
    }
  }
  for (const drawer of state.drawers) {
    assert.equal(drawer.padding, '0px', '业务抽屉共享样式应保持内容padding为0');
    for (const [name, value] of Object.entries(drawer.variables)) assert.ok(value, '业务抽屉缺少主题变量：' + name);
    for (const heading of drawer.headings) {
      assert.equal(heading.display, 'flex', '业务抽屉查询条件或结果标题未使用共享flex样式：' + JSON.stringify(heading));
      results.drawerHeadings.push({ text: heading.text, display: heading.display, variables: drawer.variables });
    }
  }
  return results;
}
