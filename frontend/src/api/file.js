import request from '@/utils/request'

/**
 * 文件上传（规范第 8、10 节已确认）
 *   POST /files/upload  multipart/form-data，字段名 file
 *   返回 { url, name, size }
 */

export function uploadFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/files/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
