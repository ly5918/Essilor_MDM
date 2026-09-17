<template>
  <div class="poc-dialog-body">
    <div class="kpi-row">
      <div v-for="item in kpis" :key="item.label" class="kpi">
        <b>{{ item.value }}</b>
        <span>{{ item.label }}</span>
      </div>
    </div>

    <el-table border :data="result.routes" class="data-table">
      <el-table-column label="结果" prop="result" min-width="140" />
      <el-table-column label="处理方式" prop="handling" min-width="200" />
      <el-table-column label="责任角色" prop="owner" min-width="160" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getBatchResult } from '@/api/demo/cmdPoc';
import type { BatchResultVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocBatchResultDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const result = ref<BatchResultVO>({ jobId: '-', exact: 0, suspected: 0, created: 0, invalid: 0, routes: [] });

/** 原型 KPI：Exact 42 / Suspected 18 / New 26（Invalid 计入分流表，不单独展示） */
const kpis = computed(() => [
  { label: 'Exact', value: result.value.exact },
  { label: 'Suspected', value: result.value.suspected },
  { label: 'New', value: result.value.created }
]);

onMounted(async () => {
  result.value = await getBatchResult((props.payload?.jobId as string) ?? 'IMP-001');
});

const submit = async (): Promise<string> => `导入任务 ${result.value.jobId} 已确认，Suspected 进入人工治理`;

defineExpose({ submit });
</script>
