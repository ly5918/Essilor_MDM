<template>
  <section class="page">
    <!-- POC 覆盖检查 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '0' }">
      <template #header><span class="card-title">POC 覆盖检查</span></template>
      <el-table v-loading="loading" border :data="items" class="data-table">
        <el-table-column label="Demo Topic" prop="topic" min-width="220" />
        <el-table-column label="状态" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="COVERAGE_STATUS_MAP[row.status].type" size="small">{{ COVERAGE_STATUS_MAP[row.status].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前证据 / 缺口" prop="evidence" min-width="460" show-overflow-tooltip />
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { listCoverage } from '@/api/demo/cmdPoc';
import type { CoverageItemVO } from '@/api/demo/cmdPoc/types';
import { COVERAGE_STATUS_MAP } from '../../constants/options';

defineOptions({ name: 'CmdPocCoveragePanel' });

const loading = ref(false);
const items = ref<CoverageItemVO[]>([]);

onMounted(async () => {
  loading.value = true;
  try {
    items.value = await listCoverage();
  } finally {
    loading.value = false;
  }
});
</script>
