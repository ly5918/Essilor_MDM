<template>
  <div class="poc-dialog-body">
    <!-- 命中统计 -->
    <div class="kpi-row">
      <div v-for="item in kpis" :key="item.label" class="kpi">
        <b>{{ item.value }}</b>
        <span>{{ item.label }}</span>
      </div>
    </div>

    <el-table border :data="rows" class="data-table" max-height="360">
      <el-table-column label="One ID" prop="oneId" width="130" />
      <el-table-column label="名称" prop="legalName" min-width="200" show-overflow-tooltip />
      <el-table-column label="BU" prop="bu" min-width="150" />
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="CUSTOMER_STATUS_MAP[row.status].type" size="small">{{ CUSTOMER_STATUS_MAP[row.status].label }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!rows.length" description="未命中客户，请调整查询条件" :image-size="70" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { CustomerVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { CUSTOMER_STATUS_MAP } from '../../constants/options';

defineOptions({ name: 'CmdPocSearchDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();
const { customers } = useCmdPoc();

/** 优先展示调用方传入的结果集，否则展示全部客户 */
const rows = computed<CustomerVO[]>(() => {
  const rowsFromPayload = props.payload?.rows as CustomerVO[] | undefined;
  return rowsFromPayload?.length ? rowsFromPayload : customers.value;
});

const kpis = computed(() => [
  { label: '命中客户', value: rows.value.length },
  { label: 'Active', value: rows.value.filter(row => row.status === 'active').length },
  { label: 'Pending', value: rows.value.filter(row => row.status === 'pending').length }
]);

const submit = async (): Promise<string> => `查询完成，命中 ${rows.value.length} 条客户`;

defineExpose({ submit });
</script>
