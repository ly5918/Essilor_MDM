<template>
  <div class="poc-dialog-body">
    <div class="two-col">
      <!-- 业务视图 -->
      <el-card shadow="never" :body-style="{ padding: '0' }">
        <template #header><span class="card-title">业务视图</span></template>
        <el-table border :data="result.businessView" class="data-table" :show-header="false">
          <el-table-column prop="key" min-width="160" />
          <el-table-column prop="value" min-width="180" />
        </el-table>
      </el-card>

      <!-- 后台记录示意 -->
      <el-card shadow="never" :body-style="{ padding: '12px' }">
        <template #header><span class="card-title">后台记录示意</span></template>
        <pre class="db-panel">{{ dbText }}</pre>
      </el-card>
    </div>

    <el-alert
      class="m-t-12"
      type="success"
      :closable="false"
      show-icon
      title="One ID和历史版本继续可查，下游接收状态变更而非物理删除。"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getDeactivateResult } from '@/api/demo/cmdPoc';
import type { DeactivateResultVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocDeactivateResultDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const result = ref<DeactivateResultVO>({ businessView: [], dbRecords: [], versions: [] });
const dbText = computed(() => result.value.dbRecords.join('\n'));

onMounted(async () => {
  result.value = await getDeactivateResult((props.payload?.oneId as string) ?? 'GC-000245');
});
</script>
