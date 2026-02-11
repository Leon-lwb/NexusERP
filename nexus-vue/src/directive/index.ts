import hasPermi from './permission/hasPermi'
import hasRole from './permission/hasRole'
import copyText from './common/copyText'

export default {
  install(app: any) {
    app.directive('hasPermi', hasPermi)
    app.directive('hasRole', hasRole)
    app.directive('copyText', copyText)
  }
}
