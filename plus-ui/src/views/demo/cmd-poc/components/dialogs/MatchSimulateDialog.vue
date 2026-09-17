<template>
  <div class="poc-dialog-body">
    <!-- 匹配置信度 KPI -->
    <div class="kpi-row">
      <div v-for="item in kpis" :key="item.label" class="kpi">
        <b>{{ item.value }}</b>
        <span>{{ item.label }}</span>
      </div>
    </div>

    <el-table border :data="rules" class="data-table">
      <el-table-column label="维度" prop="dimension" min-width="200" />
      <el-table-column label="作用" prop="role" width="120" align="center" />
      <el-table-column label="结果" prop="result" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="resultType(row.result)" size="small">{{ row.result }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { listMatchRules, simulateMatch } from '@/api/demo/cmdPoc';
import type { MatchRuleVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocMatchSimulateDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const rules = ref<MatchRuleVO[]>([]);

/** 原型 KPI：信用代码 100% / 经营地址 88% / 建议结果 Suspect */
const kpis = computed(() => [
  { label: '信用代码', value: rules.value[0]?.threshold ?? '100%' },
  { label: '经营地址', value: rules.value[1]?.result ?? '88%' },
  { label: '建议结果', value: 'Suspect' }
]);

const resultType = (result: string) => {
  if (result === 'Exact') return 'success';
  if (result === 'Similar') return 'warning';
  return 'primary';
};

const submit = async (): Promise<string> => {
  rules.value = await simulateMatch();
  return '匹配规则模拟测试完成，建议结果：Suspect';
};

onMounted(async () => {
  rules.value = await listMatchRules();
});

defineExpose({ submit });
</script>
