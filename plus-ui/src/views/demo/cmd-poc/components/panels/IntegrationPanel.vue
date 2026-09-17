<template>
  <section class="page">
    <!-- 集成运行记录 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '0' }">
      <template #header><span class="card-title">集成运行记录</span></template>
      <el-table v-loading="loading" border :data="runs" class="data-table">
        <el-table-column label="Run ID" prop="runId" width="140" />
        <el-table-column label="方向" prop="direction" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'Outbound' ? 'primary' : 'success'" size="small" effect="plain">
              {{ row.direction }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="系统" prop="system" width="140" align="center" />
        <el-table-column label="状态" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="INTEGRATION_STATUS_MAP[row.status].type" size="small">
              {{ INTEGRATION_STATUS_MAP[row.status].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog('integration', { runId: row.runId })">查看 / Retry</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { listIntegrationRuns } from '@/api/demo/cmdPoc';
import type { IntegrationRunVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { INTEGRATION_STATUS_MAP } from '../../constants/options';

defineOptions({ name: 'CmdPocIntegrationPanel' });

const { openDialog } = useCmdPoc();

const loading = ref(false);
const runs = ref<IntegrationRunVO[]>([]);

const loadRuns = async () => {
  loading.value = true;
  try {
    runs.value = await listIntegrationRuns();
  } finally {
    loading.value = false;
  }
};

onMounted(loadRuns);
</script>
