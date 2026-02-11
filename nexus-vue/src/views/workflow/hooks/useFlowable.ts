import { listCustomForm } from "@/api/workflow/customForm";

export const useFlowable = () => {
  const customformList = ref<any>([]);
  const formQueryParams = reactive<any>({
    pageNum: 1,
    pageSize: 1000
  });

  const allFormComponent = computed(() => {
    return customformList.value;
  })
  /* 挂载自定义业务表单列表 */
   const ListCustomForForm = async () => {
    listCustomForm(formQueryParams).then(res => {
      let  cfList = res.rows;
        cfList.forEach((item, index) => {
          let cms = {
              text:item.flowName,
              routeName:item.routeName,
              component: markRaw(defineAsyncComponent( () => import(/* @vite-ignore */`../../${item.routeName}.vue`))),
              businessTable:'wf_demo'
          }
          customformList.value.push(cms);
        })
    })
  }
  const getFormComponent = (routeName) => {
    return allFormComponent.value.find((item) => item.routeName === routeName) || {}
  }

  onMounted(() => {
    ListCustomForForm();
  });

  return {
    allFormComponent,
    getFormComponent
  }
}
