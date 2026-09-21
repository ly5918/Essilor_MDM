import { onBeforeUnmount, onMounted, ref } from 'vue';

/**
 * 列表页表格高度自适应（配合分页固定在内容区底部）
 * <p>
 * 要解决的问题：分页条原本紧跟在表格后面，列表长短一变，分页条就上下浮动，
 * 与「平台统一版式」不符。这里把表格高度算成
 * `内容区底部 - 表格顶部 - 分页条预留高度`，
 * 于是分页条始终落在内容区底部同一个位置，表格内部滚动。
 *
 * @param reserve 分页条 + 卡片内边距预留高度（px），默认 64
 * @returns tableRef（挂到 el-table 的 ref）、tableHeight（绑定 :height）、recalc（手工重算）
 */
export function useListTableHeight(reserve = 64) {
  const tableRef = ref<{ $el?: HTMLElement } | HTMLElement | null>(null);
  const tableHeight = ref(360);

  const el = (): HTMLElement | null => {
    const raw = tableRef.value;
    if (!raw) {
      return null;
    }
    return ((raw as { $el?: HTMLElement }).$el ?? raw) as HTMLElement;
  };

  const recalc = () => {
    const table = el();
    const main = document.querySelector('.cmd-poc .cmd-main') as HTMLElement | null;
    if (!table || !main) {
      return;
    }
    const available = main.getBoundingClientRect().bottom - table.getBoundingClientRect().top - reserve;
    // 兜底 220px：窗口很矮时也保证表格可用；上限不设，由内容区高度决定
    tableHeight.value = Math.max(220, Math.round(available));
  };

  onMounted(() => {
    recalc();
    window.addEventListener('resize', recalc);
  });

  onBeforeUnmount(() => {
    window.removeEventListener('resize', recalc);
  });

  return { tableRef, tableHeight, recalc };
}
