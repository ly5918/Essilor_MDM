<template>
  <section class="page">
    <!-- 操作区 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 20px' }">
      <template #header>
        <div class="card-head">
          <span class="card-title">操作区</span>
          <div class="card-toolbar-right">
            <el-button plain icon="Download" @click="openDialog('template')">下载模板</el-button>
            <el-button type="primary" plain icon="Plus" @click="openDialog('batchResult')">新建导入任务</el-button>
          </div>
        </div>
      </template>
      <p class="text-tip">点击「新建导入任务」可模拟上传并查看导入结果；点击「下载模板」可获取标准导入模板。</p>
    </el-card>

    <!-- 导入任务列表 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '0' }">
      <template #header><span class="card-title">导入任务列表</span></template>
      <el-table v-loading="loading" border :data="jobs" class="data-table">
        <el-table-column label="Job ID" prop="jobId" width="140" />
        <el-table-column label="文件" prop="fileName" min-width="260" show-overflow-tooltip />
        <el-table-column label="总行数" prop="totalRows" width="110" align="center" />
        <el-table-column label="状态" width="200" align="center">
          <template #default="{ row }">
            <el-tag :type="IMPORT_STATUS_MAP[row.status].type" size="small">{{ IMPORT_STATUS_MAP[row.status].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog('batchResult', { jobId: row.jobId })">查看结果</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { listImportJobs } from '@/api/demo/cmdPoc';
import type { ImportJobVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { IMPORT_STATUS_MAP } from '../../constants/options';

defineOptions({ name: 'CmdPocBatchPanel' });

const { openDialog } = useCmdPoc();

const loading = ref(false);
const jobs = ref<ImportJobVO[]>([]);

const loadJobs = async () => {
  loading.value = true;
  try {
    jobs.value = await listImportJobs();
  } finally {
    loading.value = false;
  }
};

onMounted(loadJobs);
</script>

<style lang="scss" scoped>
.text-tip {
  margin: 0;
  font-size: 13px;
  color: var(--g-text2);
}
</style>
