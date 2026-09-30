import setupExtend from 'unplugin-vue-setup-extend-plus/vite';
import type { Plugin } from 'vite';

type HookHandler<T> = T extends { handler: infer Handler } ? Handler : T;

function withoutUnusedSourcemaps(plugin: Plugin): Plugin {
  const originalTransform = plugin.transform;
  if (!originalTransform) return plugin;
  const handler = typeof originalTransform === 'function' ? originalTransform : originalTransform.handler;
  const originalConfigResolved = plugin.configResolved;
  let needsSourcemap = true;

  const configResolved: HookHandler<NonNullable<Plugin['configResolved']>> = async function (config) {
    needsSourcemap = config.command !== 'build' || !!config.build.sourcemap;
    const configure = typeof originalConfigResolved === 'function' ? originalConfigResolved : originalConfigResolved?.handler;
    await configure?.call(this, config);
  };
  const transform: HookHandler<NonNullable<Plugin['transform']>> = async function (...args) {
    const result = await handler.apply(this, args);
    /**
     * 上游为 SFC 及未改变的 script 子请求生成高精度 sourceMap；即使构建不输出，Rollup 仍会解码为逐字符数组并保留在模块图中。
     * 仅在构建未启用 sourceMap 时不传递未使用的映射；开发模式及显式启用时保留。
     */
    if (!needsSourcemap && result && typeof result === 'object') {
      return { ...result, map: null };
    }
    return result;
  };

  return {
    ...plugin,
    configResolved:
      originalConfigResolved && typeof originalConfigResolved === 'object' ? { ...originalConfigResolved, handler: configResolved } : configResolved,
    transform: typeof originalTransform === 'function' ? transform : { ...originalTransform, handler: transform }
  };
}

export default () => {
  const plugins = setupExtend({});
  return Array.isArray(plugins) ? plugins.map(withoutUnusedSourcemaps) : withoutUnusedSourcemaps(plugins);
};
