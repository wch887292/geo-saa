<template>
  <div class="trend-chart">
    <div ref="chartRef" class="chart-container" :style="{ height: height }"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
// 按需引入：全量 import * as echarts 会打进约 1MB 产物，
// 这里只注册实际用到的图表类型与组件。
import * as echarts from 'echarts/core'
import { LineChart, BarChart, RadarChart } from 'echarts/charts'
import {
  TooltipComponent,
  LegendComponent,
  GridComponent,
  RadarComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart,
  BarChart,
  RadarChart,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  RadarComponent,
  CanvasRenderer
])

const props = defineProps({
  chartData: { type: Object, default: () => ({}) },
  type: { type: String, default: 'line' },
  height: { type: String, default: '300px' }
})

const chartRef = ref(null)
let chartInstance = null

function getOption() {
  // 修复：原实现写的是 `const { data = {} } = props`，但组件根本没有名为 data 的 prop，
  // 解构结果恒为 {}，导致 categories / series 永远为空——图表从来没渲染出过真实数据。
  const data = props.chartData || {}
  const baseOption = {
    tooltip: { trigger: 'axis' },
    legend: { type: 'scroll', bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '15%', top: '10%', containLabel: true },
    animation: true,
    animationDuration: 800
  }

  if (props.type === 'radar') {
    return {
      ...baseOption,
      radar: {
        indicator: data.indicator || [],
        shape: 'polygon',
        splitNumber: 5,
        axisName: { color: '#606266' },
        splitLine: { lineStyle: { color: 'rgba(0,0,0,0.1)' } },
        splitArea: { areaStyle: { color: ['rgba(64,158,255,0.02)', 'rgba(64,158,255,0.05)'] } }
      },
      series: (data.series || []).map(s => ({
        type: 'radar',
        data: [s],
        areaStyle: { opacity: 0.1 },
        lineStyle: { width: 2 }
      }))
    }
  }

  return {
    ...baseOption,
    xAxis: {
      type: 'category',
      data: data.categories || [],
      axisLabel: { color: '#909399' },
      axisLine: { lineStyle: { color: '#e4e7ed' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#909399' },
      splitLine: { lineStyle: { color: '#f0f2f5', type: 'dashed' } }
    },
    series: (data.series || []).map(s => ({
      type: props.type === 'bar' ? 'bar' : 'line',
      data: s.data || [],
      name: s.name || '',
      smooth: props.type === 'line',
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: props.type === 'line' ? { width: 2 } : undefined,
      areaStyle: props.type === 'line' ? { opacity: 0.1 } : undefined,
      barMaxWidth: 32,
      itemStyle: { borderRadius: props.type === 'bar' ? [4, 4, 0, 0] : undefined }
    }))
  }
}

function initChart() {
  if (!chartRef.value) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(chartRef.value)
  chartInstance.setOption(getOption())
}

function resizeChart() {
  chartInstance?.resize()
}

watch(() => [props.chartData, props.type], () => {
  nextTick(() => {
    if (chartInstance) {
      chartInstance.setOption(getOption(), true)
    } else {
      initChart()
    }
  })
}, { deep: true })

onMounted(() => {
  nextTick(() => {
    initChart()
    window.addEventListener('resize', resizeChart)
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeChart)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<style scoped>
.trend-chart {
  width: 100%;
}
.chart-container {
  width: 100%;
}
</style>