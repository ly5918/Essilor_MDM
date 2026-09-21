<template>
  <div v-loading="loading" class="ft-body">
    <template v-if="trace">
      <!-- 顶部：场景 + 状态 -->
      <div class="ft-head">
        <div class="ft-scene">
          <span class="ft-scene-tag">场景</span>
          <b>{{ trace.sceneName }}（{{ trace.sceneCode }}）</b>
          <span class="ft-scene-desc">{{ trace.bizTitle }} · {{ trace.bizType }}</span>
        </div>
        <div class="ft-head-meta">
          <el-tag size="small" :type="statusTagType">{{ statusLabel }}</el-tag>
          <el-tag v-if="trace.riskLevel" size="small" type="danger" effect="plain">风险 {{ trace.riskLevel }}</el-tag>
          <el-tag size="small" type="info" effect="plain">SLA {{ slaText }}</el-tag>
        </div>
      </div>

      <!--
        泳道图入口（原 Warm-Flow 引擎流程图已隐藏）：
        引擎实例图只有 4 个泛化节点（开始/申请/初审/决策/结束），信息完全被下方
        11 步泳道步骤条覆盖，且节点名是引擎内部命名、与业务阶段对不上，反而造成
        「两张图两个口径」的困惑；引擎绑定信息（流程编码/实例 ID）保留在页脚。
      -->
      <div class="ft-graph-bar">
        <el-button
          class="ft-swim-btn"
          link
          type="primary"
          icon="Share"
          title="在 V6.1 泳道图（7 阶段 × 6 泳道）上按这一单的实际进度打开"
          @click="onViewSwimlane"
        >泳道图</el-button>
        <span class="ft-graph-bar-note">
          Warm-Flow {{ trace.engineBound ? '引擎已接入 · 实例 ' + (trace.flowInstanceId ?? '—') : '未启动实例' }}
        </span>
      </div>

      <!-- 横向步骤条（泳道图 7 阶段） -->
      <div class="ft-steps-wrap">
        <div class="ft-progress">
          <div class="ft-progress-bar">
            <div class="ft-progress-done" :style="{ width: `${trace.progressPercent}%` }" />
          </div>
          <span class="ft-progress-text">{{ trace.completedSteps }}/{{ trace.totalSteps }} · {{ trace.progressPercent }}%</span>
        </div>

        <p class="ft-steps-hint">点击任意步骤，下方会跟着切换到该节点实际发生的内容（字段 / 明细表 / 口径说明）</p>
        <div class="ft-steps">
          <template v-for="(step, i) in trace.steps" :key="step.nodeCode">
            <div
              class="ft-step"
              :class="[`is-${step.status.toLowerCase()}`, { 'is-active': step.nodeCode === activeNode }]"
              :title="`查看「${step.nodeName}」明细`"
              @click="activeNode = step.nodeCode"
            >
              <div class="ft-node">
                <!-- 恒占位、只切可见性：否则只有选中那一步多一行，步骤条高矮不齐 -->
                <span class="ft-active-chip" :class="{ 'is-on': step.nodeCode === activeNode }">明细</span>
                <span class="ft-icon">{{ iconFor(step) }}</span>
                <span class="ft-node-name">{{ step.nodeName }}</span>
                <span class="ft-lane">{{ step.lane }}</span>
              </div>
              <div class="ft-band">{{ bandText(step) }}</div>
            </div>
            <div v-if="i < trace.steps.length - 1" class="ft-arrow" :class="{ 'is-done': step.status === 'COMPLETED' }">→</div>
          </template>
        </div>

        <!-- 泳道图旁路节点（虚线：不打断主流程） -->
        <div v-if="trace.bypass" class="ft-bypass">
          <span class="ft-bypass-dash">┄</span>
          <b>{{ trace.bypass.nodeName }}</b>
          <span class="ft-bypass-lane">{{ trace.bypass.lane }}</span>
          <span class="ft-bypass-note">{{ trace.bypass.note }}</span>
        </div>
      </div>

      <!--
        分步骤明细：跟着上面选中的步骤动态切换。
        节点该出现哪些区块（字段 / 表格 / 提示）由后端按节点语义决定，前端只渲染。
        后端未下发明细（老数据 / 非 CMD 场景）时回退到「当前任务」摘要，保证这块不为空。
      -->
      <div class="ft-detail-panel">
        <div class="ft-dp-head">
          <span v-if="activeDetail?.phaseName || activeStep?.phaseName" class="ft-dp-phase">
            {{ activeDetail?.phaseName ?? activeStep?.phaseName }}
          </span>
          <h4>{{ activeStep?.nodeName ?? activeDetail?.nodeName ?? trace.currentNodeName ?? '流程详情' }}</h4>
          <el-tag size="small" :type="stepTagType(activeDetail?.status ?? activeStep?.status)">
            {{ stepStatusLabel(activeDetail?.status ?? activeStep?.status) }}
          </el-tag>
          <span class="ft-dp-lane">{{ activeDetail?.lane ?? activeStep?.lane }}</span>
          <span v-if="activeStep?.operator" class="ft-dp-meta">
            操作人 {{ activeStep.operator }}<template v-if="activeStep.actionTime"> · {{ fmtTime(activeStep.actionTime) }}</template>
          </span>
        </div>

        <p class="ft-dp-summary">
          {{ activeDetail?.summary
            ?? `该节点未下发节点级明细；当前任务由 ${trace.assigneeName ?? '—'} 处理（角色 ${trace.assigneeRole ?? '—'}）` }}
        </p>

        <div v-if="detailFields.length" class="ft-dp-fields">
          <div v-for="fd in detailFields" :key="fd.label" class="ft-dp-field">
            <span class="ft-dp-label">{{ fd.label }}</span>
            <span class="ft-dp-value">
              <el-tag v-if="fd.tone" size="small" :type="toneOf(fd.tone)" effect="light">{{ fd.value }}</el-tag>
              <template v-else>{{ fd.value }}</template>
            </span>
          </div>
        </div>

        <div v-for="(tb, ti) in detailTables" :key="`${ti}-${tb.title ?? ''}`" class="ft-dp-table">
          <h5 v-if="tb.title">{{ tb.title }}</h5>
          <el-table :data="toRows(tb)" border size="small" class="data-table">
            <el-table-column
              v-for="(col, ci) in tb.columns"
              :key="ci"
              :label="col"
              :prop="String(ci)"
              min-width="112"
              show-overflow-tooltip
            />
          </el-table>
        </div>

        <ul v-if="detailNotes.length" class="ft-dp-notes">
          <li v-for="(nt, ni) in detailNotes" :key="ni">{{ nt }}</li>
        </ul>

        <!-- 兜底：无节点级明细时沿用原有「当前任务」信息 -->
        <div v-if="!activeDetail" class="ft-task">
          <p>Task started on {{ trace.submitTime ?? '—' }}</p>
          <p>Profiles: [{{ trace.flowName }}] · [{{ (trace.assigneeRole ?? '').toLowerCase() }}]</p>
          <p class="ft-offered">
            <span class="ft-offered-icon">👥</span>
            Offered to: {{ trace.assigneeName ?? '—' }}
          </p>
        </div>
      </div>

      <!-- Data context state -->
      <div class="ft-context">
        <h4>Data context state</h4>
        <table class="ft-table">
          <thead>
            <tr>
              <th>Variable name</th>
              <th>Start value</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="v in trace.contextVars" :key="v.name">
              <td class="ft-var-name">{{ v.name }}</td>
              <td :class="{ 'is-undefined': !v.value }">{{ v.value ?? '[not defined]' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 审批轨迹 -->
      <div v-if="trace.actions.length" class="ft-actions">
        <h4>审批轨迹</h4>
        <div v-for="(a, i) in trace.actions" :key="i" class="ft-action-row">
          <span class="ft-action-time">{{ a.actionTime }}</span>
          <span class="ft-action-name">{{ a.actionName || a.actionType }}</span>
          <span class="ft-action-operator">{{ a.operatorName }} · {{ a.operatorRole }}</span>
          <span v-if="a.opinion" class="ft-action-opinion">{{ a.opinion }}</span>
        </div>
      </div>

      <!-- Warm-Flow 引擎关联 -->
      <div class="ft-engine">
        Warm-Flow 关联：流程编码 {{ trace.flowCode ?? '—' }} · 实例 {{ trace.flowInstanceId ?? '—' }} ·
        场景 SLA {{ trace.slaHours ?? '—' }}h（业务表只存实例/任务 ID，引擎进度以镜像字段透出）
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { getFlowTrace } from '@/api/demo/cmdPoc';
import { useCmdPoc } from '../composables/useCmdPoc';
import type {
  FlowStepDetailVO,
  FlowStepTableVO,
  FlowTraceStepVO,
  FlowTraceVO
} from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocFlowTraceDetail' });

const props = withDefaults(
  defineProps<{ taskNo: string; detailType?: string }>(),
  { detailType: 'create' }
);

const { openDialog } = useCmdPoc();

const loading = ref(false);
const trace = ref<FlowTraceVO | null>(null);

/**
 * 泳道图：在 V6.1 泳道图（7 阶段 × 6 泳道）上按**这一单**的实际进度打开（实例视图）。
 * 入口放在流程跟踪弹窗内 —— 之前放在列表「操作」列，需求方要求收起，改为在详情里按需查看。
 */
const onViewSwimlane = () => {
  const t = trace.value;
  if (!t?.taskNo) return;
  openDialog('flowGraph', {
    sceneCode: t.sceneCode,
    sceneName: t.sceneName ?? t.sceneCode,
    flowCode: t.flowCode,
    taskNo: t.taskNo
  });
};

/**
 * 当前选中的步骤（点步骤条切换）——「分步骤明细」的唯一状态源。
 * 默认选「进行中」那一步，其次是最后一个已完成，最后兜底第一条：
 * 打开时下方就已经是最相关的那一步，而不是空白或最早的「创建申请」。
 */
const activeNode = ref('');

const activeStep = computed<FlowTraceStepVO | undefined>(() =>
  (trace.value?.steps ?? []).find(s => s.nodeCode === activeNode.value)
);
const activeDetail = computed<FlowStepDetailVO | undefined>(() =>
  (trace.value?.stepDetails ?? []).find(d => d.nodeCode === activeNode.value)
);

/** 表格区拆成独立 computed，模板里就不需要可选链收窄（vue-tsc 更友好） */
const detailFields = computed(() => activeDetail.value?.fields ?? []);
const detailTables = computed(() => activeDetail.value?.tables ?? []);
const detailNotes = computed(() => activeDetail.value?.notes ?? []);

/** 自动选中默认步骤 */
const pickDefaultStep = () => {
  const steps = trace.value?.steps ?? [];
  const current = steps.find(s => s.status === 'CURRENT');
  // 最后一个已完成（不用 reverse()/toReversed()：前者会触发 oxlint，后者要求 ES2023 lib）
  let lastDone: FlowTraceStepVO | undefined;
  for (const s of steps) {
    if (s.status === 'COMPLETED') lastDone = s;
  }
  activeNode.value = (current ?? lastDone ?? steps[0])?.nodeCode ?? '';
};

/** string[][] → el-table 需要的对象行（列用下标作 prop） */
const toRows = (tb: FlowStepTableVO) =>
  (tb.rows ?? []).map(row => {
    const obj: Record<string, string> = {};
    (row ?? []).forEach((v, i) => {
      obj[String(i)] = v;
    });
    return obj;
  });

type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info';
const TONE_SET: readonly string[] = ['primary', 'success', 'warning', 'danger', 'info'];
/** 后端下发的语义色，非法值一律回退 info，避免 el-tag 报类型错误 */
const toneOf = (tone?: string): TagType => (TONE_SET.includes(tone ?? '') ? (tone as TagType) : 'info');

const STEP_STATUS_TEXT: Record<string, string> = {
  COMPLETED: '已完成',
  CURRENT: '进行中',
  PENDING: '待执行',
  TERMINATED: '已终止'
};
const STEP_STATUS_TAG: Record<string, TagType> = {
  COMPLETED: 'success',
  CURRENT: 'warning',
  PENDING: 'info',
  TERMINATED: 'danger'
};
const stepStatusLabel = (status?: string) => STEP_STATUS_TEXT[status ?? ''] ?? (status || '—');
const stepTagType = (status?: string) => STEP_STATUS_TAG[status ?? ''] ?? 'info';

const fmtTime = (v?: string) => (v ? String(v).replace('T', ' ').slice(0, 16) : '—');

const STATUS_LABELS: Record<string, string> = {
  PENDING: '处理中',
  APPROVED: '已批准',
  COMPLETED: '已完成',
  REJECTED: '已拒绝',
  RETURNED: '已退回',
  ESCALATED: '已升级GC',
  CANCELLED: '已取消'
};

const statusLabel = computed(() => STATUS_LABELS[trace.value?.status ?? ''] ?? (trace.value?.status || '—'));
const statusTagType = computed(() =>
  ['APPROVED', 'COMPLETED'].includes(trace.value?.status ?? '') ? 'success' : 'warning'
);

const slaText = computed(() => {
  if (!trace.value) return '—';
  const state = trace.value.slaState;
  if (state === 'OVERDUE') return '已超时';
  if (state === 'DUE_SOON') return '临近';
  return trace.value.slaDue?.slice(0, 16) ?? '正常';
});

const iconFor = (step: FlowTraceStepVO) => {
  if (step.nodeType === 'GATEWAY') return '◇';
  if (step.nodeType === 'MANUAL') return '☶';
  return '⚙';
};

const bandText = (step: FlowTraceStepVO) => {
  if (step.status === 'COMPLETED') return 'complete';
  if (step.status === 'CURRENT') return 'to do';
  if (step.status === 'TERMINATED') return 'stopped';
  return 'pending';
};

const load = async () => {
  if (!props.taskNo) {
    trace.value = null;
    activeNode.value = '';
    return;
  }
  loading.value = true;
  try {
    trace.value = await getFlowTrace(props.taskNo, props.detailType);
    pickDefaultStep();
  } finally {
    loading.value = false;
  }
};

onMounted(load);
watch(() => [props.taskNo, props.detailType], load, { flush: 'post' });
</script>

<style scoped lang="scss">
.ft-body {
  min-height: 240px;
}

.ft-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.ft-scene {
  display: flex;
  align-items: center;
  gap: 8px;

  .ft-scene-tag {
    padding: 1px 8px;
    border-radius: 4px;
    font-size: 12px;
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }

  .ft-scene-desc {
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}

.ft-head-meta {
  display: flex;
  gap: 6px;
}

/* 泳道图入口行（原 BPMN 引擎图位置） */
.ft-graph-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  padding: 5px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}

.ft-graph-bar-note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 步骤条 */
.ft-steps-wrap {
  margin-top: 8px;
}

.ft-progress {
  display: flex;
  align-items: center;
  gap: 10px;

  .ft-progress-bar {
    flex: 1;
    height: 6px;
    border-radius: 3px;
    background: var(--el-fill-color);
    overflow: hidden;
  }

  .ft-progress-done {
    height: 100%;
    border-radius: 3px;
    background: var(--el-color-success);
    transition: width 0.3s;
  }

  .ft-progress-text {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    white-space: nowrap;
  }
}

/* 步骤条操作提示：告诉用户「这里可以点」 */
.ft-steps-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ft-steps {
  display: flex;
  align-items: stretch;
  gap: 4px;
  margin-top: 8px;
  /*
    固定一个足够高的高度（BPMN 风格步骤条不再自适应），并显式关掉上下滚动条：
    overflow-x:auto 会把 overflow-y 隐式算成 auto，再加上节点原来的 height:100%
    把状态条顶出容器，就会出现一条竖直滚动条。这里一并解决。
  */
  height: 150px;
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 4px;
}

.ft-step {
  /* 等宽铺满整行（BPMN 风格），窄屏可收缩，避免横向滚动条把底部状态条压住 */
  flex: 1 1 0;
  min-width: 78px;
  text-align: center;
  cursor: pointer;
  /* 竖直排列：节点占满状态条以上的空间，状态条贴底 */
  display: flex;
  flex-direction: column;

  &:hover .ft-node {
    border-color: var(--el-color-primary-light-3);
  }

  .ft-node {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    padding: 8px 4px 4px;
    border-radius: 6px;
    border: 1px solid var(--el-border-color-lighter);
    background: var(--el-fill-color-lighter);
    /* 原来 height:100% 在外层 stretch 下会连同状态条一起撑出容器，改为占满状态条以上空间 */
    flex: 1 1 auto;
    box-sizing: border-box;
  }

  .ft-icon {
    font-size: 16px;
    color: var(--el-text-color-secondary);
  }

  .ft-node-name {
    font-size: 12px;
    line-height: 1.3;
    color: var(--el-text-color-primary);
    word-break: break-all;
  }

  .ft-lane {
    font-size: 11px;
    color: var(--el-text-color-secondary);
    transform: scale(0.92);
    word-break: break-all;
  }

  .ft-band {
    margin-top: 4px;
    font-size: 11px;
    border-radius: 3px;
    padding: 1px 0;
    color: #fff;

    &::before {
      content: '';
    }
  }

  &.is-completed {
    .ft-icon,
    .ft-node-name {
      color: var(--el-color-success);
    }

    .ft-node {
      border-color: var(--el-color-success-light-5);
      background: var(--el-color-success-light-9);
    }

    .ft-band {
      background: var(--el-color-success);
    }
  }

  &.is-current {
    .ft-node {
      border-color: var(--el-color-primary);
      background: var(--el-color-primary-light-9);
      box-shadow: 0 0 0 2px var(--el-color-primary-light-7);
    }

    .ft-icon,
    .ft-node-name {
      color: var(--el-color-primary);
      font-weight: 600;
    }

    .ft-band {
      background: var(--el-color-warning);
    }
  }

  &.is-pending {
    .ft-band {
      background: var(--el-fill-color-darker);
      color: var(--el-text-color-secondary);
    }
  }

  &.is-terminated {
    .ft-band {
      background: var(--el-color-danger-light-5);
      color: var(--el-color-danger);
    }
  }
}

/* 选中态：与「进行中」区分——进行中看状态色，选中看蓝色描边 + 「明细」角标 */
.ft-step.is-active {
  .ft-node {
    border-color: var(--el-color-primary);
    border-width: 2px;
    box-shadow: 0 0 0 3px var(--el-color-primary-light-8);
  }

  .ft-node-name {
    font-weight: 600;
  }
}

.ft-active-chip {
  padding: 0 4px;
  margin-bottom: 1px;
  border-radius: 3px;
  font-size: 10px;
  line-height: 14px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-8);
  /* 未选中的步骤只隐藏，不塌陷——保证 11 个节点等高 */
  visibility: hidden;

  &.is-on {
    visibility: visible;
  }
}

.ft-arrow {
  /* 固定高度变高后，箭头改为对准节点盒的垂直中心（节点盒 ≈150px - 状态条 ≈19px） */
  align-self: flex-start;
  margin-top: 56px;
  color: var(--el-border-color);
  font-size: 14px;

  &.is-done {
    color: var(--el-color-success);
  }
}

/* 旁路节点 */
.ft-bypass {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 6px 10px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  font-size: 12px;
  color: var(--el-text-color-regular);

  .ft-bypass-dash {
    color: var(--el-text-color-secondary);
  }

  .ft-bypass-lane {
    color: var(--el-text-color-secondary);
  }

  .ft-bypass-note {
    color: var(--el-text-color-secondary);
  }
}

/* 分步骤明细面板（跟着选中的步骤动态切换） */
.ft-detail-panel {
  margin-top: 8px;
  padding: 6px 12px 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.ft-dp-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;

  h4 {
    margin: 0;
    font-size: 14px;
  }
}

.ft-dp-phase {
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.ft-dp-lane,
.ft-dp-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ft-dp-summary {
  margin: 5px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
}

/* 字段区：三列键值对，窄屏降为两列，避免长值把布局撑破 */
.ft-dp-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 18px;
  margin-top: 6px;
}

.ft-dp-field {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  padding: 3px 0;
  border-bottom: 1px dashed var(--el-border-color-extra-light);
  font-size: 12px;
}

.ft-dp-label {
  flex: 0 0 116px;
  color: var(--el-text-color-secondary);
}

.ft-dp-value {
  flex: 1;
  min-width: 0;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.ft-dp-table {
  margin-top: 8px;

  h5 {
    margin: 0 0 4px;
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-regular);
  }
}

.ft-dp-notes {
  margin: 8px 0 0;
  padding-left: 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);

  li {
    line-height: 1.45;
  }
}

@media (max-width: 1280px) {
  .ft-dp-fields {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* 当前任务（仅当后端未下发节点级明细时作为兜底） */
.ft-task {
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);

  h4 {
    margin: 0 0 6px;
    font-size: 14px;
  }

  p {
    margin: 2px 0;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .ft-offered {
    margin-top: 8px;
    font-size: 13px;
    color: var(--el-text-color-primary);
  }

  .ft-offered-icon {
    margin-right: 4px;
  }
}

/* 上下文变量表 */
.ft-context {
  margin-top: 14px;

  h4 {
    margin: 0 0 8px;
    font-size: 14px;
  }
}

.ft-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;

  th,
  td {
    border: 1px solid var(--el-border-color-lighter);
    padding: 5px 10px;
    text-align: left;
  }

  th {
    background: var(--el-fill-color-light);
    color: var(--el-text-color-secondary);
    font-weight: 500;
  }

  td.is-undefined {
    color: var(--el-text-color-placeholder);
    font-style: italic;
  }

  .ft-var-name {
    color: var(--el-text-color-regular);
    width: 200px;
  }
}

/* 审批轨迹 */
.ft-actions {
  margin-top: 14px;

  h4 {
    margin: 0 0 8px;
    font-size: 14px;
  }
}

.ft-action-row {
  display: flex;
  gap: 10px;
  align-items: baseline;
  font-size: 12px;
  padding: 3px 0;

  .ft-action-time {
    color: var(--el-text-color-secondary);
    white-space: nowrap;
  }

  .ft-action-name {
    font-weight: 600;
    white-space: nowrap;
  }

  .ft-action-operator {
    color: var(--el-text-color-secondary);
    white-space: nowrap;
  }

  .ft-action-opinion {
    color: var(--el-text-color-regular);
  }
}

/* 引擎关联说明 */
.ft-engine {
  margin-top: 14px;
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
