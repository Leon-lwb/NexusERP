/**
 * 地理位置工具函数
 * 用于处理地理位置计算和验证
 */

/**
 * 计算两个坐标点之间的距离（使用Haversine公式）
 * @param {number} lat1 - 纬度1
 * @param {number} lon1 - 经度1
 * @param {number} lat2 - 纬度2
 * @param {number} lon2 - 经度2
 * @returns {number} 距离（米）
 */
export function calculateDistance(lat1, lon1, lat2, lon2) {
  const R = 6371000; // 地球半径（米）
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

/**
 * 将角度转换为弧度
 * @param {number} degrees - 角度
 * @returns {number} 弧度
 */
function toRad(degrees) {
  return degrees * Math.PI / 180;
}

/**
 * 验证位置是否在打卡范围内
 * @param {Object} currentLocation - 当前位置
 * @param {number} currentLocation.latitude - 纬度
 * @param {number} currentLocation.longitude - 经度
 * @param {Object} punchRule - 打卡规则
 * @param {number} punchRule.location_lat - 打卡中心纬度
 * @param {number} punchRule.location_lng - 打卡中心经度
 * @param {number} punchRule.location_radius - 打卡半径（米）
 * @returns {Object} 验证结果
 */
export function validatePunchLocation(currentLocation, punchRule) {
  if (!currentLocation || !punchRule || !punchRule.location_lat || !punchRule.location_lng) {
    return {
      isValid: false,
      distance: null,
      message: '缺少位置信息或打卡规则'
    };
  }

  const distance = calculateDistance(
    currentLocation.latitude,
    currentLocation.longitude,
    punchRule.location_lat,
    punchRule.location_lng
  );

  const isValid = distance <= (punchRule.location_radius || 100);
  
  return {
    isValid,
    distance: Math.round(distance),
    radius: punchRule.location_radius || 100,
    message: isValid 
      ? `在打卡范围内（距离${Math.round(distance)}米）`
      : `超出打卡范围（距离${Math.round(distance)}米，允许${punchRule.location_radius || 100}米）`
  };
}

/**
 * 获取位置描述信息（使用逆地理编码）
 * @param {Object} location - 位置信息
 * @returns {Promise<string>} 位置描述
 */
export async function getLocationDescription(location) {
  if (!location || !location.latitude || !location.longitude) {
    return '未知位置';
  }

  try {
    // 使用uni-app的逆地理编码
    const result = await new Promise((resolve, reject) => {
      uni.getLocation({
        type: 'wgs84',
        success: (res) => {
          // 使用腾讯地图逆地理编码API（需要申请key）
          // 这里使用简单的坐标转换，实际项目中应该使用逆地理编码服务
          resolve({
            address: `纬度: ${res.latitude.toFixed(6)}, 经度: ${res.longitude.toFixed(6)}`,
            name: '当前位置'
          });
        },
        fail: reject
      });
    });
    
    return result.address || result.name || '未知位置';
  } catch (error) {
    console.error('获取位置描述失败:', error);
    return '位置获取失败';
  }
}

/**
 * 获取当前位置（带权限检查）
 * @returns {Promise<Object>} 位置信息
 */
export async function getCurrentLocation() {
  return new Promise((resolve, reject) => {
    // 首先尝试直接获取位置
    uni.getLocation({
      type: 'wgs84',
      altitude: true,
      success: (res) => {
        resolve({
          latitude: res.latitude,
          longitude: res.longitude,
          accuracy: res.accuracy || 0,
          altitude: res.altitude || 0,
          speed: res.speed || 0,
          timestamp: res.timestamp || Date.now()
        });
      },
      fail: (err) => {
        console.error('获取位置失败:', err);
        
        // 根据错误类型提供不同的处理
        if (err.errMsg && err.errMsg.includes('auth deny') || err.errMsg.includes('permission')) {
          // 权限被拒绝，引导用户开启
          uni.showModal({
            title: '定位权限未开启',
            content: '需要获取您的位置信息以进行打卡，请前往设置开启定位权限',
            confirmText: '去设置',
            success: (modalRes) => {
              if (modalRes.confirm) {
                // 尝试打开设置页面
                if (typeof uni.openSetting === 'function') {
                  uni.openSetting({
                    success: (settingRes) => {
                      if (settingRes.authSetting && settingRes.authSetting['scope.userLocation']) {
                        // 用户已授权，重新获取位置
                        getCurrentLocation().then(resolve).catch(reject);
                      } else {
                        reject(new Error('用户拒绝授权定位权限'));
                      }
                    },
                    fail: () => {
                      reject(new Error('打开设置失败'));
                    }
                  });
                } else {
                  // 如果openSetting不可用，显示提示信息
                  uni.showModal({
                    title: '定位权限设置',
                    content: '请在手机系统设置中开启定位权限，然后返回应用重试',
                    showCancel: false,
                    confirmText: '知道了',
                    success: () => {
                      reject(new Error('请在系统设置中开启定位权限'));
                    }
                  });
                }
              } else {
                reject(new Error('用户取消授权'));
              }
            }
          });
        } else {
          // 其他错误（如定位服务未开启、网络问题等）
          reject(new Error(`获取位置失败: ${err.errMsg || '未知错误'}`));
        }
      }
    });
  });
}

/**
 * 格式化距离显示
 * @param {number} distance - 距离（米）
 * @returns {string} 格式化后的距离
 */
export function formatDistance(distance) {
  if (distance < 1000) {
    return `${Math.round(distance)}米`;
  } else {
    return `${(distance / 1000).toFixed(1)}公里`;
  }
}

/**
 * 检查是否在允许的打卡时间内
 * @param {Date} currentTime - 当前时间
 * @param {Object} punchRule - 打卡规则
 * @param {string} punchType - 打卡类型: 'clock_in' 或 'clock_out'
 * @returns {Object} 时间验证结果
 */
export function validatePunchTime(currentTime, punchRule, punchType) {
  if (!punchRule) {
    return {
      isValid: true,
      message: '无时间限制'
    };
  }

  const now = new Date(currentTime);
  const timeStr = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`;
  
  let startTime, endTime;
  
  if (punchType === 'clock_in') {
    startTime = punchRule.allow_clock_in_start;
    endTime = punchRule.allow_clock_in_end;
  } else if (punchType === 'clock_out') {
    startTime = punchRule.allow_clock_out_start;
    endTime = punchRule.allow_clock_out_end;
  } else {
    return {
      isValid: false,
      message: '无效的打卡类型'
    };
  }

  if (!startTime || !endTime) {
    return {
      isValid: true,
      message: '无时间限制'
    };
  }

  const currentMinutes = now.getHours() * 60 + now.getMinutes();
  const startMinutes = timeToMinutes(startTime);
  const endMinutes = timeToMinutes(endTime);

  const isValid = currentMinutes >= startMinutes && currentMinutes <= endMinutes;
  
  return {
    isValid,
    currentTime: timeStr,
    allowedStart: startTime,
    allowedEnd: endTime,
    message: isValid 
      ? `在允许打卡时间内（${startTime} - ${endTime}）`
      : `不在允许打卡时间内（当前${timeStr}，允许${startTime} - ${endTime}）`
  };
}

/**
 * 将时间字符串转换为分钟数
 * @param {string} timeStr - 时间字符串 (HH:mm)
 * @returns {number} 分钟数
 */
function timeToMinutes(timeStr) {
  if (!timeStr) return 0;
  const [hours, minutes] = timeStr.split(':').map(Number);
  return hours * 60 + minutes;
}

/**
 * 计算距离允许打卡时间的倒计时
 * @param {Date} currentTime - 当前时间
 * @param {Object} punchRule - 打卡规则
 * @param {string} punchType - 打卡类型: 'clock_in' 或 'clock_out'
 * @returns {Object} 倒计时信息
 */
export function getPunchCountdown(currentTime, punchRule, punchType) {
  if (!punchRule) {
    return {
      hasCountdown: false,
      message: '无时间限制'
    };
  }

  const now = new Date(currentTime);
  let targetTime;
  
  if (punchType === 'clock_in') {
    targetTime = punchRule.work_start_time;
  } else if (punchType === 'clock_out') {
    targetTime = punchRule.work_end_time;
  } else {
    return {
      hasCountdown: false,
      message: '无效的打卡类型'
    };
  }

  if (!targetTime) {
    return {
      hasCountdown: false,
      message: '无目标时间'
    };
  }

  const [targetHours, targetMinutes] = targetTime.split(':').map(Number);
  const targetDate = new Date(now);
  targetDate.setHours(targetHours, targetMinutes, 0, 0);

  // 如果目标时间已经过去，则计算明天的目标时间
  if (targetDate < now) {
    targetDate.setDate(targetDate.getDate() + 1);
  }

  const diffMs = targetDate - now;
  
  if (diffMs <= 0) {
    return {
      hasCountdown: false,
      message: '已到打卡时间'
    };
  }

  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  const diffMinutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
  const diffSeconds = Math.floor((diffMs % (1000 * 60)) / 1000);

  return {
    hasCountdown: true,
    hours: diffHours,
    minutes: diffMinutes,
    seconds: diffSeconds,
    formatted: `${diffHours.toString().padStart(2, '0')}:${diffMinutes.toString().padStart(2, '0')}:${diffSeconds.toString().padStart(2, '0')}`,
    targetTime: targetTime,
    message: `距离${punchType === 'clock_in' ? '上班' : '下班'}还有 ${diffHours}小时${diffMinutes}分钟`
  };
}

/**
 * 获取设备信息（用于Wi-Fi/蓝牙打卡）
 * @returns {Promise<Object>} 设备信息
 */
export async function getDeviceInfo() {
  const info = {
    platform: uni.getSystemInfoSync().platform,
    model: uni.getSystemInfoSync().model,
    system: uni.getSystemInfoSync().system,
    wifiEnabled: false,
    bluetoothEnabled: false
  };

  // 检查Wi-Fi状态（需要特定API）
  try {
    const wifiInfo = await new Promise((resolve, reject) => {
      uni.getConnectedWifi({
        success: resolve,
        fail: reject
      });
    });
    info.wifiEnabled = true;
    info.wifiSSID = wifiInfo.wifi.SSID;
    info.wifiBSSID = wifiInfo.wifi.BSSID;
  } catch (error) {
    info.wifiEnabled = false;
  }

  // 检查蓝牙状态（需要特定API）
  try {
    const bluetoothInfo = await new Promise((resolve, reject) => {
      uni.openBluetoothAdapter({
        success: resolve,
        fail: reject
      });
    });
    info.bluetoothEnabled = true;
  } catch (error) {
    info.bluetoothEnabled = false;
  }

  return info;
}

/**
 * 检查定位权限状态（兼容不同平台）
 * @returns {Promise<boolean>} 是否有定位权限
 */
export async function checkLocationPermission() {
  return new Promise((resolve) => {
    // 首先尝试直接获取位置来检查权限
    uni.getLocation({
      type: 'wgs84',
      success: () => {
        resolve(true);
      },
      fail: (err) => {
        if (err.errMsg && (err.errMsg.includes('auth deny') || err.errMsg.includes('permission'))) {
          resolve(false);
        } else {
          // 其他错误（如定位服务未开启），也视为无权限
          resolve(false);
        }
      }
    });
  });
}

/**
 * 请求定位权限（兼容不同平台）
 * @returns {Promise<boolean>} 是否授权成功
 */
export async function requestLocationPermission() {
  return new Promise((resolve) => {
    // 尝试直接获取位置，如果失败则引导用户
    uni.getLocation({
      type: 'wgs84',
      success: () => {
        resolve(true);
      },
      fail: (err) => {
        if (err.errMsg && (err.errMsg.includes('auth deny') || err.errMsg.includes('permission'))) {
          // 权限被拒绝，引导用户开启
          uni.showModal({
            title: '定位权限未开启',
            content: '需要获取您的位置信息以进行打卡，请前往设置开启定位权限',
            confirmText: '去设置',
            success: (modalRes) => {
              if (modalRes.confirm) {
                // 尝试打开设置页面
                if (typeof uni.openSetting === 'function') {
                  uni.openSetting({
                    success: (settingRes) => {
                      if (settingRes.authSetting && settingRes.authSetting['scope.userLocation']) {
                        resolve(true);
                      } else {
                        resolve(false);
                      }
                    },
                    fail: () => {
                      resolve(false);
                    }
                  });
                } else {
                  // 如果openSetting不可用，直接返回false
                  resolve(false);
                }
              } else {
                resolve(false);
              }
            }
          });
        } else {
          // 其他错误，无法获取权限
          resolve(false);
        }
      }
    });
  });
}

export default {
  calculateDistance,
  validatePunchLocation,
  getLocationDescription,
  getCurrentLocation,
  formatDistance,
  validatePunchTime,
  getPunchCountdown,
  getDeviceInfo,
  checkLocationPermission,
  requestLocationPermission
};