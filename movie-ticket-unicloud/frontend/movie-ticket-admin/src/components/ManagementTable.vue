<script lang="ts">
import { Comment, Fragment, Text, cloneVNode, defineComponent, h, isVNode, onBeforeUnmount, onMounted, ref, type CSSProperties, type VNode, type VNodeArrayChildren } from 'vue'
import { ElTable, type TableInstance } from 'element-plus/es/components/table/index'
import 'element-plus/es/components/table/style/css'

/** 管理列表按容器分配列宽；窄容器逐条展示全部字段，不隐藏数据列。 */
export default defineComponent({
  name: 'ManagementTable',
  inheritAttrs: false,
  setup(_props, { attrs, slots, expose }) {
    const table = ref<TableInstance>()
    const availableWidth = ref(0)
    let observer: ResizeObserver | undefined

    expose({ clearSelection: () => table.value?.clearSelection() })
    onMounted(() => {
      const element = table.value?.$el as HTMLElement | undefined
      if (!element) return
      const measure = () => {
        const style = getComputedStyle(element)
        const width = Math.floor(element.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight))
        if (width > 0) availableWidth.value = width
      }
      measure()
      observer = new ResizeObserver(measure)
      observer.observe(element)
    })
    onBeforeUnmount(() => observer?.disconnect())

    function columns(children: VNodeArrayChildren): VNode[] {
      return children.flatMap(child => {
        if (Array.isArray(child)) return columns(child)
        if (!isVNode(child) || child.type === Comment || child.type === Text) return []
        if (child.type === Fragment) return columns(child.children as VNodeArrayChildren)
        return [child]
      })
    }

    return () => {
      const nodes = columns(slots.default?.() ?? [])
      const selectionWidth = 44
      const weight = (node: VNode) => Number(node.props?.width || node.props?.minWidth || node.props?.['min-width']) || 100
      const selectable = nodes.some(node => node.props?.type === 'selection')
      const naturalWidth = nodes.reduce((sum, node) => sum + weight(node), 0)
      const width = availableWidth.value || naturalWidth
      const minimum = (node: VNode) => {
        if (node.props?.type === 'selection') return selectionWidth
        return ({ ID: 56, 海报: 76, 头像: 56, 操作: 104, 电影: 100 } as Record<string, number>)[node.props?.label] || 60
      }
      const minimumWidth = nodes.reduce((sum, node) => sum + minimum(node), 0)
      const stacked = width < Math.max(620, minimumWidth, nodes.length * 76)
      const flexible = nodes.filter(node => node.props?.type !== 'selection')
      const extraWeight = (node: VNode) => Math.max(1, weight(node) - minimum(node))
      const totalWeight = flexible.reduce((sum, node) => sum + (stacked ? weight(node) : extraWeight(node)), 0)
      const flexibleWidth = width - (selectable ? selectionWidth : 0)
      let assigned = 0
      const renderedColumns = nodes.map((node, index) => {
        const selection = node.props?.type === 'selection'
        const columnWidth = index === nodes.length - 1 ? width - assigned
          : selection ? selectionWidth
            : stacked ? Math.floor(flexibleWidth * weight(node) / totalWeight)
              : minimum(node) + Math.floor((width - minimumWidth) * extraWeight(node) / totalWeight)
        assigned += columnWidth
        return cloneVNode(node, {
          width: columnWidth,
          minWidth: columnWidth,
          'min-width': columnWidth,
          fixed: false,
          showOverflowTooltip: false,
          'show-overflow-tooltip': false,
          className: [node.props?.className || node.props?.['class-name'], node.props?.label === '操作' ? 'management-actions' : ''].filter(Boolean).join(' ')
        })
      })

      return h(ElTable, {
        ...attrs,
        ref: table,
        class: [attrs.class, 'management-table', { 'management-table--stacked': stacked, 'management-table--columns': stacked && width >= 620, 'management-table--selectable': selectable }],
        cellStyle: ({ column }: { column: { label: string; type: string } }): CSSProperties => ({
          '--management-label': JSON.stringify(column.type === 'selection' ? '选择记录' : column.label || '')
        }),
        tableLayout: 'fixed'
      }, { ...slots, default: () => renderedColumns })
    }
  }
})
</script>

<style>
.management-table {
  width: 100%;
  min-width: 0;
  max-width: 100%;
}

.management-table.el-table .cell {
  padding-inline: 8px;
  overflow: visible;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: normal;
  text-overflow: clip;
  line-height: 1.65;
}

.management-table .movie-cell {
  flex-wrap: wrap;
}

.management-table .movie-cell .el-image {
  flex-shrink: 0;
}

.management-table .movie-cell .movie-info {
  min-width: 64px;
}

.management-table .movie-info .movie-title {
  white-space: normal;
  overflow: visible;
  text-overflow: clip;
}

.management-table .cell .el-image,
.management-table .cell .el-avatar {
  max-width: 100%;
}

.management-table .cell .code-value {
  max-width: 100%;
  overflow-wrap: anywhere;
}

.management-table .management-actions .cell,
.management-table .table-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 2px;
  white-space: normal;
}

.management-table .management-actions .el-button {
  margin-left: 0;
  padding-inline: 4px;
}

.management-table .el-tag {
  max-width: 100%;
  height: auto;
  min-height: 24px;
  white-space: normal;
}

.management-table .el-tag__content {
  white-space: normal;
  overflow-wrap: anywhere;
}

.management-table .el-rate {
  display: flex;
  flex-wrap: wrap;
  height: auto;
  --el-rate-icon-size: 14px;
}

.management-table .el-rate__icon {
  margin-right: 0;
}

.management-table--stacked table,
.management-table--stacked thead,
.management-table--stacked tbody,
.management-table--stacked tr {
  display: block;
  width: 100% !important;
}

.management-table--stacked colgroup,
.management-table--stacked th.el-table__cell,
.management-table--stacked:not(.management-table--selectable) .el-table__header-wrapper {
  display: none;
}

.management-table--stacked .el-table__header-wrapper th.el-table-column--selection {
  display: block;
  height: auto;
  padding-block: 8px;
  border-radius: 8px;
}

.management-table--stacked .el-table__header-wrapper .el-table-column--selection .cell::after {
  content: '全选当前页';
  margin-left: 8px;
  color: var(--admin-muted);
  font-size: 12px;
}

.management-table--stacked .el-table__row {
  margin-block: 12px;
  padding: 6px 10px;
  border: 1px solid var(--admin-border);
  border-radius: 10px;
}

.management-table--stacked td.el-table__cell {
  display: block;
  width: 100%;
  padding-block: 8px;
}

.management-table--stacked td.el-table__cell:last-child {
  border-bottom: 0;
}

.management-table--stacked td.el-table__cell .cell {
  display: grid;
  grid-template-columns: 5.5em minmax(0, 1fr);
  align-items: start;
  gap: 10px;
  padding-inline: 2px;
  text-align: left;
}

.management-table--stacked td .cell::before {
  content: var(--management-label);
  color: var(--admin-muted);
  font-size: 12px;
  font-weight: 600;
}

.management-table--stacked .management-actions .cell {
  display: flex !important;
  gap: 10px;
}

.management-table--stacked .management-actions .cell::before {
  flex: 0 0 5.5em;
}

.management-table--stacked .el-table__empty-block {
  width: 100% !important;
}

.management-table--columns .el-table__row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 24px;
}

.management-table--columns .el-table-column--selection,
.management-table--columns .management-actions {
  grid-column: 1 / -1;
}
</style>
