/**
 * 后台返回的路由动态生成name 解决缓存问题
 * 感谢 @fourteendp
 * 详见 https://github.com/vbenjs/vue-vben-admin/issues/3927
 */
import { Component, defineComponent, h } from 'vue';

interface Options {
  name?: string;
}

export function createCustomNameComponent(loader: () => Promise<any>, options: Options = {}): () => Promise<Component> {
  const { name } = options;
  let component: Component | null = null;
  let loadError: Error | null = null;

  const load = async () => {
    try {
      const module = await loader();
      component = module.default || module;
      if (!component) {
        throw new Error(`Component ${name} has no default export`);
      }
    } catch (error) {
      loadError = error as Error;
      console.error(`Cannot resolve component ${name}, error:`, error);
    }
  };

  return async () => {
    if (!component && !loadError) {
      await load();
    }

    if (loadError || !component) {
      // 返回错误提示组件
      return defineComponent({
        name: 'ErrorComponent',
        render() {
          return h(
            'div',
            { style: 'padding: 20px; color: red;' },
            `Failed to load component: ${name || 'Unknown'}. Error: ${loadError?.message || 'Component not found'}`
          );
        }
      });
    }

    return Promise.resolve(
      defineComponent({
        name,
        render() {
          return h(component as Component);
        }
      })
    );
  };
}
