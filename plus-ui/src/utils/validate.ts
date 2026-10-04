/**
 * 路径匹配器
 * @param {string} pattern
 * @param {string} path
 * @returns {Boolean}
 */
export function isPathMatch(pattern: string, path: string) {
  const regexPattern = pattern
    .replace(/\//g, '\\/')
    .replace(/\*\*/g, '__DOUBLE_STAR__')
    .replace(/\*/g, '[^\\/]*')
    .replace(/__DOUBLE_STAR__/g, '.*');
  const regex = new RegExp(`^${regexPattern}$`);
  return regex.test(path);
}

/**
 * 判断url是否是http或https
 * @returns {Boolean}
 * @param url
 */
export const isHttp = (url: string): boolean => {
  return url.indexOf('http://') !== -1 || url.indexOf('https://') !== -1;
};

/**
 * 判断path是否为外链
 * @param {string} path
 * @returns {Boolean}
 */
export const isExternal = (path: string) => {
  return /^(https?:|mailto:|tel:)/.test(path);
};

/**
 * @param {string} url
 * @returns {Boolean}
 */
export const validURL = (url: string) => {
  const reg =
    /^(https?|ftp):\/\/([a-zA-Z0-9.-]+(:[a-zA-Z0-9.&%$-]+)*@)*((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]?)(\.(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}|([a-zA-Z0-9-]+\.)*[a-zA-Z0-9-]+\.(com|edu|gov|int|mil|net|org|biz|arpa|info|name|pro|aero|coop|museum|[a-zA-Z]{2}))(:[0-9]+)*(\/($|[a-zA-Z0-9.,?'\\+&%$#=~_-]+))*$/;
  return reg.test(url);
};

/**
 * @param {string} str
 * @returns {Boolean}
 */
export const validLowerCase = (str: string) => {
  const reg = /^[a-z]+$/;
  return reg.test(str);
};

/**
 * @param {string} str
 * @returns {Boolean}
 */
export const validUpperCase = (str: string) => {
  const reg = /^[A-Z]+$/;
  return reg.test(str);
};

/**
 * @param {string} str
 * @returns {Boolean}
 */
export const validAlphabets = (str: string) => {
  const reg = /^[A-Za-z]+$/;
  return reg.test(str);
};

/**
 * @param {string} email
 * @returns {Boolean}
 */
export const validEmail = (email: string) => {
  const reg =
    /^(([^<>()\]\\.,;:\s@"]+(\.[^<>()\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
  return reg.test(email);
};

/**
 * @param {string} str
 * @returns {Boolean}
 */
export const isString = (str: any) => {
  return typeof str === 'string' || str instanceof String;
};

/**
 * @param {Array} arg
 * @returns {Boolean}
 */
export const isArray = (arg: string | string[]) => {
  if (typeof Array.isArray === 'undefined') {
    return Object.prototype.toString.call(arg) === '[object Array]';
  }
  return Array.isArray(arg);
};

/**
 * 菜单权限格式：可留空，非空值最多 100 字符；至少三段，首段不允许通配符，后续段保留既有 * 支持。
 * 仅校验格式，不参与授权匹配；严格匹配结尾，避免 $ 接受末尾换行。
 */
export const validPermission = (permission?: string | null): boolean => {
  return permission == null || permission === '' || (permission.length <= 100 && /^[a-zA-Z0-9_]+(?::[a-zA-Z0-9_*]+){2,}(?![\s\S])/.test(permission));
};
