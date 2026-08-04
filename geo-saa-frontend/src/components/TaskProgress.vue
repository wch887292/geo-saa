<template>
  <div class="task-progress" :class="['task-progress--' + status]">
    <div class="task-header">
      <span class="task-name">{{ name }}</span>
      <el-tag v-if="statusLabel" :type="tagType" size="small" effect="plain">
        {{ statusLabel }}
      </el-tag>
    </div>
    <el-progress
      :percentage="progress"
      :status="progressStatus"
      :stroke-width="10"
      :color="progressColor"
      striped
      striped-flow
    />
    <div class="task-footer" v-if="detail || currentStep">
      <span class="detail-text" v-if="detail">{{ detail }}</span>
      <span class="step-text" v-if="currentStep">{{ currentStep }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, default: '' },
  progress: { type: Number, default: 0 },
  status: { type: String, default: 'pending' },
  detail: { type: String, default: '' },
  currentStep: { type: String, default: '' }
})

const emit = defineEmits(['update:progress'])

const statusMap = {
  pending: { label: '等待中', tag: 'info', progress: '' },
  running: { label: '运行中', tag: 'warning', progress: '' },
  completed: { label: '已完成', tag: 'success', progress: 'success' },
  failed: { label: '失败', tag: 'danger', progress: 'exception' }
}

const statusLabel = computed(() => statusMap[props.status]?.label || '')
const tagType = computed(() => statusMap[props.status]?.tag || 'info')
const progressStatus = computed(() => statusMap[props.status]?.progress || '')

const progressColor = computed(() => {
  if (props.progress < 30) return '#f56c6c'
  if (props.progress < 70) return '#e6a23c'
  return '#67c23a'
})
</script>

<style scoped>
.task-progress {
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  transition: box-shadow 0.3s;
}
.task-progress:hover {
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}
.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.task-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.task-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.detail-text {
  font-size: 12px;
  color: #909399;
}
.step-text {
  font-size: 12px;
  color: #409eff;
}
.task-progress--failed {
  border-color: #fde2e2;
}
.task-progress--completed {
  border-color: #e1f3d8;
}
</style>