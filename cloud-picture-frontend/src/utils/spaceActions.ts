import { Modal, message } from 'ant-design-vue'
import type { SpaceImageVO } from '../api/types'
import { deleteSpace, deleteSpaceImage } from '../api/space'
import { errorMessage } from '../api/http'

/** 删除私有图前的二次确认；成功后由调用方刷新本页（不触发全局 dataVersion） */
export function confirmDeleteSpaceImage(image: SpaceImageVO, onDone?: () => void) {
  Modal.confirm({
    title: '确认删除这张私有图片？',
    content: '删除后图片会从私有空间移除，对象存储中的文件也会被删除，操作不可撤销。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteSpaceImage(image.id)
        message.success('图片已删除')
        onDone?.()
      } catch (error) {
        message.error(errorMessage(error))
        throw error
      }
    },
  })
}

/** 删除私有空间的二次确认：有图时必须展示实际张数 */
export function confirmDeleteSpace(imageCount: number, onDone?: () => void) {
  Modal.confirm({
    title: '确认删除私有空间？',
    content: imageCount > 0
      ? `将删除该空间及其中 ${imageCount} 张图片，对象存储中的文件会一并删除，操作不可撤销。`
      : '该空间没有图片，删除后不可恢复。',
    okText: '删除空间',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteSpace()
        message.success('私有空间已删除')
        onDone?.()
      } catch (error) {
        message.error(errorMessage(error))
        throw error
      }
    },
  })
}