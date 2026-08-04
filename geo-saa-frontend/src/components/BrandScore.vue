<template>
  <div class="brand-score" :class="['brand-score--' + size]">
    <div class="score-ring" :style="ringStyle">
      <svg :viewBox="viewBox" class="ring-svg">
        <circle
          :cx="center" :cy="center" :r="radius"
          fill="none" stroke="#f0f0f0"
          :stroke-width="strokeWidth"
        />
        <circle
          :cx="center" :cy="center" :r="radius"
          fill="none"
          :stroke="scoreColor"
          :stroke-width="strokeWidth"
          stroke-linecap="round"
          :stroke-dasharray="circumference"
          :stroke-dashoffset="dashOffset"
          :transform="'rotate(-90, ' + center + ', ' + center + ')'"
          class="score-circle"
        />
      </svg>
      <div class="score-value">
        <span class="score-number">{{ score }}</span>
      </div>
    </div>
    <div class="score-label">{{ label }}</div>
    <div class="score-subtitle" v-if="subtitle">{{ subtitle }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  score: { type: Number, default: 0 },
  size: { type: String, default: 'default' },
  label: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  maxScore: { type: Number, default: 100 }
})

const sizeMap = {
  small: { dimension: 80, stroke: 6, fontSize: 22 },
  default: { dimension: 120, stroke: 8, fontSize: 32 },
  large: { dimension: 160, stroke: 10, fontSize: 42 }
}

const dim = computed(() => sizeMap[props.size] || sizeMap.default)
const viewBox = computed(() => `0 0 ${dim.value.dimension} ${dim.value.dimension}`)
const center = computed(() => dim.value.dimension / 2)
const radius = computed(() => center.value - dim.value.stroke / 2 - 2)
const strokeWidth = computed(() => dim.value.stroke)
const circumference = computed(() => 2 * Math.PI * radius.value)
const ringStyle = computed(() => ({
  width: dim.value.dimension + 'px',
  height: dim.value.dimension + 'px',
  '--font-size': dim.value.fontSize + 'px'
}))

const dashOffset = computed(() => {
  return circumference.value - (props.score / props.maxScore) * circumference.value
})

const scoreColor = computed(() => {
  if (props.score >= 80) return '#67c23a'
  if (props.score >= 60) return '#e6a23c'
  if (props.score >= 40) return '#f56c6c'
  return '#f56c6c'
})
</script>

<style scoped>
.brand-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px;
}
.score-ring {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ring-svg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}
.score-circle {
  transition: stroke-dashoffset 0.8s ease, stroke 0.3s ease;
  filter: drop-shadow(0 0 4px currentColor);
}
.score-value {
  display: flex;
  align-items: baseline;
  gap: 2px;
  z-index: 1;
}
.score-number {
  font-size: var(--font-size, 32px);
  font-weight: 700;
  color: #303133;
}
.score-label {
  margin-top: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.score-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
</style>