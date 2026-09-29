import { Modal, message } from 'ant-design-vue'
import type { ImageVO } from '../api/types'
import { deleteImage } from '../api/image'
import { adminDeleteImage } from '../api/admin-image'
import { errorMessage } from '../api/http'
import { isAdmin, session } from '../stores/session'
import { bumpData } from '../stores/ui'

export function isOwner(image: ImageVO): boolean {
  return !!session.user && image.ownerId === session.user.id
}

/**
 * 删除前二次确认。管理员删除他人图片走管理端接口，其余走所有者接口；
 * 失败时保持弹窗打开且不提前把图片从列表移除（列表由 dataVersion 触发重新拉取）。
 */
export function confirmDeleteImage(image: ImageVO, onDone?: () => void) {
  const viaAdminApi = isAdmin() && !isOwner(image)
  Modal.confirm({
    title: '确认删除这张图片？',
    content: '删除后图片会从列表移除，对象存储中的文件也会被删除，操作不可撤销。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await (viaAdminApi ? adminDeleteImage(image.id) : deleteImage(image.id))
        message.success('图片已删除')
        bumpData()
        onDone?.()
      } catch (error) {
        message.error(errorMessage(error))
        throw error
      }
    },
  })
}