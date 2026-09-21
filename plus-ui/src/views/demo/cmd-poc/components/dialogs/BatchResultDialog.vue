<template>
  <div class="poc-dialog-body">
    <div class="kpi-row">
      <div
        v-for="item in kpis"
        :key="item.label"
        class="kpi clickable"
        :class="{ disabled: item.value === 0 }"
        @click="item.value > 0 && selectType(item.type)"
      >
        <b>{{ item.value }}</b>
        <span>{{ item.label }} · 点击查看明细</span>
      </div>
    </div>

    <el-table border :data="result.routes" class="data-table">
      <el-table-column label="结果" prop="result" min-width="110" />
      <el-table-column label="处理方式" prop="handling" min-width="190" />
      <el-table-column label="责任角色" prop="owner" min-width="140" />
      <el-table-column label="明细" min-width="120" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="selectType(routeTypeOf(row.result))">{{ row.detail }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分流明细下钻（真实行明细：cmd_import_row） -->
    <div class="detail-block">
      <div class="detail-head">
        <span class="detail-title">{{ activeLabel }} 明细（{{ rowTotal }} 条）</span>
        <el-pagination
          v-if="rowTotal > rowPageSize"
          v-model:current-page="rowPageNum"
          :page-size="rowPageSize"
          :total="rowTotal"
          layout="prev, pager, next"
          small
          @current-change="loadRows"
        />
      </div>
      <el-table v-loading="rowLoading" border :data="rows" class="data-table" max-height="320" size="small">
        <el-table-column label="行号" prop="rowNo" width="70" align="center" />
        <el-table-column label="客户名称" prop="legalName" min-width="180" show-overflow-tooltip />
        <el-table-column label="信用代码" prop="creditCode" min-width="170" show-overflow-tooltip />
        <el-table-column label="One ID / 候选" min-width="140">
          <template #default="{ row }">{{ row.oneId || '—' }}</template>
        </el-table-column>
        <el-table-column label="质量分" prop="dqScore" width="80" align="center" />
        <el-table-column label="问题 / 说明" prop="errorSummary" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.errorSummary || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="activeType === 'SUSPECTED'" label="治理操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="onLink(row)">关联已有</el-button>
            <el-button link type="warning" @click="onReturn(row)">退回修复</el-button>
            <el-button link type="danger" @click="onExclude(row)">排除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="empty-hint">该分流暂无行明细</span>
        </template>
      </el-table>
      <div v-if="activeType === 'NEW'" class="impact">
        New 行已随任务提交「批量导入确认」审批（工作流场景 IMPORT_BATCH）；审批通过后自动生成 One ID 并激活主档。
      </div>
      <div v-else-if="activeType === 'INVALID'" class="impact">
        Invalid 行按错误策略退回修复（Fix）；修复后可在「新建导入任务」重新上传。
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getBatchResult, importRowAction, listImportJobRows } from '@/api/demo/cmdPoc';
import type { BatchResultVO, ImportRowVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocBatchResultDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const { closeDialog } = useCmdPoc();

const result = ref<BatchResultVO>({
  jobId: '-',
  exact: 0,
  suspected: 0,
  created: 0,
  review: 0,
  invalid: 0,
  routes: []
});

const rowLoading = ref(false);
const rows = ref<ImportRowVO[]>([]);
const rowTotal = ref(0);
const rowPageNum = ref(1);
const rowPageSize = 20;
const activeType = ref<'EXACT' | 'SUSPECTED' | 'NEW' | 'INVALID'>('SUSPECTED');

const jobCode = () => (props.payload?.jobId as string) ?? 'IMP-001';

/** 原型 KPI：Exact / Suspected / New / Review / Invalid（四类分流 + 待复核） */
const kpis = computed(() => [
  { label: 'Exact', value: result.value.exact, type: 'EXACT' as const },
  { label: 'Suspected', value: result.value.suspected, type: 'SUSPECTED' as const },
  { label: 'New', value: result.value.created, type: 'NEW' as const },
  { label: 'Review', value: result.value.review, type: 'SUSPECTED' as const },
  { label: 'Invalid', value: result.value.invalid, type: 'INVALID' as const }
]);

const activeLabel = computed(
  () => ({ EXACT: 'Exact', SUSPECTED: 'Suspected', NEW: 'New', INVALID: 'Invalid' })[activeType.value] ?? activeType.value
);

const routeTypeOf = (result: string) => {
  const upper = (result ?? '').toUpperCase();
  if (upper === 'NEW' || upper === 'CREATED') {
    return 'NEW' as const;
  }
  if (upper === 'INVALID') {
    return 'INVALID' as const;
  }
  if (upper === 'REVIEW') {
    return 'SUSPECTED' as const;
  }
  return 'EXACT' as const;
};

const loadResult = async () => {
  result.value = await getBatchResult(jobCode());
};

/** 点击 KPI 或 明细：加载该分流的真实行明细 */
const selectType = async (type: 'EXACT' | 'SUSPECTED' | 'NEW' | 'INVALID') => {
  activeType.value = type;
  rowPageNum.value = 1;
  await loadRows();
};

const loadRows = async () => {
  rowLoading.value = true;
  try {
    const page = await listImportJobRows(jobCode(), activeType.value, rowPageNum.value, rowPageSize);
    rows.value = page.rows;
    rowTotal.value = page.total;
  } finally {
    rowLoading.value = false;
  }
};

/** BU Scope 治理：批量关联、排除或退回修复（设计场景二泳道第 5 阶段） */
const onLink = async (row: ImportRowVO) => {
  const { value } = await ElMessageBox.prompt('输入要关联的已有 One ID', '关联已有 One ID', {
    inputValue: row.oneId ?? '',
    inputPattern: /^GC-[0-9A-Z]{6,}$/,
    inputErrorMessage: 'One ID 格式形如 GC-000128',
    confirmButtonText: '确认关联',
    cancelButtonText: '取消'
  });
  const note = await importRowAction(row.id!, 'LINK', value);
  ElMessage.success(note);
  await refresh();
};

const onReturn = async (row: ImportRowVO) => {
  await ElMessageBox.confirm(`确认将「${row.legalName ?? `第 ${row.rowNo} 行`}」退回修复？`, '退回修复', {
    type: 'warning',
    confirmButtonText: '确认退回',
    cancelButtonText: '取消'
  });
  const note = await importRowAction(row.id!, 'RETURN');
  ElMessage.success(note);
  await refresh();
};

const onExclude = async (row: ImportRowVO) => {
  await ElMessageBox.confirm(`确认排除「${row.legalName ?? `第 ${row.rowNo} 行`}」？排除后不纳入主档。`, '排除', {
    type: 'warning',
    confirmButtonText: '确认排除',
    cancelButtonText: '取消'
  });
  const note = await importRowAction(row.id!, 'EXCLUDE');
  ElMessage.success(note);
  await refresh();
};

/** 治理动作后刷新统计与行明细，并通知列表页刷新 */
const refresh = async () => {
  await Promise.all([loadResult(), loadRows()]);
};

onMounted(async () => {
  await loadResult();
  await selectType('SUSPECTED');
});

const submit = async (): Promise<string> => {
  // 关闭弹窗触发列表页刷新（BatchPanel 监听 batchResult 关闭后重查）
  closeDialog();
  return `导入任务 ${result.value.jobId} 处理完成：Exact 关联已有，Suspected 治理，New 审批后生成 One ID`;
};

defineExpose({ submit });
</script>

<style lang="scss" scoped>
.clickable {
  cursor: pointer;

  &:hover {
    border-color: var(--el-color-primary-light-5);
    background: var(--g-card);
  }

  &.disabled {
    cursor: not-allowed;
    opacity: 0.6;

    &:hover {
      border-color: var(--g-divider);
      background: var(--g-content);
    }
  }
}

.detail-block {
  margin-top: 12px;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.detail-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--g-text);
}

.empty-hint {
  font-size: 12px;
  color: var(--g-text2);
}

.impact {
  margin-top: 12px;
  padding: 10px 12px;
  background: #eaf2f8;
  border-radius: 6px;
  font-size: 12px;
  color: var(--g-text2);
}
</style>
