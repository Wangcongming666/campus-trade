import { onBeforeUnmount, onMounted, shallowRef, watch } from 'vue'
import { init, use } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

let registered = false

function registerCharts() {
  if (registered) return
  use([
    LineChart,
    PieChart,
    BarChart,
    GridComponent,
    TooltipComponent,
    LegendComponent,
    TitleComponent,
    CanvasRenderer,
  ])
  registered = true
}

/** 按需初始化 ECharts，并在窗口和容器尺寸变化时自动 resize */
export function useChart(elRef, optionRef) {
  const chart = shallowRef(null)
  let observer = null

  function render() {
    if (!elRef.value) return
    registerCharts()
    if (!chart.value) {
      chart.value = init(elRef.value)
    }
    if (optionRef?.value) {
      chart.value.setOption(optionRef.value, true)
    }
    chart.value.resize()
  }

  function onResize() {
    chart.value?.resize()
  }

  onMounted(() => {
    render()
    window.addEventListener('resize', onResize)
    if (typeof ResizeObserver !== 'undefined' && elRef.value) {
      observer = new ResizeObserver(() => onResize())
      observer.observe(elRef.value)
    }
  })

  watch(
    () => optionRef?.value,
    () => render(),
    { deep: true },
  )

  onBeforeUnmount(() => {
    window.removeEventListener('resize', onResize)
    observer?.disconnect()
    chart.value?.dispose()
    chart.value = null
  })

  return { chart, render, resize: onResize }
}
