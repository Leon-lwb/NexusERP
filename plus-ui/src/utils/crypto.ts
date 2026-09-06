import CryptoJS from 'crypto-js';

/**
 * 随机生成32位的字符串
 * @returns {string}
 */
const generateRandomString = () => {
  const characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let result = '';
  const charactersLength = characters.length;
  for (let i = 0; i < 32; i++) {
    result += characters.charAt(Math.floor(Math.random() * charactersLength));
  }
  return result;
};

/**
 * 随机生成aes 密钥
 * @returns {string}
 */
export const generateAesKey = () => {
  return CryptoJS.enc.Utf8.parse(generateRandomString());
};

/**
 * 加密base64
 * @returns {string}
 */
export const encryptBase64 = (str: CryptoJS.lib.WordArray) => {
  return CryptoJS.enc.Base64.stringify(str);
};

/**
 * 解密base64
 */
export const decryptBase64 = (str: string) => {
  return CryptoJS.enc.Base64.parse(str);
};

/**
 * 使用密钥对数据进行加密
 * @param message
 * @param aesKey
 * @returns {string}
 */
export const encryptWithAes = (message: string, aesKey: CryptoJS.lib.WordArray) => {
  const encrypted = CryptoJS.AES.encrypt(message, aesKey, {
    mode: CryptoJS.mode.ECB,
    padding: CryptoJS.pad.Pkcs7
  });
  return encrypted.toString();
};

/**
 * 使用密钥对数据进行解密
 * @param message
 * @param aesKey
 * @returns {string}
 */
export const decryptWithAes = (message: string, aesKey: CryptoJS.lib.WordArray) => {
  const decrypted = CryptoJS.AES.decrypt(message, aesKey, {
    mode: CryptoJS.mode.ECB,
    padding: CryptoJS.pad.Pkcs7
  });
  return decrypted.toString(CryptoJS.enc.Utf8);
};

/** 记住密码本地加密口令（仅防 localStorage 明文落盘；客户端侧密钥无法抵御已获页面 JS 执行权的攻击者，不能替代服务端会话） */
const REMEMBER_PASSPHRASE = 'NexusERP@remember-me-v1';

/**
 * 本地存储敏感字段加密（记住密码）：passphrase 模式 AES，输出 OpenSSL Salted base64
 * @param message 明文
 * @returns 密文
 */
export const encryptRemember = (message: string): string => {
  return CryptoJS.AES.encrypt(message, REMEMBER_PASSPHRASE).toString();
};

/**
 * 本地存储敏感字段解密（记住密码）
 * @param cipherText 密文
 * @returns 明文；解密失败（历史明文残留/密文损坏）返回空串
 */
export const decryptRemember = (cipherText: string): string => {
  try {
    const plain = CryptoJS.AES.decrypt(cipherText, REMEMBER_PASSPHRASE).toString(CryptoJS.enc.Utf8);
    return plain || '';
  } catch {
    return '';
  }
};
