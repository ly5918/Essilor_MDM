<template>
  <div class="poc-dialog-body">
    <div class="kpi-row">
      <div v-for="item in kpis" :key="item.label" class="kpi">
        <b>{{ item.value }}</b>
        <span>{{ item.label }}</span>
      </div>
    </div>

    <pre class="db-panel">{{ logText }}</pre>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { listIntegrationRuns, retryIntegration } from '@/api/demo/cmdPoc';
import type { IntegrationRunVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocIntegrationDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const runId = computed(() => (props.payload?.runId as string) ?? 'OUT-008');

const runs = ref<IntegrationRunVO[]>([]);
const currentRun = computed(() => runs.value.find(item => item.runId === runId.value));

/** 原型 KPI：OUT / Cloud / Failed，方向取简写（Outbound → OUT） */
const kpis = computed(() => [
  { label: '方向', value: currentRun.value?.direction === 'Outbound' ? 'OUT' : 'IN' },
  { label: '目标', value: currentRun.value?.system ?? 'Cloud' },
  { label: '状态', value: currentRun.value?.status ?? 'Failed' }
]);

const logText = computed(() =>
  [
    'HTTP 504 Gateway Timeout',
    `Run ID: ${currentRun.value?.runId ?? 'OUT-20260915-008'}`,
    `Record: ${currentRun.value?.record ?? 'GC-000128'}`,
    `Attempt: ${currentRun.value?.attempt ?? '1/3'}`
  ].join('\n')
);

const submit = async (): Promise<string> => retryIntegration(runId.value);

onMounted(async () => {
  runs.value = await listIntegrationRuns();
});

defineExpose({ submit });
</script>
