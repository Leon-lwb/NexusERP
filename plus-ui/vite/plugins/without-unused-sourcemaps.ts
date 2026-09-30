import type { Plugin } from 'vite';

type HookHandler<T> = T extends { handler: infer Handler } ? Handler : T;

function wrapPlugin(plugin: Plugin): Plugin {
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
     * 只有同时包含 code 和 map 的转换结果，才会向 Rollup 保留的映射链加入映射。
     * null/undefined、字符串与不透明对象（如 Map）保持原样。
     */
    if (!needsSourcemap && result && typeof result === 'object' && !Array.isArray(result) && typeof result.code === 'string' && 'map' in result) {
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

export default function withoutUnusedSourcemaps(plugins: Plugin): Plugin;
export default function withoutUnusedSourcemaps(plugins: Plugin[]): Plugin[];
export default function withoutUnusedSourcemaps(plugins: Plugin | Plugin[]): Plugin | Plugin[];
export default function withoutUnusedSourcemaps(plugins: Plugin | Plugin[]): Plugin | Plugin[] {
  return Array.isArray(plugins) ? plugins.map(wrapPlugin) : wrapPlugin(plugins);
}
