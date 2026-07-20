import AutoImport from 'unplugin-auto-import/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';

export default (path: any) => {
  return AutoImport({
    // 自动导入 Vue 相关函数
    imports: ['vue', 'vue-router', '@vueuse/core', 'pinia'],
    eslintrc: {
      enabled: true,
      filepath: './.eslintrc-auto-import.json',
      globalsPropValue: true
    },
    resolvers: [
      // 自动导入 Element Plus 相关函数ElMessage, ElMessageBox...(样式已由 main.ts 全量引入,关闭按需样式注入)
      ElementPlusResolver({ importStyle: false })
    ],
    vueTemplate: true, // 是否在 vue 模板中自动导入
    dts: path.resolve(path.resolve(__dirname, '../../src'), 'types', 'auto-imports.d.ts')
  });
};
