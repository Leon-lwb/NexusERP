import useTagsViewStore from '@/store/modules/tagsView'
import router from '@/router'

export default {
  refreshPage(obj?: any) {
    const { path, query, matched } = router.currentRoute.value
    if (obj === undefined) {
      matched.forEach((m: any) => {
        if (m.components && m.components.default && m.components.default.name) {
          if (!['Layout', 'ParentView'].includes(m.components.default.name)) {
            obj = { name: m.components.default.name, path: path, query: query }
          }
        }
      })
    }
    return useTagsViewStore().delCachedView(obj).then(() => {
      const { path, query } = obj
      router.replace({
        path: '/redirect' + path,
        query: query
      })
    })
  },
  closeOpenPage(obj?: any) {
    useTagsViewStore().delView(router.currentRoute.value)
    if (obj !== undefined) {
      return router.push(obj)
    }
  },
  closePage(obj?: any) {
    if (obj === undefined) {
      return useTagsViewStore().delView(router.currentRoute.value).then(({ visitedViews }) => {
        const latestView = visitedViews.slice(-1)[0]
        if (latestView) {
          return router.push(latestView.fullPath)
        }
        return router.push('/')
      })
    }
    return useTagsViewStore().delView(obj)
  },
  closeAllPage() {
    return useTagsViewStore().delAllViews()
  },
  closeLeftPage(obj?: any) {
    return useTagsViewStore().delLeftTags(obj || router.currentRoute.value)
  },
  closeRightPage(obj?: any) {
    return useTagsViewStore().delRightTags(obj || router.currentRoute.value)
  },
  closeOtherPage(obj?: any) {
    return useTagsViewStore().delOthersViews(obj || router.currentRoute.value)
  },
  openPage(title: string, url: string, params?: any) {
    const obj = { path: url, meta: { title: title } }
    useTagsViewStore().addView(obj)
    return router.push({ path: url, query: params })
  },
  updatePage(obj: any) {
    return useTagsViewStore().updateVisitedView(obj)
  }
}
