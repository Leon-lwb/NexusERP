import { ElMessage, ElMessageBox, ElNotification, ElLoading } from 'element-plus'

let loadingInstance: any

export default {
  msg(content: string) {
    ElMessage.info(content)
  },
  msgError(content: string) {
    ElMessage.error(content)
  },
  msgSuccess(content: string) {
    ElMessage.success(content)
  },
  msgWarning(content: string) {
    ElMessage.warning(content)
  },
  alert(content: string) {
    ElMessageBox.alert(content, "系统提示")
  },
  alertError(content: string) {
    ElMessageBox.alert(content, "系统提示", { type: 'error' })
  },
  alertSuccess(content: string) {
    ElMessageBox.alert(content, "系统提示", { type: 'success' })
  },
  alertWarning(content: string) {
    ElMessageBox.alert(content, "系统提示", { type: 'warning' })
  },
  notify(content: string) {
    ElNotification.info(content)
  },
  notifyError(content: string) {
    ElNotification.error(content)
  },
  notifySuccess(content: string) {
    ElNotification.success(content)
  },
  notifyWarning(content: string) {
    ElNotification.warning(content)
  },
  confirm(content: string) {
    return ElMessageBox.confirm(content, "系统提示", {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: "warning",
    })
  },
  prompt(content: string) {
    return ElMessageBox.prompt(content, "系统提示", {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: "warning",
    })
  },
  loading(content: string) {
    loadingInstance = ElLoading.service({
      lock: true,
      text: content,
      background: "rgba(0, 0, 0, 0.7)",
    })
  },
  closeLoading() {
    loadingInstance.close()
  }
}
