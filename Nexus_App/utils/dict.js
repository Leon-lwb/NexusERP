import useDictStore from "@/store/modules/dict"
import { getDicts } from "@/api/system/dict/data"
import { ref, toRefs } from "vue"

/**
 * 获取字典数据
 */
export function useDict(...args) {
  const res = ref({})
  return (() => {
    args.forEach((dictType, index) => {
      res.value[dictType] = []
      const dicts = useDictStore().getDict(dictType)
      if (dicts) {
        res.value[dictType] = dicts
      } else {
        getDicts(dictType).then((resp) => {
          res.value[dictType] = resp.data.map((p) => ({ label: p.dictLabel, value: p.dictValue, elTagType: p.listClass, elTagClass: p.cssClass }))
          useDictStore().setDict(dictType, res.value[dictType])
        })
      }
    })
    return toRefs(res.value)
  })()
}

export async function getSelectDict(dict) {
    try {
        const res = await getDicts(dict); // 等待异步请求完成
        const tempDict = res.data;
        if (tempDict.length > 0) {
            const newDicts = tempDict.map((item) => {
                return {
                    text: item.dictLabel,
                    value: item.dictValue
                };
            });
            return newDicts; // 正确返回处理后的数组
        }
        return []; // 如果数组为空，返回空数组
    } catch (error) {
        console.error('Error fetching dicts:', error);
        return []; // 处理异常情况下的返回值
    }
}