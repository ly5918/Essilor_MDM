<template>
  <section class="page list-page">
    <el-card class="page-card" shadow="never" :body-style="{ padding: '10px 16px 14px' }">
      <div class="fd-toolbar card-toolbar">
        <div class="fd-toolbar-left">
          <span class="fd-title">已完成的工作流</span>
          <span class="fd-count">共 {{ total }} 条实例</span>
        </div>
        <div class="fd-toolbar-right">
          <el-input
            v-model="keyword"
            placeholder="事务ID / 申请编号 / 客户主题"
            clearable
            style="width: 240px"
            @keyup.enter="load"
            @clear="load"
          />
          <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px" @change="load">
            <el-option label="全部状态" value="" />
            <el-option label="已批准" value="APPROVED" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已拒绝" value="REJECTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
          <el-button type="primary" plain icon="Search" @click="load">搜索</el-button>
          <el-button type="primary" plain icon="Refresh" @click="load">刷新</el-button>
        </div>
      </div>

      <el-alert
        class="fd-tip"
        type="info"
        :closable="false"
        show-icon
        title="已结束的实例（已批准 / 已拒绝 / 已取消 / 已完成）。点「流程跟踪」弹窗展示完整链路（Warm-Flow 实例流程图、泳道步骤条、分步骤明细、Data context state 与审批轨迹），可点步骤条上任意节点查看该节点实际发生了什么。"
      />

      <el-table
        ref="tableRef"
        v-loading="loading"
        border
        :data="rows"
        :height="tableHeight"
        class="data-table"
        highlight-current-row
      >
        <!-- 操作列只留「流程跟踪」；泳道图统一从流程跟踪弹窗内进入 -->
        <el-table-column label="操作" width="118" align="center" fixed="left">
          <template #default="{ row }">
            <el-button link type="primary" size="small" icon="View" @click.stop="onViewTrace(row)">流程跟踪</el-button>
          </template>
        </el-table-column>
        <!-- 事务ID = task_no（AP-…）：提交即生成，整套工作流全程用它贯穿追踪；
             纯文本展示，泳道图统一从「流程跟踪」弹窗内进入 -->
        <el-table-column label="事务ID" width="150" fixed="left">
          <template #default="{ row }">
            <span class="fd-oneid">{{ row.taskNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" prop="bizType" width="110" />
        <el-table-column label="描述" prop="bizTitle" min-width="190" show-overflow-tooltip />
        <el-table-column label="场景" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sceneName ?? row.sceneCode }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="140">
          <template #default="{ row }">
            <el-progress :percentage="row.progressPercent ?? 0" :stroke-width="10" :text-inside="true" />
          </template>
        </el-table-column>
        <el-table-column label="当前节点" prop="currentNodeName" min-width="140" show-overflow-tooltip />
        <el-table-column label="处理人" prop="assigneeName" width="100" />
        <el-table-column label="完成时间" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.finishTime) }}</template>
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
import type { FlowInstanceVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { useListTableHeight } from '../../composables/useListTableHeight';

defineOptions({ name: 'CmdPocFlowDonePanel' });

/**
 * 「工作流 › 已完成的工作流」：已结束实例的**列表页**（runState=DONE 固定，
 * 与后端 FINAL_STATUSES 口径一致）。
 *
 * 详情**不再单独占一个菜单**：行内「查看流程跟踪」直接打开 flowTrace 弹窗
 * （与「工作流定义 → 某行配置」同一形态）。理由是「某一次执行」属于二级下钻对象，
 * 做成独立页面会出现「进了页面却不知道看哪条实例」的空态。
 */
const { openDialog } = useCmdPoc();

/** 表格高度自适应：分页条固定在内容区底部，不随数据条数浮动 */
const { tableRef, tableHeight, recalc } = useListTableHeight(70);

const loading = ref(false);
const rows = ref<FlowInstanceVO[]>([]);
const keyword = ref('');
const status = ref('');
const pageNum = ref(1);
const pageSize = ref(10);
const total = ref(0);

const load = async () => {
  loading.value = true;
  try {
    const page = await listFlowInstances({
      runState: 'DONE',
      status: status.value || undefined,
      keyword: keyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    });
    rows.value = page.rows ?? [];
    total.value = page.total ?? 0;
  } finally {
    loading.value = false;
    recalc();
  }
};

/**
 * 查看流程跟踪：打开弹窗展示该实例的完整链路。
 * detailType='done' 供 mock 分支推导「全部步骤已完成」；live 分支忽略该参数。
 */
const onViewTrace = (row: unknown) => {
  const inst = row as FlowInstanceVO;
  if (!inst?.taskNo) return;
  openDialog('flowTrace', { taskNo: inst.taskNo, detailType: 'done' });
};

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

const formatTime = (v?: string) => (v ? String(v).replace('T', ' ').slice(0, 16) : '—');

onMounted(load);
</script>

<style scoped lang="scss">
.fd-toolbar {
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

.fd-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.fd-count {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.fd-tip {
  margin-bottom: 10px;
}

/* 事务ID（taskNo）：等宽字体高亮，便于跨页面人工比对 */
.fd-oneid {
  font-family: 'Cascadia Mono', Consolas, 'Courier New', monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary);
}
</style>
