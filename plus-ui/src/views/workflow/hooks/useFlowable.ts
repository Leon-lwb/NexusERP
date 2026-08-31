import { listCustomForm } from '@/api/workflow/custom-form';

export const useFlowable = () => {
  const customformList = ref<any>([]);
  const formQueryParams = reactive<any>({
    pageNum: 1,
    pageSize: 1000
  });

  // 生产环境不能用 `import(/* @vite-ignore */ '.../*.vue')` 去按字符串加载 .vue 文件（dist 里不存在 .vue 源码）。
  // 用 glob 让 Vite 在构建期把可能用到的表单组件都纳入打包，然后运行时按 key 取 loader。
  const viewModules = import.meta.glob('../../**/*.vue');
  const resolveFormComponent = (routeName: string) => {
    if (!routeName) return undefined;
    // routeName 期望形如：workflow/demo/edit（兼容 /workflow/demo/edit、views/workflow/demo/edit、带 .vue 后缀等）
    let name = String(routeName).trim();
    name = name.replace(/^[#/]+/, '').replace(/^\/+/, '');
    name = name.replace(/\.vue$/i, '');
    name = name.replace(/^(src\/)?views\//i, '');
    const key = `../../${name}.vue`;
    const loader = (viewModules as any)[key] as undefined | (() => Promise<any>);
    if (!loader) {
      console.warn('[useFlowable] 未找到表单组件，请检查 wf_custom_form.route_name 配置是否为 views 下的相对路径:', {
        routeName,
        expectedKey: key
      });
      return undefined;
    }
    return markRaw(defineAsyncComponent(() => loader().then((m: any) => m?.default || m)));
  };

  const allFormComponent = computed(() => {
    return customformList.value;
  });
  /* 挂载自定义业务表单列表 */
  const ListCustomForForm = async () => {
    listCustomForm(formQueryParams).then((res) => {
      const cfList = res.rows;
      cfList.forEach((item, index) => {
        const cms = {
          text: item.flowName,
          routeName: item.routeName,
          component: resolveFormComponent(item.routeName),
          businessTable: 'wf_demo'
        };
        customformList.value.push(cms);
      });
    });
  };
  const getFormComponent = (routeName) => {
    return allFormComponent.value.find((item) => item.routeName === routeName) || {};
  };

  onMounted(() => {
    ListCustomForForm();
  });

  return {
    allFormComponent,
    getFormComponent
  };
};
