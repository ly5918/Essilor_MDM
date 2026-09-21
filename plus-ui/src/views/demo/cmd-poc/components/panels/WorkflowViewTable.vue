<template>
  <section class="page list-page">
    <el-card class="page-card" shadow="never" :body-style="{ padding: '10px 16px 14px' }">
      <div class="wv-toolbar card-toolbar">
        <div class="wv-toolbar-left">
          <span class="wv-title">{{ meta.title }}</span>
          <span class="wv-count">共 {{ rows.length }} 条</span>
        </div>
        <div class="wv-toolbar-right">
          <el-input v-model="keyword" placeholder="One ID / 申请编号 / 客户主题" clearable style="width: 240px" @keyup.enter="load" @clear="load" />
          <el-button type="primary" plain icon="Refresh" @click="load">刷新</el-button>
        </div>
      </div>

      <!-- 口径说明：为什么这里是「已激活」而不是「待办」 -->
      <el-alert
        class="wv-tip"
        type="info"
        :closable="false"
        show-icon
        title="已激活 = Warm-Flow 实例已启动且未结束。刚提交的客户新建 / 变更 / 层级申请会立刻出现在这里；审批结束（批准 / 拒绝 / 取消）后移入「已完成的工作流」。点 One ID 看这一单走到泳道图哪一步；点「流程跟踪」弹窗查看逐步明细。"
      />

      <!-- 已激活的工作流：运行中 / 等待人工处理（对齐 Deepblue「工作项」列结构） -->
      <el-table
        v-if="view === 'workitem'"
        ref="tableRef"
        v-loading="loading"
        border
        :data="rows"
        :height="tableHeight"
        class="data-table"
      >
        <!-- 操作列只留「流程跟踪」；泳道图改由点 One ID 打开（与「已完成的工作流」同一形态） -->
        <el-table-column label="操作" width="118" align="center" fixed="left">
          <template #default="{ row }">
            <el-button link type="primary" size="small" icon="View" @click="onViewTrace(row)">流程跟踪</el-button>
          </template>
        </el-table-column>
        <el-table-column label="One ID" width="155" fixed="left">
          <template #default="{ row }">
            <span
              v-if="row.oneId"
              class="wv-oneid"
              title="点击查看该单的泳道图（按实际执行进度点亮节点）"
              @click="onViewGraph(row)"
            >{{ row.oneId }}</span>
            <span v-else class="wv-oneid-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column label="类型（当前节点）" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="warning">Wait for user</el-tag>
            <span class="wv-sub">{{ row.currentNodeName ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="优先" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.priority)" size="small" effect="plain">{{ row.priority ?? '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bizType ?? '—' }} - {{ row.bizTitle ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="数据工作流" prop="sceneCode" width="160" show-overflow-tooltip />
        <el-table-column label="用户" prop="assigneeName" width="100" />
        <el-table-column label="建立日期" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="注释" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.opinion || '—' }}</template>
        </el-table-column>
        <el-table-column label="模板流程" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sceneName ?? row.sceneCode }}</template>
        </el-table-column>
        <el-table-column label="SLA" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="slaTagType(row.slaState)" size="small" effect="plain">{{ slaLabel(row.slaState) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container" v-if="total > 0">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { listFlowInstances } from '@/api/demo/cmdPoc';
import type { FlowInstanceVO, PageResult } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { useListTableHeight } from '../../composables/useListTableHeight';

defineOptions({ name: 'CmdPocWorkflowViewTable' });

/**
 * 「流程中心 › 已激活工作流」表格（Deepblue 列结构）。
 * 列结构对齐需求截图（Novartis Deepblue - Customer Data Management China），
 * 「One ID」列承载贯穿 ID，位置紧随「图形」之后（fixed），保证不横向滚动也能看到，
 * 可按该 ID 到任意页面搜索框查询。
 *
 * 注意：目前只有 workitem 视图在用（`FlowWorkitemPanel`）；
 * 「已完成的工作流」列表在 `FlowDonePanel`（需要状态筛选 + 分页），两者列结构不同，没有共用。
 */
const props = defineProps<{
  /** 视图类型：workitem 运行中 / 等待人工处理 */
  view: 'workitem' | 'active' | 'done';
}>();

const META: Record<string, { title: string }> = {
  workitem: { title: '已激活工作流' },
  active: { title: '已激活工作流' },
  done: { title: '已完成的工作流' }
};
const meta = META[props.view];

const loading = ref(false);
const rows = ref<FlowInstanceVO[]>([]);
const keyword = ref('');
const pageNum = ref(1);
const pageSize = ref(10);
const total = ref(0);

const { openDialog } = useCmdPoc();

/** 表格高度自适应：分页条固定在内容区底部，不随数据条数浮动 */
const { tableRef, tableHeight, recalc } = useListTableHeight(70);

const load = async () => {
  loading.value = true;
  try {
    // 「已激活的工作流」= 引擎实例已启动且未结束（runState=RUNNING，与后端 FINAL_STATUSES 口径互补）：
    // 用 status=PENDING 会漏掉「实例在跑但当前节点是自动节点」的中间态，也不符合「已激活」这个叫法。
    const query = {
      runState: props.view === 'done' ? 'DONE' : 'RUNNING',
      keyword: keyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    };
    const page = await listFlowInstances(query);
    rows.value = page.rows ?? [];
    total.value = page.total ?? 0;
  } finally {
    loading.value = false;
    recalc();
  }
};

/** 泳道图（实例视图：按该次执行的实际进度点亮节点） */
const onViewGraph = (row: unknown) => {
  const inst = row as FlowInstanceVO;
  openDialog('flowGraph', {
    sceneCode: inst.sceneCode,
    sceneName: inst.sceneName ?? inst.sceneCode,
    flowCode: inst.flowCode,
    taskNo: inst.taskNo
  });
};

/**
 * 流程跟踪：与「已完成的工作流」行内按钮是**同一个弹窗**（同形不同态）。
 * detailType='active' 供 mock 分支推导「实例进行到一半」；live 分支忽略该参数。
 */
const onViewTrace = (row: unknown) => {
  const inst = row as FlowInstanceVO;
  if (!inst?.taskNo) return;
  openDialog('flowTrace', { taskNo: inst.taskNo, detailType: 'active' });
};

const RISK_TAG: Record<string, 'danger' | 'warning' | 'info'> = { High: 'danger', Medium: 'warning', Low: 'info' };
const riskTagType = (v?: string) => RISK_TAG[v ?? ''] ?? 'info';

const STATUS_TEXT: Record<string, string> = {
  PENDING: '进行中',
  APPROVED: '已批准',
  COMPLETED: '已完成',
  REJECTED: '已拒绝',
  RETURNED: '已退回',
  ESCALATED: '已升级GC',
  CANCELLED: '已取消'
};
const STATUS_TAG: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  PENDING: 'warning',
  APPROVED: 'success',
  COMPLETED: 'success',
  REJECTED: 'danger',
  RETURNED: 'danger',
  ESCALATED: 'warning',
  CANCELLED: 'info'
};
const statusLabel = (v: string) => STATUS_TEXT[v] ?? v ?? '—';
const statusTagType = (v: string) => STATUS_TAG[v] ?? 'info';

const slaLabel = (v?: string) => (v === 'OVERDUE' ? '已超时' : v === 'DUE_SOON' ? '即将超时' : '正常');
const slaTagType = (v?: string) => (v === 'OVERDUE' ? 'danger' : v === 'DUE_SOON' ? 'warning' : 'success');

const formatTime = (v?: string) => (v ? String(v).replace('T', ' ').slice(0, 16) : '—');

onMounted(load);
</script>

<style scoped lang="scss">
.wv-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;

  &-left {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.wv-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.wv-count {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.wv-tip {
  margin-bottom: 10px;
}

.wv-sub {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

/* 贯穿 ID（One ID）：等宽字体高亮，便于跨页面人工比对 */
.wv-oneid {
  font-family: 'Cascadia Mono', Consolas, 'Courier New', monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary);
  cursor: pointer;
}

.wv-oneid-empty {
  color: var(--el-text-color-placeholder);
}
</style>
