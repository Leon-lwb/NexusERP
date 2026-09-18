import { defineConfig, loadEnv } from 'vite';
import createPlugins from './vite/plugins';
import autoprefixer from 'autoprefixer'; // css自动添加兼容性前缀
import path from 'path';

/** 从模块 id 中取出所属 npm 包名（正确处理 scoped 包与嵌套 node_modules） */
function getPackageName(id: string): string | undefined {
  const normalized = id.replace(/\\/g, '/');
  const marker = 'node_modules/';
  const idx = normalized.lastIndexOf(marker);
  if (idx === -1) return undefined;
  const parts = normalized.slice(idx + marker.length).split('/');
  if (!parts[0]) return undefined;
  return parts[0].startsWith('@') ? `${parts[0]}/${parts[1] ?? ''}` : parts[0];
}

/**
 * vendor 分包表：包名 → chunk 名。
 * 只列体积大、值得独立缓存的库；其余第三方库不干预，跟随各自的路由级 chunk 按需加载。
 */
const VENDOR_CHUNKS: Record<string, string> = {
  vue: 'vendor-vue',
  'vue-router': 'vendor-vue',
  pinia: 'vendor-vue',
  '@vueuse/core': 'vendor-vue',
  'element-plus': 'vendor-element',
  '@element-plus/icons-vue': 'vendor-element',
  echarts: 'vendor-echarts',
  zrender: 'vendor-echarts',
  'bpmn-js': 'vendor-bpmn',
  'bpmn-js-token-simulation': 'vendor-bpmn',
  'diagram-js': 'vendor-bpmn',
  'diagram-js-minimap': 'vendor-bpmn',
  three: 'vendor-three',
  'pdfjs-dist': 'vendor-pdf',
  marked: 'vendor-markdown',
  'highlight.js': 'vendor-markdown',
  '@highlightjs/vue-plugin': 'vendor-markdown',
  axios: 'vendor-utils',
  'crypto-js': 'vendor-utils',
  jsencrypt: 'vendor-utils',
  'file-saver': 'vendor-utils',
  nprogress: 'vendor-utils',
  'await-to-js': 'vendor-utils',
  'image-conversion': 'vendor-utils'
};

export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd());
  return {
    // 部署生产环境和开发环境下的URL。
    // 默认情况下，vite 会假设你的应用是被部署在一个域名的根路径上
    // 例如 https://www.ruoyi.vip/。如果应用被部署在一个子路径上，你就需要用这个选项指定这个子路径。例如，如果你的应用被部署在 https://www.ruoyi.vip/admin/，则设置 baseUrl 为 /admin/。
    base: env.VITE_APP_CONTEXT_PATH,
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src')
      },
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
    },
    // https://cn.vitejs.dev/config/#resolve-extensions
    plugins: createPlugins(env, command === 'build'),
    server: {
      host: true,
      port: Number(env.VITE_APP_PORT),
      open: true,
      proxy: {
        [env.VITE_APP_BASE_API]: {
          target: env.VITE_APP_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
          ws: true,
          rewrite: (path) => path.replace(new RegExp('^' + env.VITE_APP_BASE_API), '')
        }
      }
    },
    css: {
      preprocessorOptions: {
        scss: {
          // additionalData: '@use "@/assets/styles/variables.module.scss as *";'
          // javascriptEnabled: true
        }
      },
      postcss: {
        plugins: [
          // 浏览器兼容性
          autoprefixer(),
          {
            postcssPlugin: 'internal:charset-removal',
            AtRule: {
              charset: (atRule) => {
                atRule.remove();
              }
            }
          }
        ]
      }
    },
    // 预编译
    optimizeDeps: {
      include: ['vue', 'vue-router', 'pinia', 'axios', '@vueuse/core', 'echarts', '@vueup/vue-quill', 'image-conversion', 'three']
    },
    build: {
      chunkSizeWarningLimit: 1000,
      rollupOptions: {
        output: {
          /**
           * 分包策略：按 node_modules 下的包名动态归组，配合路由懒加载按需加载。
           *
           * <p>此前用对象形式手工列举包清单，有两个问题：一是漏列的重依赖会落进引用它的
           * 业务 chunk（pdfjs-dist / marked / highlight.js 即如此）；二是清单容易与实际依赖
           * 脱节（例如把已无人 import 的 bpmn-js-task-resize 写进了 vendor-bpmn）。</p>
           *
           * <p>函数形式按包名查表，新增依赖无需再维护清单；未命中的包保持 Rollup 默认行为，
           * 跟随业务 chunk 按需加载，避免 chunk 数量膨胀。</p>
           */
          manualChunks(id: string) {
            const pkg = getPackageName(id);
            return pkg ? VENDOR_CHUNKS[pkg] : undefined;
          }
        }
      }
    }
  };
});
