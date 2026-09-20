<template>
  <section class="page">
    <el-card class="page-card" shadow="never" :body-style="{ padding: '10px 16px 14px' }">
      <el-tabs v-model="activeTab" class="wc-tabs">
        <!-- ① 工作流定义：全部 CMD 业务场景（V6.1 总设计业务流） -->
        <el-tab-pane name="def">
          <template #label>
            <span class="wc-tab-label">工作流定义</span>
            <el-tag size="small" type="info" effect="plain">{{ scenes.length }}</el-tag>
          </template>

          <div class="wc-toolbar card-toolbar">
            <div class="wc-toolbar-left">
              <span class="wc-count">共 {{ scenes.length }} 条 CMD 业务工作流</span>
              <el-tag v-if="deployedCount === scenes.length" type="success" size="small">全部已部署</el-tag>
              <el-tag v-else type="warning" size="small">{{ deployedCount }}/{{ scenes.length }} 已部署</el-tag>
            </div>
            <div class="wc-toolbar-right">
              <el-input v-model="sceneKeyword" placeholder="场景名称 / 流程编码" clearable style="width: 220px" @keyup.enter="loadScenes" />
              <el-button type="primary" plain icon="Refresh" @click="loadScenes">刷新</el-button>
            </div>
          </div>

          <el-table v-loading="sceneLoading" border :data="visibleScenes" class="data-table">
            <el-table-column label="场景编码" prop="sceneCode" width="150" />
            <el-table-column label="业务场景（V6.1）" prop="sceneName" min-width="150" show-overflow-tooltip />
            <el-table-column label="Warm-Flow 流程名称" prop="flowName" min-width="150" show-overflow-tooltip />
            <el-table-column label="流程编码" prop="flowCode" min-width="165" show-overflow-tooltip />
            <el-table-column label="SLA" width="80" align="center">
              <template #default="{ row }">{{ row.slaHours ?? '—' }}h</template>
            </el-table-column>
            <el-table-column label="版本" width="70" align="center">
              <template #default="{ row }">v{{ row.version ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="泳道节点" prop="nodeCount" width="85" align="center" />
            <el-table-column label="部署状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.deployed" type="success" size="small">已部署</el-tag>
                <el-tag v-else type="info" size="small">未部署</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" icon="Share" :disabled="!row.deployed" @click="onViewSceneGraph(row)">
                  Graph 泳道图
                </el-button>
                <el-button v-if="!row.deployed" link type="warning" size="small" icon="Upload" @click="onDeploy(row)">
                  部署
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ② 流程实例记录：每一次执行过的工作流 -->
        <el-tab-pane name="inst">
          <template #label>
            <span class="wc-tab-label">流程实例记录</span>
            <el-tag size="small" type="info" effect="plain">{{ instances.length }}</el-tag>
          </template>

          <div class="wc-toolbar card-toolbar">
            <div class="wc-toolbar-left">
              <el-radio-group v-model="runState" size="small" @change="loadInstances">
                <el-radio-button value="">全部</el-radio-button>
                <el-radio-button value="RUNNING">进行中</el-radio-button>
                <el-radio-button value="DONE">已完成</el-radio-button>
                <el-radio-button value="NEW">未启动</el-radio-button>
              </el-radio-group>
              <span class="wc-count">共 {{ instances.length }} 条执行记录</span>
            </div>
            <div class="wc-toolbar-right">
              <el-input v-model="instKeyword" placeholder="One ID / 申请编号 / 客户主题" clearable style="width: 240px" @keyup.enter="loadInstances" />
              <el-button type="primary" plain icon="Refresh" @click="loadInstances">刷新</el-button>
            </div>
          </div>

          <el-table v-loading="instLoading" border :data="instances" class="data-table">
            <el-table-column label="Graph" width="120" align="center" fixed="left">
              <template #default="{ row }">
                <el-button link type="primary" size="small" icon="Share" @click="onViewInstanceGraph(row)">泳道图</el-button>
              </template>
            </el-table-column>
            <el-table-column label="申请编号" prop="taskNo" width="165" />
            <el-table-column label="One ID" prop="oneId" width="140">
              <template #default="{ row }">
                <span v-if="row.oneId" class="wc-oneid">{{ row.oneId }}</span>
                <span v-else class="wc-oneid-empty">—</span>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="riskTagType(row.priority)" size="small" effect="plain">{{ row.priority ?? '—' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="客户主题（描述）" prop="bizTitle" min-width="200" show-overflow-tooltip />
            <el-table-column label="业务类型" prop="bizType" width="110" />
            <el-table-column label="发起人" prop="applicantName" width="100" />
            <el-table-column label="父流程（场景）" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.sceneName ?? row.sceneCode }}</template>
            </el-table-column>
            <el-table-column label="当前节点" prop="currentNodeName" min-width="140" show-overflow-tooltip />
            <el-table-column label="进度" width="150">
              <template #default="{ row }">
                <el-progress :percentage="row.progressPercent ?? 0" :stroke-width="10" :text-inside="true" />
                <span class="wc-progress-text">{{ row.completedSteps ?? 0 }}/{{ row.totalSteps ?? 0 }} 步</span>
              </template>
            </el-table-column>
            <el-table-column label="运行状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="引擎实例" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.engineBound" type="success" size="small" effect="plain">已接入</el-tag>
                <el-tag v-else type="info" size="small" effect="plain">未启动</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建日期" width="160" align="center">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { deployFlowScene, listFlowInstances, listFlowScenes } from '@/api/demo/cmdPoc';
import type { FlowInstanceVO, FlowSceneVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocWorkflowCenterPanel' });

const { openDialog } = useCmdPoc();

const activeTab = ref<'def' | 'inst'>('def');

/* ---------------- 工作流定义 ---------------- */
const sceneLoading = ref(false);
const scenes = ref<FlowSceneVO[]>([]);
const sceneKeyword = ref('');

const deployedCount = computed(() => scenes.value.filter(s => s.deployed).length);

const visibleScenes = computed(() => {
  const kw = sceneKeyword.value.trim().toLowerCase();
  if (!kw) return scenes.value;
  return scenes.value.filter(
    s =>
      s.sceneName?.toLowerCase().includes(kw) ||
      s.flowCode?.toLowerCase().includes(kw) ||
      s.sceneCode.toLowerCase().includes(kw)
  );
});

const loadScenes = async () => {
  sceneLoading.value = true;
  try {
    scenes.value = await listFlowScenes();
  } finally {
    sceneLoading.value = false;
  }
};

/** 查看蓝图：定义视图（节点全部待执行） */
const onViewSceneGraph = (row: unknown) => {
  const scene = row as FlowSceneVO;
  if (!scene.deployed) {
    ElMessage.warning('该流程尚未部署到 Warm-Flow，请先点击「部署」');
    return;
  }
  openDialog('flowGraph', { sceneCode: scene.sceneCode, sceneName: scene.sceneName, flowCode: scene.flowCode });
};

/** 部署单个场景到 Warm-Flow（幂等） */
const onDeploy = async (row: unknown) => {
  const scene = row as FlowSceneVO;
  sceneLoading.value = true;
  try {
    await deployFlowScene(scene.sceneCode);
    ElMessage.success(`流程「${scene.sceneName}」已部署到 Warm-Flow`);
    await loadScenes();
  } catch {
    ElMessage.error('部署失败，请检查后端日志');
  } finally {
    sceneLoading.value = false;
  }
};

/* ---------------- 流程实例记录 ---------------- */
const instLoading = ref(false);
const instances = ref<FlowInstanceVO[]>([]);
const instKeyword = ref('');
const runState = ref('');

const loadInstances = async () => {
  instLoading.value = true;
  try {
    const page = await listFlowInstances({
      keyword: instKeyword.value.trim() || undefined,
      runState: runState.value || undefined
    });
    instances.value = page.rows ?? [];
  } finally {
    instLoading.value = false;
  }
};

/** 查看某次执行的泳道图：实例视图（按实际进度点亮节点） */
const onViewInstanceGraph = (row: unknown) => {
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
  APPROVED: '已完成',
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

const formatTime = (v?: string) => (v ? String(v).replace('T', ' ').slice(0, 16) : '—');

onMounted(async () => {
  await Promise.all([loadScenes(), loadInstances()]);
});
</script>

<style scoped lang="scss">
.wc-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 10px;
  }

  :deep(.el-tabs__item) {
    height: 38px;
  }
}

.wc-tab-label {
  margin-right: 6px;
}

.wc-toolbar {
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

.wc-count {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.wc-progress-text {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

/* 贯穿 ID：等宽字体高亮，便于跨页面人工比对 */
.wc-oneid {
  font-family: 'Cascadia Mono', Consolas, 'Courier New', monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.wc-oneid-empty {
  color: var(--el-text-color-placeholder);
}

/* 工作项「类型」列的第二行：当前节点名 */
.wi-sub {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
</style>
