import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';
import IconsResolver from 'unplugin-icons/resolver';
import withoutUnusedSourcemaps from './without-unused-sourcemaps';

export default (path: any) => {
  return withoutUnusedSourcemaps(
    Components({
      resolvers: [
        /** 自动导入 Element Plus 组件（样式已由 main.ts 全量引入，关闭按需样式注入，避免开发模式下触发 Vite 重新预构建并导致整页重新加载）。 */
        ElementPlusResolver({ importStyle: false }),
        /** 自动注册图标组件 */
        IconsResolver({
          enabledCollections: ['ep']
        })
      ],
      dts: path.resolve(path.resolve(__dirname, '../../src'), 'types', 'components.d.ts')
    })
  );
};
