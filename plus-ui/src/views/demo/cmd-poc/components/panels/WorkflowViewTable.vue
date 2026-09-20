<template>
  <section class="page">
    <el-card class="page-card" shadow="never" :body-style="{ padding: '10px 16px 14px' }">
      <div class="wv-toolbar card-toolbar">
        <div class="wv-toolbar-left">
          <span class="wv-title">{{ meta.title }}</span>
          <span class="wv-count">共 {{ rows.length }} 条</span>
        </div>
        <div class="wv-toolbar-right">
          <el-input v-model="keyword" placeholder="One ID / 申请编号 / 客户主题" clearable style="width: 240px" @keyup.enter="load" @clear="load" />
          <el-button type="primary" plain icon="Refresh" @click="load">刷新</el-button>
        </div>
      </div>

      <!-- 工作项：等待人工处理（对齐 Deepblue「工作项」列结构） -->
      <el-table v-if="view === 'workitem'" v-loading="loading" border :data="rows" class="data-table">
        <el-table-column label="图形" width="90" align="center" fixed="left">
          <template #default="{ row }">
            <el-button link type="primary" size="small" icon="Share" @click="onViewGraph(row)">泳道图</el-button>
          </template>
        </el-table-column>
        <el-table-column label="One ID" width="155" fixed="left">
          <template #default="{ row }">
            <span v-if="row.oneId" class="wv-oneid">{{ row.oneId }}</span>
            <span v-else class="wv-oneid-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column label="类型（当前节点）" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="warning">Wait for user</el-tag>
            <span class="wv-sub">{{ row.currentNodeName ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="优先" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.priority)" size="small" effect="plain">{{ row.priority ?? '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bizType ?? '—' }} - {{ row.bizTitle ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="数据工作流" prop="sceneCode" width="160" show-overflow-tooltip />
        <el-table-column label="用户" prop="assigneeName" width="100" />
        <el-table-column label="建立日期" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="注释" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.opinion || '—' }}</template>
        </el-table-column>
        <el-table-column label="模板流程" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sceneName ?? row.sceneCode }}</template>
        </el-table-column>
        <el-table-column label="SLA" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="slaTagType(row.slaState)" size="small" effect="plain">{{ slaLabel(row.slaState) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 已激活工作流：引擎已启动且未到终态（对齐 Deepblue「活跃的工作流」列结构） -->
      <el-table v-else-if="view === 'active'" v-loading="loading" border :data="rows" class="data-table">
        <el-table-column label="图形" width="90" align="center" fixed="left">
          <template #default="{ row }">
            <el-button link type="primary" size="small" icon="Share" @click="onViewGraph(row)">泳道图</el-button>
          </template>
        </el-table-column>
        <el-table-column label="One ID" width="155" fixed="left">
          <template #default="{ row }">
            <span v-if="row.oneId" class="wv-oneid">{{ row.oneId }}</span>
            <span v-else class="wv-oneid-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column label="标签" min-width="185" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bizType ?? '—' }}: {{ row.taskNo }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" prop="bizTitle" min-width="190" show-overflow-tooltip />
        <el-table-column label="创作者" prop="applicantName" width="100" />
        <el-table-column label="父工作流" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sceneName ?? row.sceneCode }}</template>
        </el-table-column>
        <el-table-column label="建立日期" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="进度" width="140">
          <template #default="{ row }">
            <el-progress :percentage="row.progressPercent ?? 0" :stroke-width="10" :text-inside="true" />
          </template>
        </el-table-column>
        <el-table-column label="当前步骤" prop="currentNodeName" min-width="140" show-overflow-tooltip />
        <el-table-column label="当前状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.engineBound" type="success" size="small" effect="plain">启用</el-tag>
            <el-tag v-else type="info" size="small" effect="plain">未启动</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 已完成的工作流：业务终态（对齐 Deepblue「已完成的工作流」列结构） -->
      <el-table v-else v-loading="loading" border :data="rows" class="data-table">
        <el-table-column label="图形" width="90" align="center" fixed="left">
          <template #default="{ row }">
            <el-button link type="primary" size="small" icon="Share" @click="onViewGraph(row)">泳道图</el-button>
          </template>
        </el-table-column>
        <el-table-column label="One ID" width="155" fixed="left">
          <template #default="{ row }">
            <span v-if="row.oneId" class="wv-oneid">{{ row.oneId }}</span>
            <span v-else class="wv-oneid-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column label="标签" min-width="185" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bizType ?? '—' }}: {{ row.taskNo }}</template>
        </el-table-column>
        <el-table-column label="描述" prop="bizTitle" min-width="190" show-overflow-tooltip />
        <el-table-column label="创作者" prop="applicantName" width="100" />
        <el-table-column label="父工作流" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sceneName ?? row.sceneCode }}</template>
        </el-table-column>
        <el-table-column label="完成日期" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.finishTime ?? row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注释" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.opinion || '—' }}</template>
        </el-table-column>
        <el-table-column label="当前状态" width="100" align="center">
          <template #default>
            <el-tag type="info" size="small" effect="plain">归档</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { listFlowInstances } from '@/api/demo/cmdPoc';
import type { FlowInstanceVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocWorkflowViewTable' });

/**
 * Deepblue 工作流三视图共享表格（工作项 / 已激活工作流 / 已完成的工作流）。
 * 列结构对齐需求截图（Novartis Deepblue - Customer Data Management China），
 * 「One ID」列承载贯穿 ID，位置紧随「图形」之后（fixed），保证不横向滚动也能看到，
 * 可按该 ID 到任意页面搜索框查询。
 */
const props = defineProps<{
  /** 视图类型：workitem 待办工作项 / active 运行中 / done 已完成 */
  view: 'workitem' | 'active' | 'done';
}>();

const META: Record<string, { title: string }> = {
  workitem: { title: '工作项' },
  active: { title: '已激活工作流' },
  done: { title: '已完成的工作流' }
};
const meta = META[props.view];

const loading = ref(false);
const rows = ref<FlowInstanceVO[]>([]);
const keyword = ref('');

const { openDialog } = useCmdPoc();

const load = async () => {
  loading.value = true;
  try {
    const query =
      props.view === 'workitem'
        ? { status: 'PENDING', keyword: keyword.value.trim() || undefined }
        : { runState: props.view === 'active' ? 'RUNNING' : 'DONE', keyword: keyword.value.trim() || undefined };
    const page = await listFlowInstances(query);
    rows.value = page.rows ?? [];
  } finally {
    loading.value = false;
  }
};

/** 泳道图（实例视图：按该次执行的实际进度点亮节点） */
const onViewGraph = (row: unknown) => {
  const inst = row as FlowInstanceVO;
  openDialog('flowGraph', {
    sceneCode: inst.sceneCode,
    sceneName: inst.sceneName ?? inst.sceneCode,
    flowCode: inst.flowCode,
    taskNo: inst.taskNo
  });
};

const RISK_TAG: Record<string, 'danger' | 'warning' | 'info'> = { High: 'danger', Medium: 'warning', Low: 'info' };
const riskTagType = (v?: string) => RISK_TAG[v ?? ''] ?? 'info';

const STATUS_TEXT: Record<string, string> = {
  PENDING: '进行中',
  APPROVED: '已批准',
  COMPLETED: '已完成',
  REJECTED: '已拒绝',
  RETURNED: '已退回',
  ESCALATED: '已升级GC',
  CANCELLED: '已取消'
};
const STATUS_TAG: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  PENDING: 'warning',
  APPROVED: 'success',
  COMPLETED: 'success',
  REJECTED: 'danger',
  RETURNED: 'danger',
  ESCALATED: 'warning',
  CANCELLED: 'info'
};
const statusLabel = (v: string) => STATUS_TEXT[v] ?? v ?? '—';
const statusTagType = (v: string) => STATUS_TAG[v] ?? 'info';

const slaLabel = (v?: string) => (v === 'OVERDUE' ? '已超时' : v === 'DUE_SOON' ? '即将超时' : '正常');
const slaTagType = (v?: string) => (v === 'OVERDUE' ? 'danger' : v === 'DUE_SOON' ? 'warning' : 'success');

const formatTime = (v?: string) => (v ? String(v).replace('T', ' ').slice(0, 16) : '—');

onMounted(load);
</script>

<style scoped lang="scss">
.wv-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;

  &-left {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.wv-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.wv-count {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.wv-sub {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

/* 贯穿 ID（One ID）：等宽字体高亮，便于跨页面人工比对 */
.wv-oneid {
  font-family: 'Cascadia Mono', Consolas, 'Courier New', monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.wv-oneid-empty {
  color: var(--el-text-color-placeholder);
}
</style>
