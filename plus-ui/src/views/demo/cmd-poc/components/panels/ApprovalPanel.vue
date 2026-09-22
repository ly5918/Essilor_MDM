<template>
  <section class="page">
    <!-- KPI 概览：待我处理 / 临近SLA / 已超时 / 退回待补充 / 本周已处理 -->
    <div class="ap-kpis">
      <div v-for="kpi in kpis" :key="kpi.label" class="ap-kpi">
        <b>{{ kpi.value }}</b>
        <span>{{ kpi.label }}</span>
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
        <el-input v-model="filter.keyword" placeholder="One ID / 申请编号 / 客户名称" clearable style="width: 240px" @keyup.enter="applyFilter" />
        <el-button type="primary" plain icon="Search" @click="applyFilter">查询</el-button>
      </div>

      <!-- 左列表 + 右侧只读速览：点行看概要，「进入审批」打开宽弹窗完成审批操作 -->
      <div class="ap-layout">
      <div class="ap-list">
        <div class="ap-list-title">{{ tabLabel }}</div>
          <el-table
            v-loading="loading"
            ref="tableRef"
            border
            :data="visibleTasks"
            :height="tableHeight"
            class="data-table"
            highlight-current-row
            :current-row-key="selectedId"
            row-key="taskId"
            :row-style="{ cursor: 'pointer' }"
            @current-change="onRowSelect"
          >
            <el-table-column label="任务编号" prop="taskId" width="150" />
            <!-- One ID 列已移除：批次/合并任务该列为空易误读，One ID 统一在右侧详情头部展示 -->
            <el-table-column label="客户/主题" prop="customerName" min-width="240" show-overflow-tooltip />
            <el-table-column label="任务类型" prop="taskType" width="130" />
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

          <!-- 分页 -->
          <div class="pagination-container" v-if="total > 0">
            <el-pagination
              v-model:current-page="pageNum"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="total"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadData"
              @current-change="loadData"
            />
          </div>
        </div>

        <!-- 右侧速览（只读）：概要信息 + 进入审批入口；完整治理证据与审批动作在弹窗中操作 -->
        <div class="ap-detail ap-quick">
          <template v-if="detail">
            <div class="ap-detail-head">
              <h3 class="ap-detail-name">{{ detail.name }}</h3>
              <div class="ap-detail-meta">
                <div class="ap-detail-tags">
                  <el-tag v-if="detail.oneId" size="small" type="success" effect="dark">One ID：{{ detail.oneId }}</el-tag>
                  <!-- 批次级审批：批量导入确认以「批次号」为全链路业务主键，One ID 在批准后按行生成 -->
                  <el-tag v-if="detail.bizId" size="small" type="info" effect="plain">批次号：{{ detail.bizId }}</el-tag>
                  <el-tag size="small" type="primary">{{ sceneText }}</el-tag>
                  <el-tag size="small" :type="isGc ? 'warning' : 'info'">{{ isGc ? 'GC Scope' : 'BU Scope' }}</el-tag>
                </div>
                <div class="ap-detail-ops">
                  <!-- 流程跟踪：泳道图步骤条 + Warm-Flow 实例进度（场景泳道图可视化） -->
                  <el-button link type="primary" icon="Share" @click="onOpenFlowTrace">流程跟踪</el-button>
                  <!-- MERGE 场景：疑似/精准重复任务可直接发起客户合并（总设计 MERGE 触发路径） -->
                  <el-button v-if="canMerge" link type="warning" icon="Connection" @click="onLaunchMerge">发起合并</el-button>
                </div>
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
                <div>
                  <b>{{ isBatch ? 'Data Quality（批次均分）' : 'Data Quality' }}</b><br />{{ detail.dq }}
                </div>
                <div>
                  <b>{{ isBatch ? 'Duplicate Check（分流结论）' : 'Duplicate Check' }}</b><br />{{ detail.duplicate }}
                </div>
              </div>

              <h4>{{ isGc ? 'GC治理决策' : 'BU初审判断' }}</h4>
              <div class="ap-decisions">
                <el-tag v-for="d in detail.decisions" :key="d" class="ap-tag" effect="plain">{{ d }}</el-tag>
              </div>
            </div>
            <div class="ap-quick-foot">
              <span class="ap-quick-hint">完整治理证据（字段级对比）与审批动作请在审批弹窗中查看操作</span>
              <el-button type="primary" icon="EditPen" :disabled="!detail" @click="openApproval">进入审批</el-button>
            </div>
          </template>
          <el-empty v-else description="选择左侧任务查看概要" />
        </div>
      </div>

      <!-- 审批详情弹窗：宽容器（约 1040px）让证据字段对比 / DQ 明细 / 动作区完整铺开 -->
      <el-dialog v-model="detailOpen" :title="detailTitle" width="1040px" top="6vh" class="ap-dlg" destroy-on-close>
          <template v-if="detail">
            <div class="ap-detail-head">
              <h3 class="ap-detail-name">{{ detail.name }}</h3>
              <div class="ap-detail-meta">
                <div class="ap-detail-tags">
                  <el-tag v-if="detail.oneId" size="small" type="success" effect="dark">One ID：{{ detail.oneId }}</el-tag>
                  <!-- 批次级审批：批量导入确认以「批次号」为全链路业务主键，One ID 在批准后按行生成 -->
                  <el-tag v-if="detail.bizId" size="small" type="info" effect="plain">批次号：{{ detail.bizId }}</el-tag>
                  <el-tag v-if="detail.bizId && !detail.oneId" size="small" type="warning" effect="plain">
                    批次级审批 · 批准后逐条生成 One ID
                  </el-tag>
                  <el-tag size="small" type="primary">{{ sceneText }}</el-tag>
                  <el-tag size="small" :type="isGc ? 'warning' : 'info'">{{ isGc ? 'GC Scope' : 'BU Scope' }}</el-tag>
                </div>
                <div class="ap-detail-ops">
                  <!-- 流程跟踪：泳道图步骤条 + Warm-Flow 实例进度（场景泳道图可视化） -->
                  <el-button link type="primary" icon="Share" @click="onOpenFlowTrace">流程跟踪</el-button>
                  <!-- MERGE 场景：疑似/精准重复任务可直接发起客户合并（总设计 MERGE 触发路径） -->
                  <el-button v-if="canMerge" link type="warning" icon="Connection" @click="onLaunchMerge">发起合并</el-button>
                </div>
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
                <div>
                  <b>{{ isBatch ? 'Data Quality（批次均分）' : 'Data Quality' }}</b><br />{{ detail.dq }}
                </div>
                <div>
                  <b>{{ isBatch ? 'Duplicate Check（分流结论）' : 'Duplicate Check' }}</b><br />{{ detail.duplicate }}
                </div>
              </div>

              <h4>{{ isGc ? 'GC治理决策' : 'BU初审判断' }}</h4>
              <div class="ap-decisions">
                <el-tag v-for="d in detail.decisions" :key="d" class="ap-tag" effect="plain">{{ d }}</el-tag>
              </div>

              <h4>治理证据</h4>
              <!-- 疑似/精准重复：字段级命中高亮对比（借鉴 DCR Matching Review） -->
              <div v-if="candidateCompare.length" class="ap-cand">
                <div class="ap-cand-head">
                  <el-tag size="small" type="success" effect="dark">命中候选：{{ candOneId }}</el-tag>
                  <el-tag v-if="candCrossBu" size="small" type="danger" effect="plain">跨 BU</el-tag>
                  <span class="ap-cand-hint">确认关联后，本申请将转为对该 One ID 主档的更新，不再新建客户</span>
                </div>
                <div class="ap-cmp-head">
                  <span>对比字段</span>
                  <span>新申请</span>
                  <span>命中主档</span>
                  <span class="ap-cmp-flag">匹配</span>
                </div>
                <div v-for="row in candidateCompare" :key="row.label" class="ap-cmp-row">
                  <span class="ap-cmp-label">{{ row.label }}</span>
                  <span class="ap-cmp-val" :class="`is-${row.status.toLowerCase()}`">{{ row.incoming || '（空）' }}</span>
                  <span class="ap-cmp-val" :class="`is-${row.status.toLowerCase()}`">{{ row.existing || '（空）' }}</span>
                  <span class="ap-cmp-flag">
                    <el-tag size="small" :type="cmpFlagTag(row.status)" effect="plain">{{ cmpFlagText(row.status) }}</el-tag>
                  </span>
                </div>
              </div>
              <!-- 后端以 JSON 快照下发，逐条渲染为「标签 / 值」；非 JSON 时原样展示 -->
              <div class="ap-evidence">
                <template v-if="evidenceRest.length">
                  <div v-for="row in evidenceRest" :key="row.label" class="ap-ev-row">
                    <span class="ap-ev-key">{{ row.label }}</span>
                    <span class="ap-ev-val">{{ row.value }}</span>
                  </div>
                </template>
                <span v-else>{{ detail.evidence }}</span>
              </div>

              <h4>审批意见</h4>
              <el-input v-model="comment" type="textarea" :rows="2" placeholder="请输入审批意见或升级原因" />

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
          <div v-else v-loading="true" class="ap-dlg-loading" element-loading-text="加载审批详情…" />
      </el-dialog>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import {
  BIZ_TYPE_TEXT,
  getApprovalKpis,
  getApprovalTaskDetail,
  listApprovalTasksByCategory,
  submitApprovalAction
} from '@/api/demo/cmdPoc';
import type { ApprovalKpiVO, ApprovalTaskDetailVO, ApprovalTaskVO, DuplicateFieldStatus, RoleKey } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { useListTableHeight } from '../../composables/useListTableHeight';

defineOptions({ name: 'CmdPocApprovalPanel' });

/** 表格高度自适应 + 分页固定在内容区底部（与其他列表面板一致，reserve=分页条+卡片内边距） */
const { tableRef, tableHeight, recalc } = useListTableHeight(70);

const { roleKey, openDialog, refreshBadge } = useCmdPoc();
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

const TASK_TYPE_OPTIONS = ['客户新建', '客户变更', '逻辑停用', '层级关系', '疑似重复', '跨BU合并', 'DQ异常', '批量治理', '批量导入确认'];
const BU_OPTIONS = ['High End', 'Mainstream', 'Cross-BU'];

/**
 * 审批任务类型（Tab=审批任务）
 *
 * 后端 bizType 实际值：客户新建 / 客户新建 - OCR（单条创建，含 OCR 来源）、
 * 层级关系、跨BU合并、批量导入确认（IMPORT）等，这里必须逐字对齐，否则 live 待办被过滤成空。
 *
 * 「批量导入确认」是**批次级审批**：一条导入任务对应一条审批待办，
 * 批准后为批次内 New 行逐条生成 One ID（总设计场景二节点「批量处理结果」）。
 */
/**
 * 页签 → 后端队列分类。
 *
 * 「全部待办」= ALL（后端按 status 聚合：待处理 + 退回待补充），
 * 而不是前端把三类各取一页再截断——后者会让治理复核 / 退回类任务永远排不进首页（测试报告 BUG-6）。
 */
const TAB_CATEGORY: Record<string, string> = {
  all: 'ALL',
  approval: 'APPROVAL',
  governance: 'GOVERNANCE',
  returned: 'RETURNED',
  done: 'DONE'
};

const kpis = ref<ApprovalKpiVO[]>([]);
/** 当前页签的任务（服务端分页，与该页签的 total 严格一致） */
const allTasks = ref<ApprovalTaskVO[]>([]);
const returnedTasks = ref<ApprovalTaskVO[]>([]);
const doneTasks = ref<ApprovalTaskVO[]>([]);
const loading = ref(false);
const activeTab = ref<(typeof TABS)[number]['key']>('all');
const detailOpen = ref(false);
const selectedId = ref('');
const selectedRow = ref<ApprovalTaskVO | null>(null);
const detail = ref<ApprovalTaskDetailVO | null>(null);
const comment = ref('');
const submitting = ref(false);
const pageNum = ref(1);
const pageSize = ref(10);
const total = ref(0);

const filter = reactive({ taskType: '', bu: '', sla: '', risk: '', keyword: '' });

const tabLabel = computed(() => TABS.find(t => t.key === activeTab.value)?.label ?? '全部待办');

/**
 * 是否为批量导入确认（批次级审批）
 *
 * 总设计场景二「批量导入」：BU Scope 治理处理 Same-BU 候选（批量关联 / 排除 / 退回修复），
 * 批量处理结果节点才「Exact 关联已有 One ID；New 审批后生成 One ID」——即**一条导入任务
 * 对应一条审批待办**，逐条粒度体现在对批次内各行的治理决策，而不是把一批拆成 N 条审批。
 * 后端对该场景存业务码 IMPORT，此处按业务码识别，不改动后端语义。
 */
const isBatch = computed(() => detail.value?.scene === 'IMPORT');

/** 疑似/精准重复任务可发起客户合并（SUSPECTED → 跨BU治理；EXACT → 关联确认） */
const canMerge = computed(() => {
  const d = detail.value;
  return !!d?.oneId && /SUSPECTED|EXACT|疑似|重复/i.test(d.duplicate ?? '');
});
/** 从审批详情直接打开合并申请弹窗（当前任务客户为合并源） */
const onLaunchMerge = () => {
  if (detail.value?.oneId) openDialog('merge', { oneId: detail.value.oneId, name: detail.value.name });
};

/** 场景显示名（IMPORT → 批量导入确认；客户类后端已存中文，原样显示） */
const sceneText = computed(() => BIZ_TYPE_TEXT[detail.value?.scene ?? ''] ?? detail.value?.scene ?? '');

/** 治理证据：后端以 JSON 快照下发，逐条渲染为「标签 / 值」；非 JSON 时原样展示 */
const evidenceRows = computed<Array<{ label: string; value: string }>>(() => {
  const text = (detail.value?.evidence ?? '').trim();
  if (!text.startsWith('{')) return [];
  try {
    const obj = JSON.parse(text) as Record<string, unknown>;
    return Object.entries(obj).map(([label, value]) => ({
      label,
      value: value === null || value === undefined ? '—' : String(value)
    }));
  } catch {
    return [];
  }
});

/**
 * 候选字段级对比（借鉴 DCR Matching Review：绿=一致 / 红=不一致 / 灰=空缺）。
 * 后端 evidence JSON 在疑似/精准重复时成对下发「申请X / 候选X」键；
 * 旧数据缺申请侧键时按「空缺」降级展示，不报错。
 */
interface CandidateCompareRow {
  label: string;
  incoming: string;
  existing: string;
  status: DuplicateFieldStatus;
}
const CAND_ONE_ID_KEY = '候选One ID';
const CAND_SIDE_KEYS = [
  '信用代码',
  '注册地址',
  '候选名称',
  '候选信用代码',
  '候选经营地址',
  '候选BU',
  '候选来源系统',
  '跨BU'
];
const compareRow = (label: string, incoming: string, existing: string): CandidateCompareRow => {
  const normalize = (value: string) => {
    const text = (value ?? '').trim();
    return !text || text === '未提供' || text === '—' ? '' : text;
  };
  const a = normalize(incoming);
  const b = normalize(existing);
  const status: DuplicateFieldStatus = !a || !b ? 'EMPTY' : a === b ? 'MATCH' : 'DIFF';
  return { label, incoming: a, existing: b, status };
};
const candidateCompare = computed<CandidateCompareRow[]>(() => {
  const map = new Map(evidenceRows.value.map(row => [row.label, row.value]));
  if (!map.has(CAND_ONE_ID_KEY)) return [];
  const get = (key: string) => map.get(key) ?? '';
  return [
    compareRow('客户名称', get('申请名称'), get('候选名称')),
    compareRow('统一社会信用代码', get('信用代码'), get('候选信用代码')),
    compareRow('经营地址', get('注册地址'), get('候选经营地址')),
    compareRow('所属 BU', get('申请BU'), get('候选BU')),
    compareRow('来源系统', get('申请来源系统'), get('候选来源系统'))
  ];
});
/** 已进入候选对比的字段不再重复平铺 */
const evidenceRest = computed(() =>
  candidateCompare.value.length ? evidenceRows.value.filter(row => !CAND_SIDE_KEYS.includes(row.label)) : evidenceRows.value
);
const candOneId = computed(() => {
  const map = new Map(evidenceRows.value.map(row => [row.label, row.value]));
  return map.get(CAND_ONE_ID_KEY) ?? '';
});
const candCrossBu = computed(() => {
  const map = new Map(evidenceRows.value.map(row => [row.label, row.value]));
  return map.get('跨BU') === 'Y';
});
const CMP_FLAG_TEXT: Record<DuplicateFieldStatus, string> = { MATCH: '一致', DIFF: '不一致', EMPTY: '空缺' };
const CMP_FLAG_TAG: Record<DuplicateFieldStatus, 'success' | 'danger' | 'info'> = {
  MATCH: 'success',
  DIFF: 'danger',
  EMPTY: 'info'
};
const cmpFlagText = (status: DuplicateFieldStatus) => CMP_FLAG_TEXT[status] ?? status;
const cmpFlagTag = (status: DuplicateFieldStatus) => CMP_FLAG_TAG[status] ?? 'info';

/** 当前 Tab 的数据集（由服务端按分类 + 分页返回，与该页签的 total 一致） */
const baseTasks = computed<ApprovalTaskVO[]>(() => allTasks.value);

/** 在基础数据集上叠加统一筛选条件（任务类型 / BU / 风险 / 关键词） */
const visibleTasks = computed<ApprovalTaskVO[]>(() => {
  const kw = filter.keyword.trim().toLowerCase();
  return baseTasks.value.filter(t => {
    const matchType = !filter.taskType || t.taskType === filter.taskType;
    const matchBu = !filter.bu || t.bu === filter.bu;
    const matchRisk = !filter.risk || t.risk === filter.risk;
    const matchKw =
      !kw ||
      t.taskId.toLowerCase().includes(kw) ||
      t.customerName.toLowerCase().includes(kw) ||
      (t.oneId ?? '').toLowerCase().includes(kw);
    return matchType && matchBu && matchRisk && matchKw;
  });
});

const onTabChange = () => {
  pageNum.value = 1;
  selectedId.value = '';
  selectedRow.value = null;
  detail.value = null;
  comment.value = '';
  // 分类在服务端切换，切页签必须重新取数，否则列表仍是上一个分类的结果（测试报告 BUG-6）
  void loadData();
};

const applyFilter = () => {
  /* 筛选已通过 visibleTasks 计算属性实时生效，这里仅用于「查询」按钮的点击反馈 */
};

/** 点行选中：右侧速览加载只读概要；审批意见与动作在「进入审批」弹窗中完成 */
const onRowSelect = async (row: ApprovalTaskVO | null) => {
  if (!row) return;
  selectedId.value = row.taskId;
  selectedRow.value = row;
  detail.value = await getApprovalTaskDetail(row.taskId);
};

/** 进入审批：打开宽弹窗（完整治理证据 + 审批意见 + 动作按钮） */
const openApproval = () => {
  if (detail.value) detailOpen.value = true;
};

/** 弹窗标题：任务编号 + 客户/主题 */
const detailTitle = computed(() => (detail.value ? `${detail.value.id} · ${detail.value.name}` : '审批详情'));

/** 打开流程跟踪弹窗（泳道图步骤条 + Warm-Flow 实例进度） */
const onOpenFlowTrace = () => {
  const row = selectedRow.value;
  if (!row) return;
  openDialog('flowTrace', { taskNo: row.taskId, detailType: row.detailType });
};

const onAction = async (act: { key: string; label: string; type?: string }) => {
  if (!detail.value) return;
  submitting.value = true;
  try {
    await submitApprovalAction({
      taskId: detail.value.id,
      actionType: act.key,
      opinion: comment.value
    });
    ElMessage.success(`已执行「${act.label}」${comment.value ? `，意见：${comment.value}` : ''}`);
    comment.value = '';
    detail.value = null;
    detailOpen.value = false;
    selectedId.value = '';
    selectedRow.value = null;
    await loadData();
    refreshBadge();
  } finally {
    submitting.value = false;
  }
};

/**
 * 按当前页签分类从服务端取数（分页在服务端完成，避免前端合并三类导致的翻页丢数据）。
 * KPI 仍按 Scope 全量统计，与页签列表口径独立。
 */
const loadData = async () => {
  loading.value = true;
  try {
    const [k, t] = await Promise.all([
      getApprovalKpis(scope.value),
      listApprovalTasksByCategory(
        scope.value,
        TAB_CATEGORY[activeTab.value] ?? 'ALL',
        pageNum.value,
        pageSize.value
      )
    ]);
    kpis.value = k;
    allTasks.value = t.rows;
    total.value = t.total;
  } finally {
    loading.value = false;
    // KPI 行 / Tab 高度稳定后表格顶部才准；数据到位后重算一次固定分页位置
    requestAnimationFrame(recalc);
  }
};

/** 切换角色时重新拉取对应 Scope 数据 */
watch(scope, () => {
  onTabChange();
  loadData();
});

onMounted(loadData);
</script>
