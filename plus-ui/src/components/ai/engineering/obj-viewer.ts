import {
  AmbientLight,
  Box3,
  Color,
  DirectionalLight,
  DoubleSide,
  LoadingManager,
  Material,
  Mesh,
  MeshStandardMaterial,
  Object3D,
  PerspectiveCamera,
  Scene,
  Texture,
  Vector3,
  WebGLRenderer
} from 'three';
import { OBJLoader } from 'three/addons/loaders/OBJLoader.js';
import { MTLLoader } from 'three/addons/loaders/MTLLoader.js';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { assertSafeObjText, assertModelTextBudget, MAX_MODEL_VERTICES } from './model-text';

/** 解析真实 OBJ/MTL 数据，纹理 URL 全部经过受控资源解析器。 */
export function parseEngineeringObj(objText: string, mtlText?: string, urlModifier?: (url: string) => string, maxVertices = MAX_MODEL_VERTICES) {
  assertSafeObjText(objText, maxVertices);
  if (mtlText) assertModelTextBudget(mtlText.length, 'MTL');
  const manager = new LoadingManager();
  manager.setURLModifier(
    urlModifier ||
      (() => {
        throw new Error('模型纹理未获授权');
      })
  );
  let textureStarted = false;
  let resolveReady: () => void;
  let rejectReady: (error: Error) => void;
  const ready = new Promise<void>((resolve, reject) => {
    resolveReady = resolve;
    rejectReady = reject;
  });
  // 异步纹理失败保留给调用者处理，预先登记避免同步解析失败产生未处理拒绝。
  ready.catch(() => {});
  manager.onStart = () => {
    textureStarted = true;
  };
  manager.onLoad = () => resolveReady();
  manager.onError = () => rejectReady(new Error('模型纹理解码失败，请核对导出文件'));
  const loader = new OBJLoader(manager);
  const materials = mtlText ? new MTLLoader(manager).parse(mtlText, '') : undefined;
  if (materials) loader.setMaterials(materials);
  let object: Object3D | undefined;
  try {
    object = loader.parse(objText);
    let count = 0;
    object.traverse((child) => {
      const mesh = child as Mesh;
      const position = mesh.geometry?.getAttribute('position');
      if (!position) return;
      count += position.count;
      if (count > maxVertices) throw new Error('OBJ 展开后的几何顶点数量超限');
      for (let i = 0; i < position.count; i++)
        if (![position.getX(i), position.getY(i), position.getZ(i)].every(Number.isFinite)) throw new Error('OBJ 几何包含无效坐标');
      if (!materials) {
        const original = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
        original.forEach((material) => material?.dispose());
        mesh.material = new MeshStandardMaterial({ color: 0x347b59, roughness: 0.75, side: DoubleSide });
      }
    });
    const bounds = new Box3().setFromObject(object);
    if (!count || bounds.isEmpty()) throw new Error('OBJ 不包含可预览几何');
    if (!textureStarted) resolveReady();
    return { object, bounds, hasMaterials: !!materials, ready };
  } catch (error) {
    if (object) disposeEngineeringObject(object);
    if (materials) for (const material of Object.values(materials.materials)) disposeMaterial(material, new Set());
    throw error;
  }
}
/** 释放材质所有纹理，不重复释放同一张贴图。 */
function disposeMaterial(material: Material, textures: Set<Texture>): void {
  for (const value of Object.values(material))
    if (value instanceof Texture && !textures.has(value)) {
      textures.add(value);
      value.dispose();
    }
  material.dispose();
}
/** 遍历几何、材质和纹理，所有共享对象只释放一次。 */
export function disposeEngineeringObject(object: Object3D): void {
  const geometries = new Set();
  const materials = new Set<Material>();
  const textures = new Set<Texture>();
  object.traverse((child) => {
    const mesh = child as Mesh;
    if (mesh.geometry && !geometries.has(mesh.geometry)) {
      geometries.add(mesh.geometry);
      mesh.geometry.dispose();
    }
    for (const material of Array.isArray(mesh.material) ? mesh.material : [mesh.material])
      if (material && !materials.has(material)) {
        materials.add(material);
        disposeMaterial(material, textures);
      }
  });
  object.removeFromParent();
}
/** 统一管理所有浏览器资源，切换、隐藏或卸载均可安全重复调用。 */
export function createViewerDisposer(parts: {
  cancelFrame: () => void;
  disconnect: () => void;
  controls: { dispose: () => void };
  disposeObject: () => void;
  renderer: { dispose: () => void; forceContextLoss: () => void };
  resources: { dispose: () => void };
}): () => void {
  let disposed = false;
  return () => {
    if (disposed) return;
    disposed = true;
    parts.cancelFrame();
    parts.disconnect();
    parts.controls.dispose();
    parts.disposeObject();
    parts.renderer.dispose();
    parts.renderer.forceContextLoss();
    parts.resources.dispose();
  };
}
/** 创建三维视口；相机仅用于查看，不对工程尺寸作认证。 */
export function createEngineeringViewer(container: HTMLElement, parsed: ReturnType<typeof parseEngineeringObj>, resources: { dispose: () => void }) {
  const renderer = new WebGLRenderer({ antialias: true, alpha: true });
  let controls: OrbitControls | undefined;
  let observer: ResizeObserver | undefined;
  let frame = 0;
  let disposed = false;
  try {
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.setClearColor(new Color('#f5f7fa'), 1);
    container.appendChild(renderer.domElement);
    const scene = new Scene();
    scene.add(parsed.object);
    scene.add(new AmbientLight(0xffffff, 2));
    const light = new DirectionalLight(0xffffff, 3);
    light.position.set(3, 6, 5);
    scene.add(light);
    const camera = new PerspectiveCamera(45, 1, 0.01, 10000);
    controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    const resize = () => {
      const width = Math.max(container.clientWidth, 1);
      const height = Math.max(container.clientHeight, 1);
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
      renderer.setSize(width, height);
    };
    const fit = () => {
      const center = parsed.bounds.getCenter(new Vector3());
      const radius = Math.max(parsed.bounds.getSize(new Vector3()).length() / 2, 0.001);
      const distance = (radius / Math.sin((camera.fov * Math.PI) / 360) / Math.min(camera.aspect, 1)) * 1.2;
      camera.near = Math.max(distance / 10000, 0.00001);
      camera.far = distance * 20;
      camera.updateProjectionMatrix();
      camera.position.copy(center).add(new Vector3(1, 0.8, 1.3).normalize().multiplyScalar(distance));
      controls.target.copy(center);
      controls.minDistance = radius / 20;
      controls.maxDistance = distance * 10;
      controls.update();
      controls.saveState();
    };
    observer = new ResizeObserver(resize);
    observer.observe(container);
    resize();
    fit();
    const animate = () => {
      if (disposed) return;
      frame = requestAnimationFrame(animate);
      controls.update();
      renderer.render(scene, camera);
    };
    animate();
    const dispose = createViewerDisposer({
      cancelFrame: () => {
        disposed = true;
        cancelAnimationFrame(frame);
      },
      disconnect: () => observer?.disconnect(),
      controls,
      disposeObject: () => {
        disposeEngineeringObject(parsed.object);
        renderer.domElement.remove();
      },
      renderer,
      resources
    });
    return { fit, reset: () => controls?.reset(), dispose };
  } catch (error) {
    // 创建成功前几何和资源仍由调用方持有，此处只清理局部浏览器句柄。
    disposed = true;
    cancelAnimationFrame(frame);
    observer?.disconnect();
    controls?.dispose();
    renderer.domElement.remove();
    renderer.dispose();
    renderer.forceContextLoss();
    throw error;
  }
}
