<template>
  <section class="page">
    <!-- 审计事件：原型 auditPage 为 标题 + 表格，此处补充「贯通 ID 检索」能力 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '0' }">
      <template #header>
        <div class="au-header">
          <span class="card-title">审计事件</span>
          <div class="au-tools">
            <el-input
              v-model="keyword"
              placeholder="One ID / 申请编号 / 事件 / 操作人"
              clearable
              style="width: 280px"
              @keyup.enter="onSearch"
              @clear="onSearch"
            />
            <el-button type="primary" plain icon="Search" @click="onSearch">查询</el-button>
            <span class="au-count">共 {{ events.length }} 条</span>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" border :data="events" class="data-table">
        <el-table-column label="事件编号" prop="id" width="165" />
        <el-table-column label="时间" prop="time" width="120" align="center" />
        <el-table-column label="事件" prop="event" min-width="240" show-overflow-tooltip />
        <el-table-column label="One ID" prop="oneId" width="140">
          <template #default="{ row }">
            <span v-if="row.oneId" class="au-oneid">{{ row.oneId }}</span>
            <span v-else class="au-oneid-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column label="关联单号" prop="bizId" width="165">
          <template #default="{ row }">
            <span v-if="row.bizId">{{ row.bizId }}</span>
            <span v-else class="au-oneid-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" prop="role" width="150" align="center" />
        <el-table-column label="结果" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="AUDIT_RESULT_MAP[row.result].type" size="small">{{ AUDIT_RESULT_MAP[row.result].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="traceable(row).oneId" link type="primary" size="small" @click="onTrace(traceable(row))">复制ID</el-button>
            <span v-else class="au-oneid-empty">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { listAuditEvents } from '@/api/demo/cmdPoc';
import type { AuditEventVO } from '@/api/demo/cmdPoc/types';
import { AUDIT_RESULT_MAP } from '../../constants/options';

defineOptions({ name: 'CmdPocAuditPanel' });

const loading = ref(false);
const keyword = ref('');
const events = ref<AuditEventVO[]>([]);

const load = async () => {
  loading.value = true;
  try {
    events.value = await listAuditEvents(keyword.value);
  } finally {
    loading.value = false;
  }
};

/** 查询：One ID / 申请编号 / 事件 / 操作人（后端 keyword 已覆盖全部关键字段） */
const onSearch = async () => {
  await load();
  const kw = keyword.value.trim();
  if (kw) {
    ElMessage.info(`已按「${kw}」检索到 ${events.value.length} 条审计事件`);
  }
};

/** 行数据类型由 el-table 统一为 DefaultRow，此处收敛断言，保证模板调用无需类型体操 */
const traceable = (row: unknown) => row as AuditEventVO;

/** 复制 One ID 到剪贴板，便于粘贴到任意页面搜索框做跨页面追溯 */
const onTrace = async (row: AuditEventVO) => {
  try {
    await navigator.clipboard.writeText(row.oneId);
    ElMessage.success(`已复制 One ID：${row.oneId}，可粘贴到任意页面搜索框查询`);
  } catch {
    ElMessage.info(`该客户的 One ID 为：${row.oneId}`);
  }
};

onMounted(load);
</script>

<style scoped lang="scss">
.au-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.au-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.au-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 贯穿 ID：等宽字体高亮，便于跨页面人工比对 */
.au-oneid {
  font-family: 'Cascadia Mono', Consolas, 'Courier New', monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.au-oneid-empty {
  color: var(--el-text-color-placeholder);
}
</style>
