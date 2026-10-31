/**
 * 按需加载并注册图表组件，供经营统计与排片分析页面复用。
 */
import * as echarts from 'echarts/core'
import { BarChart, HeatmapChart, LineChart, PieChart } from 'echarts/charts'
import { GraphicComponent, GridComponent, TooltipComponent, VisualMapComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  HeatmapChart,
  LineChart,
  PieChart,
  GraphicComponent,
  GridComponent,
  TooltipComponent,
  VisualMapComponent,
  CanvasRenderer
])

export { echarts }
