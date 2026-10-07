<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { Button, Modal, Tooltip } from 'ant-design-vue'
import {
  RotateRightOutlined,
  UndoOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
} from '@ant-design/icons-vue'
import type { SpaceImageVO } from '../api/types'

const open = defineModel<boolean>('open', { required: true })
const props = defineProps<{ image: SpaceImageVO | null }>()

const MIN_SCALE = 0.2
const MAX_SCALE = 5
const STEP = 0.2

const scale = ref(1)
const rotation = ref(0)
const offsetX = ref(0)
const offsetY = ref(0)
const dragging = ref(false)

let startX = 0
let startY = 0
let startOffsetX = 0
let startOffsetY = 0

const transformStyle = computed(() => ({
  transform: `translate(${offsetX.value}px, ${offsetY.value}px) scale(${scale.value}) rotate(${rotation.value}deg)`,
}))

function clampScale(value: number) {
  return Math.min(MAX_SCALE, Math.max(MIN_SCALE, Number(value.toFixed(2))))
}

function zoomIn() {
  scale.value = clampScale(scale.value + STEP)
}

function zoomOut() {
  scale.value = clampScale(scale.value - STEP)
}

function rotate() {
  rotation.value = (rotation.value + 90) % 360
}

function reset() {
  scale.value = 1
  rotation.value = 0
  offsetX.value = 0
  offsetY.value = 0
}

function onWheel(event: WheelEvent) {
  scale.value = clampScale(scale.value + (event.deltaY < 0 ? STEP : -STEP))
}

function onPointerMove(event: PointerEvent) {
  if (!dragging.value) return
  offsetX.value = startOffsetX + (event.clientX - startX)
  offsetY.value = startOffsetY + (event.clientY - startY)
}

function stopDrag() {
  dragging.value = false
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', stopDrag)
}

function onPointerDown(event: PointerEvent) {
  dragging.value = true
  startX = event.clientX
  startY = event.clientY
  startOffsetX = offsetX.value
  startOffsetY = offsetY.value
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', stopDrag)
}

// 每次打开都回到初始视图；关闭时摘掉拖拽监听，避免残留
watch(open, (value) => {
  if (value) {
    reset()
  } else {
    stopDrag()
  }
})

onUnmounted(stopDrag)
</script>

<template>
  <Modal
    v-model:open="open"
    :title="props.image?.name || '图片预览'"
    :footer="null"
    :width="1000"
    centered
    :destroy-on-close="true"
    :body-style="{ background: 'var(--cp-accent)', padding: '16px' }"
  >
    <div v-if="props.image?.url" class="preview-stage" @wheel.prevent="onWheel">
      <img
        class="preview-image"
        :class="{ 'preview-image-dragging': dragging }"
        :src="props.image.url"
        :alt="props.image.name || '私有图片预览'"
        :style="transformStyle"
        draggable="false"
        @pointerdown="onPointerDown"
        @dragstart.prevent
      />

      <div class="preview-toolbar">
        <Tooltip title="缩小">
          <Button type="text" size="small" aria-label="缩小" @click="zoomOut">
            <template #icon><ZoomOutOutlined /></template>
          </Button>
        </Tooltip>
        <Tooltip title="放大">
          <Button type="text" size="small" aria-label="放大" @click="zoomIn">
            <template #icon><ZoomInOutlined /></template>
          </Button>
        </Tooltip>
        <Tooltip title="旋转 90°">
          <Button type="text" size="small" aria-label="旋转 90 度" @click="rotate">
            <template #icon><RotateRightOutlined /></template>
          </Button>
        </Tooltip>
        <Tooltip title="重置视图">
          <Button type="text" size="small" aria-label="重置视图" @click="reset">
            <template #icon><UndoOutlined /></template>
          </Button>
        </Tooltip>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
/* 固定高度的深色画布：缩放/旋转都不会被裁掉 */
.preview-stage {
  position: relative;
  display: flex;
  height: 62vh;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: var(--cp-radius);
}

.preview-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  cursor: grab;
  touch-action: none;
  user-select: none;
  transition: transform 0.15s ease;
}

.preview-image-dragging {
  cursor: grabbing;
  transition: none;
}

.preview-toolbar {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  gap: 2px;
  padding: 4px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: var(--cp-radius);
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
}

.preview-toolbar :deep(.ant-btn) {
  color: #fff;
}

.preview-toolbar :deep(.ant-btn:hover) {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}
</style>