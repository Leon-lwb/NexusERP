import * as THREE from 'three';
import type { WarehouseArea3dVO } from '@/api/erp/wm/warehouse/types';

export interface SceneLabels {
  /** 挂入 scene 的根组（含全部标签） */
  root: THREE.Group;
  /** 每个库区一个子组（key: areaId），供库区显隐联动 */
  areaGroups: Map<number, THREE.Group>;
  /** 总开关 */
  setVisible(visible: boolean): void;
  dispose(): void;
}

/** 库位标签默认不渲染的规模阈值（超过则仅渲染库区标签，防视觉噪声） */
export const LOCATION_LABEL_MAX_COUNT = 300;

const num = (v: unknown, dft: number): number => {
  const n = Number(v);
  return v == null || Number.isNaN(n) ? dft : n;
};

/** 圆角矩形路径 */
const roundRect = (ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) => {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
};

/** 生成圆角色块文字标签纹理（返回宽高比供 Sprite 缩放） */
const makeLabelTexture = (text: string, bg: string): { texture: THREE.CanvasTexture; aspect: number } => {
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d')!;
  const font = 'bold 28px "Helvetica Neue", Arial, "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.font = font;
  const padding = 18;
  const w = Math.max(Math.ceil(ctx.measureText(text).width) + padding * 2, 60);
  const h = 48;
  canvas.width = w;
  canvas.height = h;
  // canvas 尺寸变更会重置上下文状态，需重设字体
  ctx.font = font;
  roundRect(ctx, 1, 1, w - 2, h - 2, 9);
  ctx.fillStyle = bg;
  ctx.fill();
  ctx.fillStyle = '#ffffff';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(text, w / 2, h / 2 + 1);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.anisotropy = 2;
  return { texture, aspect: w / h };
};

/**
 * 场景标签：库区名（悬浮于库区顶上方）+ 库位编码（showLabel=1 且未超规模阈值）。
 * Sprite 始终面向相机；depthTest 开启，被厂房/货架遮挡时不透出，与写实风格一致。
 */
export function buildSceneLabels(areas: WarehouseArea3dVO[]): SceneLabels {
  const root = new THREE.Group();

  const totalLocations = areas.reduce((sum, a) => sum + (a.locations?.length || 0), 0);
  const showLocationLabels = totalLocations <= LOCATION_LABEL_MAX_COUNT;

  const textures: THREE.CanvasTexture[] = [];
  const materials: THREE.SpriteMaterial[] = [];

  const addSprite = (group: THREE.Group, text: string, bg: string, height: number, x: number, y: number, z: number) => {
    const { texture, aspect } = makeLabelTexture(text, bg);
    textures.push(texture);
    const mat = new THREE.SpriteMaterial({ map: texture, transparent: true, depthTest: true });
    materials.push(mat);
    const sprite = new THREE.Sprite(mat);
    sprite.scale.set(height * aspect, height, 1);
    sprite.position.set(x, y, z);
    group.add(sprite);
  };

  for (const area of areas) {
    const areaGroup = new THREE.Group();
    // 库区标签：库区顶上方悬浮
    if (Number(area.showLabel ?? 1) === 1) {
      const topY = num(area.posY, 0) + num(area.areaHeight, 3);
      addSprite(areaGroup, area.areaName || area.areaCode || '库区', area.color || '#409eff', 1.0, num(area.posX, 0), topY + 0.7, num(area.posZ, 0));
    }
    // 库位标签：编码小签，TOP 悬浮于库位顶 / FRONT 贴前立面
    if (showLocationLabels) {
      for (const loc of area.locations || []) {
        if (Number(loc.showLabel ?? 0) !== 1) continue;
        const w = num(loc.width, 1);
        const h = num(loc.height, 1);
        const d = num(loc.locationDepth, 1);
        const size = Math.max(num(loc.labelSize, 0.3) * 2, 0.4);
        const front = (loc.labelPosition || 'TOP').toUpperCase() === 'FRONT';
        const x = num(loc.posX, 0);
        const y = front ? num(loc.posY, 0) + h / 2 : num(loc.posY, 0) + h + size * 0.7;
        const z = front ? num(loc.posZ, 0) + d / 2 + 0.06 : num(loc.posZ, 0);
        addSprite(areaGroup, loc.locationCode || loc.locationName || '', '#37414f', size, x, y, z);
      }
    }
    root.add(areaGroup);
  }

  // 组装 areaGroups 映射（root 子组按 areas 顺序一一对应）
  const areaGroups = new Map<number, THREE.Group>();
  areas.forEach((area, i) => {
    const g = root.children[i];
    if (g instanceof THREE.Group) areaGroups.set(area.areaId, g);
  });

  return {
    root,
    areaGroups,
    setVisible(visible: boolean) {
      root.visible = visible;
    },
    dispose() {
      for (const t of textures) t.dispose();
      for (const m of materials) m.dispose();
      textures.length = 0;
      materials.length = 0;
      root.clear();
    }
  };
}
