<template>
  <section class="page">
    <!-- KPI 概览：待我处理 / 临近SLA / 已超时 / 退回待补充 / 本周已处理 -->
    <div class="ap-kpis">
      <div v-for="kpi in kpis" :key="kpi.label" class="ap-kpi">
        <b>{{ kpi.value }}</b>
        <span>{{ kpi.label }} · Demo data</span>
      </div>
    </div>

    <!-- Tab：全部待办 / 审批任务 / 治理复核 / 升级与退回 / 我已处理 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '12px 16px' }">
      <el-tabs v-model="activeTab" class="ap-tabs" @tab-change="onTabChange">
        <el-tab-pane v-for="tab in TABS" :key="tab.key" :name="tab.key">
          <template #label>{{ tab.label }}</template>
        </el-tab-pane>
      </el-tabs>

      <!-- 统一筛选条 -->
      <div class="ap-filter card-toolbar">
        <el-select v-model="filter.taskType" placeholder="全部任务类型" clearable style="width: 160px">
          <el-option v-for="t in TASK_TYPE_OPTIONS" :key="t" :label="t" :value="t" />
        </el-select>
        <el-select v-model="filter.bu" placeholder="全部BU" clearable style="width: 140px">
          <el-option v-for="b in BU_OPTIONS" :key="b" :label="b" :value="b" />
        </el-select>
        <el-select v-model="filter.sla" placeholder="全部SLA" clearable style="width: 130px">
          <el-option label="临近SLA" value="near" />
          <el-option label="已超时" value="over" />
        </el-select>
        <el-select v-model="filter.risk" placeholder="全部风险" clearable style="width: 120px">
          <el-option label="High" value="High" />
          <el-option label="Medium" value="Medium" />
        </el-select>
        <el-input v-model="filter.keyword" placeholder="任务编号 / 客户名称" clearable style="width: 220px" @keyup.enter="applyFilter" />
        <el-button type="primary" plain icon="Search" @click="applyFilter">查询</el-button>
      </div>

      <!-- 列表 + 详情 双栏 -->
      <div class="ap-layout">
        <div class="ap-list">
          <div class="ap-list-title">{{ tabLabel }}</div>
          <el-table
            v-loading="loading"
            border
            :data="visibleTasks"
            class="data-table"
            highlight-current-row
            :current-row-key="selectedId"
            row-key="taskId"
            @current-change="onRowSelect"
          >
            <el-table-column label="任务编号" prop="taskId" width="150" />
            <el-table-column label="客户/主题" prop="customerName" min-width="190" show-overflow-tooltip />
            <el-table-column label="任务类型" prop="taskType" width="120" />
            <el-table-column label="来源" prop="source" width="110" />
            <el-table-column label="BU" prop="bu" width="120" />
            <el-table-column label="DQ" prop="dq" width="96" align="center" />
            <el-table-column label="Match" prop="match" width="110" align="center" />
            <el-table-column label="SLA" prop="sla" width="80" align="center" />
            <el-table-column label="风险" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="RISK_MAP[row.risk].type" size="small">{{ row.risk }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 右侧详情 -->
        <div class="ap-detail">
          <template v-if="detail">
            <div class="ap-detail-head">
              <b>{{ detail.id }}</b>
              <h3 class="ap-detail-name">{{ detail.name }}</h3>
              <div>
                <el-tag size="small" type="primary">{{ detail.scene }}</el-tag>
                <el-tag size="small" :type="isGc ? 'warning' : 'info'">{{ isGc ? 'GC Scope' : 'BU Scope' }}</el-tag>
              </div>
            </div>

            <div class="ap-detail-body">
              <h4>申请信息</h4>
              <div class="h-kv">
                <div>提交人</div>
                <div>{{ detail.submitter }}</div>
                <div>当前节点</div>
                <div>{{ detail.currentNode }}</div>
                <div>SLA</div>
                <div>{{ detail.sla }}</div>
              </div>

              <h4>自动检查结果</h4>
              <div class="ap-check">
                <div><b>Data Quality</b><br />{{ detail.dq }}</div>
                <div><b>Duplicate Check</b><br />{{ detail.duplicate }}</div>
              </div>

              <h4>{{ isGc ? 'GC治理决策' : 'BU初审判断' }}</h4>
              <div class="ap-decisions">
                <el-tag v-for="d in detail.decisions" :key="d" class="ap-tag" effect="plain">{{ d }}</el-tag>
              </div>

              <h4>治理证据</h4>
              <div class="ap-evidence">{{ detail.evidence }}</div>

              <h4>审批意见</h4>
              <el-input v-model="comment" type="textarea" :rows="3" placeholder="请输入审批意见或升级原因" />

              <div class="ap-actions">
                <el-button
                  v-for="act in detail.actions"
                  :key="act.key"
                  :type="act.type"
                  :loading="submitting"
                  @click="onAction(act)"
                >
                  {{ act.label }}
                </el-button>
              </div>
            </div>
          </template>
          <el-empty v-else description="选择左侧任务查看处理详情" />
        </div>
      </div>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import {
  getApprovalDone,
  getApprovalKpis,
  getApprovalReturned,
  getApprovalTaskDetail,
  listApprovalTasks
} from '@/api/demo/cmdPoc';
import type { ApprovalKpiVO, ApprovalTaskDetailVO, ApprovalTaskVO, RoleKey } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocApprovalPanel' });

const { roleKey } = useCmdPoc();
/** 仅 BU / GC 拥有审批菜单；其余角色理论上不会进入本面板 */
const isGc = computed(() => roleKey.value === 'gc');
const scope = computed<'bu' | 'gc'>(() => (isGc.value ? 'gc' : 'bu'));

const RISK_MAP: Record<string, { type: 'danger' | 'warning' | 'info' }> = {
  High: { type: 'danger' },
  Medium: { type: 'warning' },
  Low: { type: 'info' }
};

const TABS = [
  { key: 'all', label: '全部待办' },
  { key: 'approval', label: '审批任务' },
  { key: 'governance', label: '治理复核' },
  { key: 'returned', label: '升级与退回' },
  { key: 'done', label: '我已处理' }
] as const;

const TASK_TYPE_OPTIONS = ['客户创建', '客户变更', '逻辑停用', '层级关系', '疑似重复', '跨BU合并', 'DQ异常', '批量治理'];
const BU_OPTIONS = ['High End', 'Mainstream', 'Cross-BU'];

/** 审批任务类型（Tab=审批任务） */
const APPROVAL_TYPES = ['客户创建', '层级关系', '跨BU合并', '合并审批'];
/** 治理复核类型（Tab=治理复核） */
const GOVERNANCE_TYPES = ['DQ异常', '疑似重复', '批量治理', '多候选One ID'];

const kpis = ref<ApprovalKpiVO[]>([]);
const allTasks = ref<ApprovalTaskVO[]>([]);
const returnedTasks = ref<ApprovalTaskVO[]>([]);
const doneTasks = ref<ApprovalTaskVO[]>([]);
const loading = ref(false);
const activeTab = ref<(typeof TABS)[number]['key']>('all');
const selectedId = ref('');
const detail = ref<ApprovalTaskDetailVO | null>(null);
const comment = ref('');
const submitting = ref(false);

const filter = reactive({ taskType: '', bu: '', sla: '', risk: '', keyword: '' });

const tabLabel = computed(() => TABS.find(t => t.key === activeTab.value)?.label ?? '全部待办');

/** 当前 Tab 的基础数据集 */
const baseTasks = computed<ApprovalTaskVO[]>(() => {
  switch (activeTab.value) {
    case 'approval':
      return allTasks.value.filter(t => APPROVAL_TYPES.includes(t.taskType));
    case 'governance':
      return allTasks.value.filter(t => GOVERNANCE_TYPES.includes(t.taskType));
    case 'returned':
      return returnedTasks.value;
    case 'done':
      return doneTasks.value;
    default:
      return allTasks.value;
  }
});

/** 在基础数据集上叠加统一筛选条件（任务类型 / BU / 风险 / 关键词） */
const visibleTasks = computed<ApprovalTaskVO[]>(() => {
  const kw = filter.keyword.trim().toLowerCase();
  return baseTasks.value.filter(t => {
    const matchType = !filter.taskType || t.taskType === filter.taskType;
    const matchBu = !filter.bu || t.bu === filter.bu;
    const matchRisk = !filter.risk || t.risk === filter.risk;
    const matchKw = !kw || t.taskId.toLowerCase().includes(kw) || t.customerName.toLowerCase().includes(kw);
    return matchType && matchBu && matchRisk && matchKw;
  });
});

const onTabChange = () => {
  selectedId.value = '';
  detail.value = null;
  comment.value = '';
};

const applyFilter = () => {
  /* 筛选已通过 visibleTasks 计算属性实时生效，这里仅用于「查询」按钮的点击反馈 */
};

const onRowSelect = async (row: ApprovalTaskVO | null) => {
  if (!row) return;
  selectedId.value = row.taskId;
  detail.value = await getApprovalTaskDetail(row.detailType);
};

const onAction = async (act: { key: string; label: string }) => {
  submitting.value = true;
  try {
    ElMessage.success(`已执行「${act.label}」${comment.value ? `，意见：${comment.value}` : ''}（模拟写入审计日志）`);
    comment.value = '';
  } finally {
    submitting.value = false;
  }
};

const loadData = async () => {
  loading.value = true;
  try {
    const [k, t, r, d] = await Promise.all([
      getApprovalKpis(scope.value),
      listApprovalTasks(scope.value),
      getApprovalReturned(scope.value),
      getApprovalDone(scope.value)
    ]);
    kpis.value = k;
    allTasks.value = t;
    returnedTasks.value = r;
    doneTasks.value = d;
  } finally {
    loading.value = false;
  }
};

/** 切换角色时重新拉取对应 Scope 数据 */
watch(scope, () => {
  onTabChange();
  loadData();
});

onMounted(loadData);
</script>
