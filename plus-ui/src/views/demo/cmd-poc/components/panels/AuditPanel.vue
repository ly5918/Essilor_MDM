<template>
  <section class="page">
    <!-- 审计事件：原型 auditPage 仅 标题 + 表格，无操作区 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '0' }">
      <template #header><span class="card-title">审计事件</span></template>
      <el-table v-loading="loading" border :data="events" class="data-table">
        <el-table-column label="时间" prop="time" width="120" align="center" />
        <el-table-column label="事件" prop="event" min-width="280" show-overflow-tooltip />
        <el-table-column label="角色" prop="role" width="180" align="center" />
        <el-table-column label="结果" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="AUDIT_RESULT_MAP[row.result].type" size="small">{{ AUDIT_RESULT_MAP[row.result].label }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { listAuditEvents } from '@/api/demo/cmdPoc';
import type { AuditEventVO } from '@/api/demo/cmdPoc/types';
import { AUDIT_RESULT_MAP } from '../../constants/options';

defineOptions({ name: 'CmdPocAuditPanel' });

const loading = ref(false);
const events = ref<AuditEventVO[]>([]);

onMounted(async () => {
  loading.value = true;
  try {
    events.value = await listAuditEvents();
  } finally {
    loading.value = false;
  }
});
</script>
