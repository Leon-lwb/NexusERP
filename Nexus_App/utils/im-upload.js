import config from '@/config'
import { getToken } from '@/utils/auth'

const baseUrl = config.baseUrl

export function uploadImage(filePath) {
  return new Promise((resolve, reject) => {
    const token = getToken()
    
    uni.uploadFile({
      url: baseUrl + '/system/user/profile/avatar',
      filePath: filePath,
      name: 'avatarfile',
      header: {
        'Authorization': 'Bearer ' + token
      },
      success: (res) => {
        if (res.statusCode === 200) {
          try {
            const data = JSON.parse(res.data)
            if (data.code === 200) {
              resolve(data)
            } else {
              reject(new Error(data.msg || '上传失败'))
            }
          } catch (error) {
            reject(error)
          }
        } else {
          reject(new Error('上传失败'))
        }
      },
      fail: (error) => {
        reject(error)
      }
    })
  })
}

export function uploadFile(filePath, name = 'file') {
  return new Promise((resolve, reject) => {
    const token = getToken()
    
    uni.uploadFile({
      url: baseUrl + '/common/upload',
      filePath: filePath,
      name: name,
      header: {
        'Authorization': 'Bearer ' + token
      },
      success: (res) => {
        if (res.statusCode === 200) {
          try {
            const data = JSON.parse(res.data)
            if (data.code === 200) {
              resolve(data)
            } else {
              reject(new Error(data.msg || '上传失败'))
            }
          } catch (error) {
            reject(error)
          }
        } else {
          reject(new Error('上传失败'))
        }
      },
      fail: (error) => {
        reject(error)
      }
    })
  })
}

export function chooseAndUploadImage() {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: async (res) => {
        const tempFilePath = res.tempFilePaths[0]
        
        try {
          uni.showLoading({
            title: '上传中...',
            mask: true
          })
          
          const uploadRes = await uploadImage(tempFilePath)
          
          uni.hideLoading()
          
          resolve({
            localPath: tempFilePath,
            remotePath: uploadRes.fileName || uploadRes.url,
            url: uploadRes.url
          })
        } catch (error) {
          uni.hideLoading()
          reject(error)
        }
      },
      fail: (error) => {
        reject(error)
      }
    })
  })
}

export function chooseAndUploadFile() {
  return new Promise((resolve, reject) => {
    uni.chooseFile({
      count: 1,
      type: 'file',
      success: async (res) => {
        const tempFilePath = res.tempFilePaths[0]
        const fileName = res.tempFiles[0].name
        
        try {
          uni.showLoading({
            title: '上传中...',
            mask: true
          })
          
          const uploadRes = await uploadFile(tempFilePath)
          
          uni.hideLoading()
          
          resolve({
            localPath: tempFilePath,
            remotePath: uploadRes.fileName || uploadRes.url,
            url: uploadRes.url,
            fileName: fileName,
            fileSize: res.tempFiles[0].size
          })
        } catch (error) {
          uni.hideLoading()
          reject(error)
        }
      },
      fail: (error) => {
        reject(error)
      }
    })
  })
}

export function downloadFile(url) {
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: url,
      success: (res) => {
        if (res.statusCode === 200) {
          resolve(res.tempFilePath)
        } else {
          reject(new Error('下载失败'))
        }
      },
      fail: (error) => {
        reject(error)
      }
    })
  })
}

export function openDocument(filePath, fileType) {
  return new Promise((resolve, reject) => {
    uni.openDocument({
      filePath: filePath,
      fileType: fileType,
      success: () => {
        resolve()
      },
      fail: (error) => {
        reject(error)
      }
    })
  })
}
