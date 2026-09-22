<template>
  <section class="page list-page">
    <!-- 批次总览（对应设计「批次任务详情」：Completed / Partial / Failed、数量与分流待办） -->
    <div class="kpi-row">
      <div
        v-for="item in kpis"
        :key="item.label"
        class="kpi"
        :style="{ '--kpi-color': item.color }"
      >
        <b>{{ item.value }}</b>
        <span>{{ item.label }}</span>
      </div>
    </div>

    <!-- 导入任务列表（下载模板 / 新建导入任务按钮在页标题区，与原型一致） -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '0' }">
      <template #header><span class="card-title">导入任务列表</span></template>
      <!-- 全列展示（业务上下文/总行数/待办/提交人/提交时间独立成列） -->
      <el-table
        ref="tableRef"
        v-loading="loading"
        border
        :data="jobs"
        :height="tableHeight"
        class="data-table"
      >
        <el-table-column label="Job ID" prop="jobId" width="125" />
        <el-table-column label="文件" prop="fileName" min-width="180" show-overflow-tooltip />
        <el-table-column label="业务上下文" width="130">
          <template #default="{ row }">
            <span>{{ [row.scene, row.buScope].filter(Boolean).join(' · ') || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总行数" prop="totalRows" width="75" align="center" />
        <el-table-column label="状态" width="130" align="center">
          <template #default="{ row }">
            <el-tooltip :content="statusTip(row)" placement="top">
              <el-tag :type="IMPORT_STATUS_MAP[row.status]?.type ?? 'info'" size="small">
                {{ IMPORT_STATUS_MAP[row.status]?.label ?? row.status }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="待办" width="185" align="center">
          <template #default="{ row }">
            <span v-if="pendingOf(row).length" class="todo-chips">
              <span v-for="t in pendingOf(row)" :key="t" class="todo-chip">{{ t }}</span>
            </span>
            <span v-else class="todo-none">—</span>
          </template>
        </el-table-column>
        <el-table-column label="提交人" width="105" align="center">
          <template #default="{ row }">{{ row.submittedBy || '—' }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="145">
          <template #default="{ row }">{{ formatTime(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <span class="op-btns">
              <!-- 上传数据明细已并入「查看结果」弹窗（默认页签），操作列不再单列入口 -->
              <el-button link type="primary" @click="openResult(row)">查看结果</el-button>
            </span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页：固定在内容区底部（高度由 useListTableHeight 反推，不随列表长短浮动） -->
      <div class="pagination-container" v-if="total > 0">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadJobs"
          @current-change="loadJobs"
        />
      </div>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { listImportJobs } from '@/api/demo/cmdPoc';
import type { ImportJobVO, PageResult } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { useListTableHeight } from '../../composables/useListTableHeight';
import { IMPORT_STATUS_MAP } from '../../constants/options';

defineOptions({ name: 'CmdPocBatchPanel' });

const { openDialog, dialog } = useCmdPoc();

/** 表格高度自适应：分页条固定在内容区底部，位置不随列表长短浮动 */
const { tableRef, tableHeight, recalc } = useListTableHeight(70);

const loading = ref(false);
const jobs = ref<ImportJobVO[]>([]);
const pageNum = ref(1);
const pageSize = ref(10);
const total = ref(0);

const loadJobs = async () => {
  loading.value = true;
  try {
    const page: PageResult<ImportJobVO> = await listImportJobs(pageNum.value, pageSize.value);
    jobs.value = page.rows;
    total.value = page.total;
  } finally {
    loading.value = false;
    recalc();
  }
};

onMounted(loadJobs);

// 上传成功 / 结果弹窗内治理动作后关闭，均触发一次重查
watch(
  () => dialog.current,
  (cur, prev) => {
    if (cur === '' && (prev === 'batchUpload' || prev === 'batchResult')) {
      pageNum.value = 1;
      void loadJobs();
    }
  }
);

/** 批次总览：任务数 / 总行数 / 四类分流合计（当前页口径，演示足够） */
const kpis = computed(() => [
  { label: '导入任务', value: total.value, color: '#176c9f' },
  { label: '总行数', value: jobs.value.reduce((sum, j) => sum + (j.totalRows ?? 0), 0), color: '#547f9f' },
  { label: 'Exact 关联', value: jobs.value.reduce((sum, j) => sum + (j.exactCount ?? 0), 0), color: '#2e8b57' },
  { label: 'Suspected 待治理', value: jobs.value.reduce((sum, j) => sum + (j.suspectedCount ?? 0), 0), color: '#b8791a' },
  { label: 'New 待审批', value: jobs.value.reduce((sum, j) => sum + (j.newCount ?? 0), 0), color: '#2f73ad' },
  { label: 'Invalid 退回修复', value: jobs.value.reduce((sum, j) => sum + (j.invalidCount ?? 0), 0), color: '#b4392f' }
]);

/** 状态悬停提示：待办（设计「批次任务详情」要求给出原因与待办）+ 任务备注 */
const statusTip = (row: ImportJobVO | Record<string, unknown>): string => {
  const item = row as ImportJobVO;
  const items = [...pendingOf(item)];
  if (item.remark) {
    items.push(item.remark);
  }
  return items.length ? items.join(' · ') : '无待办事项';
};

/** 待办（设计「批次任务详情」：数量、原因与待办）——疑似待治理 / New 待审批 */
const pendingOf = (row: ImportJobVO | Record<string, unknown>): string[] => {
  const item = row as ImportJobVO;
  const items: string[] = [];
  if ((item.suspectedCount ?? 0) > 0) {
    items.push(`治理 Suspected ${item.suspectedCount} 条`);
  }
  if ((item.newCount ?? 0) > 0 && item.status === 'Waiting for Review') {
    items.push(`审批 New ${item.newCount} 条`);
  }
  return items;
};

const formatTime = (value?: string) => (value ? String(value).replace('T', ' ').slice(0, 16) : '—');

/**
 * 查看结果：打开「批量结果分流」弹窗，默认页签=上传数据明细（原独立弹窗已合并），
 * 分流统计与治理在第二页签。泳道图入口已移除（与工作流定义同场景视图）。
 */
const openResult = (row: ImportJobVO | Record<string, unknown>) => {
  const item = row as ImportJobVO;
  openDialog('batchResult', {
    jobId: item.jobId,
    fileName: item.fileName,
    templateCode: item.templateCode,
    scene: item.scene,
    buScope: item.buScope,
    totalRows: item.totalRows
  });
};
</script>

<style lang="scss" scoped>
.text-tip {
  margin: 0;
  font-size: 13px;
  color: var(--g-text2);
}

/* 操作列两个链接按钮保持一行（全局 .el-button.is-link 有 width/min-width 约束，会挤压折行） */
.op-btns {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

/* 批次总览 KPI 卡（修复：此前类名无样式定义，退化为纯文本堆叠） */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.kpi {
  background: var(--g-card);
  border: 1px solid var(--g-divider);
  border-left: 3px solid var(--kpi-color, var(--el-color-primary));
  border-radius: 8px;
  padding: 12px 14px;

  b {
    display: block;
    font-size: 22px;
    font-weight: 700;
    color: var(--g-text);
    line-height: 1.2;
    margin-bottom: 4px;
  }

  span {
    display: block;
    font-size: 12px;
    color: var(--g-text2);
    line-height: 1.35;
  }
}

/* 待办列：小徽章（治理 Suspected / 审批 New） */
.todo-chips {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
}

.todo-chip {
  display: inline-block;
  font-size: 12px;
  color: #b8791a;
  background: #fdf5e6;
  border: 1px solid #f0dcb4;
  border-radius: 10px;
  padding: 1px 8px;
  white-space: nowrap;
}

.todo-none {
  color: var(--g-text2);
}
</style>
