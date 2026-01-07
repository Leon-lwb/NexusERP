/**
* 显示消息提示框
* @param content 提示的标题
*/
export function toast(content) {
  uni.showToast({
    icon: 'none',
    title: content
  })
}

/**
 * 格式化日期
 * @param {Date} date 日期对象
 * @param {String} fmt 格式字符串
 * @returns {String} 格式化后的日期字符串
 */
export function formatDate(date, fmt) {
  if (typeof date === 'string') {
    date = new Date(date);
  }
  
  if (!fmt) fmt = 'yyyy-MM-dd';
  
  const o = {
    'M+': date.getMonth() + 1,
    'd+': date.getDate(),
    'h+': date.getHours(),
    'm+': date.getMinutes(),
    's+': date.getSeconds(),
    'q+': Math.floor((date.getMonth() + 3) / 3),
    'S': date.getMilliseconds()
  };
  
  if (/(y+)/.test(fmt)) {
    fmt = fmt.replace(RegExp.$1, (date.getFullYear() + '').substr(4 - RegExp.$1.length));
  }
  
  for (let k in o) {
    if (new RegExp('(' + k + ')').test(fmt)) {
      fmt = fmt.replace(RegExp.$1, (RegExp.$1.length === 1) ? (o[k]) : (('00' + o[k]).substr(('' + o[k]).length)));
    }
  }
  
  return fmt;
}

/**
 * 格式化时间
 * @param {Date} date 日期对象
 * @returns {String} 格式化后的时间字符串 HH:mm
 */
export function formatTime(date) {
  if (typeof date === 'string') {
    date = new Date(date);
  }
  
  const hours = date.getHours().toString().padStart(2, '0');
  const minutes = date.getMinutes().toString().padStart(2, '0');
  
  return `${hours}:${minutes}`;
}

/**
 * 显示确认对话框
 * @param {String} content 对话框内容
 * @returns {Promise} Promise对象
 */
export function showConfirm(content) {
  return new Promise((resolve) => {
    uni.showModal({
      title: '提示',
      content: content,
      success: function(res) {
        resolve(res);
      }
    });
  });
}

/**
* 参数处理
* @param params 参数
*/
export function tansParams(params) {
  let result = ''
  for (const propName of Object.keys(params)) {
    const value = params[propName]
    var part = encodeURIComponent(propName) + "="
    if (value !== null && value !== "" && typeof (value) !== "undefined") {
      if (typeof value === 'object') {
        for (const key of Object.keys(value)) {
          if (value[key] !== null && value[key] !== "" && typeof (value[key]) !== 'undefined') {
            let params = propName + '[' + key + ']'
            var subPart = encodeURIComponent(params) + "="
            result += subPart + encodeURIComponent(value[key]) + "&"
          }
        }
      } else {
        result += part + encodeURIComponent(value) + "&"
      }
    }
  }
  return result
}