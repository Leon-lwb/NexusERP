import auth from './auth'
import cache from './cache'
import modal from './modal'
import download from './download'
import tab from './tab'

export default {
  install(app: any) {
    app.config.globalProperties.$auth = auth
    app.config.globalProperties.$cache = cache
    app.config.globalProperties.$modal = modal
    app.config.globalProperties.$download = download
    app.config.globalProperties.$tab = tab
  }
}
