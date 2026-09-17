<template>
  <div class="poc-dialog-body">
    <div class="kpi-row">
      <div
        v-for="item in kpis"
        :key="item.label"
        class="kpi clickable"
        :class="{ disabled: item.value === 0 }"
        @click="item.value > 0 && showDetails(item.label)"
      >
        <b>{{ item.value }}</b>
        <span>{{ item.label }} · 点击查看明细</span>
      </div>
    </div>

    <el-table border :data="result.routes" class="data-table">
      <el-table-column label="结果" prop="result" min-width="120" />
      <el-table-column label="处理方式" prop="handling" min-width="200" />
      <el-table-column label="责任角色" prop="owner" min-width="160" />
      <el-table-column label="明细" min-width="140" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetails(row.result)">{{ row.detail }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="impact">统计卡片数字及表格中的记录数均支持点击，并打开对应记录明细。</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getBatchResult } from '@/api/demo/cmdPoc';
import type { BatchResultVO, CustomerVO } from '@/api/demo/cmdPoc/types';
import { listCustomers } from '@/api/demo/cmdPoc';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocBatchResultDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const { openDialog } = useCmdPoc();

const result = ref<BatchResultVO>({
  jobId: '-',
  exact: 0,
  suspected: 0,
  created: 0,
  review: 0,
  invalid: 0,
  routes: []
});

/** 原型 KPI：Exact 42 / Suspected 18 / New 26 / Review 16 / Invalid 14 */
const kpis = computed(() => [
  { label: 'Exact', value: result.value.exact },
  { label: 'Suspected', value: result.value.suspected },
  { label: 'New', value: result.value.created },
  { label: 'Review', value: result.value.review },
  { label: 'Invalid', value: result.value.invalid }
]);

onMounted(async () => {
  result.value = await getBatchResult((props.payload?.jobId as string) ?? 'IMP-001');
});

/** 点击 KPI 或 明细 下钻到对应分类记录 */
const showDetails = async (type: string) => {
  const customers = await listCustomers();
  let rows: CustomerVO[] = [];
  switch (type) {
    case 'Exact':
      rows = customers.filter(c => c.oneId && c.status === 'active').slice(0, result.value.exact);
      break;
    case 'Suspected':
      rows = customers.filter(c => c.status === 'pending').slice(0, result.value.suspected);
      break;
    case 'Review':
      rows = customers.filter(c => c.status === 'pending').slice(0, result.value.review);
      break;
    case 'New':
      rows = customers.filter(c => c.oneId.startsWith('PENDING') || c.status === 'pending').slice(0, result.value.created);
      break;
    case 'Invalid':
      rows = customers.filter(c => c.status === 'inactive').slice(0, result.value.invalid);
      break;
    default:
      rows = customers.slice(0, 5);
  }
  openDialog('search', { rows: rows.map(r => ({ ...r })), keyword: `${type} 记录明细` });
};

const submit = async (): Promise<string> => `导入任务 ${result.value.jobId} 已确认，Suspected 进入人工治理`;

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

.impact {
  margin-top: 12px;
  padding: 10px 12px;
  background: #eaf2f8;
  border-radius: 6px;
  font-size: 12px;
  color: var(--g-text2);
}
</style>
