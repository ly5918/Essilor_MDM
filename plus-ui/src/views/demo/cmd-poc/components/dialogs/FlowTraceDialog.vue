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

      <!-- Warm-Flow BPMN 风格流程图（引擎真实节点/连线 + 进度高亮） -->
      <div class="ft-bpmn">
        <h4>
          流程图（Warm-Flow 实例）
          <el-tag v-if="trace.engineBound" size="small" type="success" effect="plain">引擎已接入</el-tag>
          <el-tag v-else size="small" type="info" effect="plain">未启动实例</el-tag>
          <el-button v-if="!trace.engineBound" link type="primary" :loading="starting" @click="onStartInstance">
            启动流程实例
          </el-button>
        </h4>

        <svg v-if="graph" class="ft-svg" :viewBox="viewBox" preserveAspectRatio="xMidYMid meet">
          <defs>
            <marker id="ft-arrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto">
              <path d="M0,0 L7,3 L0,6 Z" :fill="arrowColor" />
            </marker>
          </defs>

          <!-- 连线 -->
          <g>
            <template v-for="e in graph.edges" :key="`${e.from}-${e.to}`">
              <line
                v-if="edgePoints(e)"
                :x1="edgePoints(e)!.x1"
                :y1="edgePoints(e)!.y1"
                :x2="edgePoints(e)!.x2"
                :y2="edgePoints(e)!.y2"
                :stroke="e.passed ? doneColor : pendingColor"
                :stroke-dasharray="e.skipType === 'REJECT' ? '4 3' : undefined"
                stroke-width="1.5"
                marker-end="url(#ft-arrow)"
              />
              <text
                v-if="edgePoints(e)"
                :x="(edgePoints(e)!.x1 + edgePoints(e)!.x2) / 2"
                :y="(edgePoints(e)!.y1 + edgePoints(e)!.y2) / 2 - 4"
                class="ft-edge-label"
                text-anchor="middle"
              >
                {{ e.label }}
              </text>
            </template>
          </g>

          <!-- 节点 -->
          <g v-for="n in graph.nodes" :key="n.nodeCode">
            <rect
              v-if="n.shape === 'RECT'"
              :x="n.x - 48"
              :y="n.y - 22"
              width="96"
              height="44"
              rx="6"
              :fill="fillOf(n)"
              :stroke="strokeOf(n)"
              stroke-width="1.5"
            />
            <polygon
              v-else-if="n.shape === 'DIAMOND'"
              :points="diamondPoints(n)"
              :fill="fillOf(n)"
              :stroke="strokeOf(n)"
              stroke-width="1.5"
            />
            <circle v-else :cx="n.x" :cy="n.y" r="18" :fill="fillOf(n)" :stroke="strokeOf(n)" stroke-width="1.5" />
            <text :x="n.x" :y="n.y + 4" class="ft-node-label" text-anchor="middle">{{ n.nodeName }}</text>
          </g>
        </svg>
        <el-empty v-else description="该任务尚未启动 Warm-Flow 实例，点击下方按钮启动后可查看流程图" :image-size="48" />
      </div>

      <!-- 横向步骤条（泳道图 7 阶段） -->
      <div class="ft-steps-wrap">
        <div class="ft-progress">
          <div class="ft-progress-bar">
            <div class="ft-progress-done" :style="{ width: `${trace.progressPercent}%` }" />
          </div>
          <span class="ft-progress-text">{{ trace.completedSteps }}/{{ trace.totalSteps }} · {{ trace.progressPercent }}%</span>
        </div>

        <div class="ft-steps">
          <template v-for="(step, i) in trace.steps" :key="step.nodeCode">
            <div class="ft-step" :class="`is-${step.status.toLowerCase()}`">
              <div class="ft-node">
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

      <!-- 当前任务（offered to） -->
      <div class="ft-task">
        <h4>{{ currentStep?.nodeName ?? trace.currentNodeName ?? '流程详情' }}</h4>
        <p>Task started on {{ trace.submitTime ?? '—' }}</p>
        <p>Profiles: [{{ trace.flowName }}] · [{{ (trace.assigneeRole ?? '').toLowerCase() }}]</p>
        <p class="ft-offered">
          <span class="ft-offered-icon">👥</span>
          Offered to: {{ trace.assigneeName ?? '—' }}
        </p>
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
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getFlowTrace, startFlowInstance } from '@/api/demo/cmdPoc';
import type { FlowGraphEdgeVO, FlowGraphNodeVO, FlowGraphVO, FlowStepStatus, FlowTraceStepVO, FlowTraceVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocFlowTraceDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const loading = ref(false);
const starting = ref(false);
const trace = ref<FlowTraceVO | null>(null);

/** 引擎图形（未接入引擎时为 undefined，回退业务侧步骤条） */
const graph = computed<FlowGraphVO | undefined>(() =>
  trace.value?.graph && trace.value.graph.nodes.length ? trace.value.graph : undefined
);

/** SVG 画布范围：按节点坐标自适应留白 */
const viewBox = computed(() => {
  const nodes = graph.value?.nodes ?? [];
  if (!nodes.length) return '0 0 640 180';
  const maxX = Math.max(...nodes.map(n => n.x)) + 90;
  const maxY = Math.max(...nodes.map(n => n.y)) + 70;
  return `0 0 ${maxX} ${Math.max(maxY, 180)}`;
});

const doneColor = 'var(--el-color-success)';
const currentColor = 'var(--el-color-primary)';
const pendingColor = 'var(--el-border-color)';
const arrowColor = 'var(--el-text-color-secondary)';

const fillOf = (n: FlowGraphNodeVO) => {
  if (n.status === 'COMPLETED') return 'var(--el-color-success-light-9)';
  if (n.status === 'CURRENT') return 'var(--el-color-primary-light-9)';
  return 'var(--el-fill-color-lighter)';
};

const strokeOf = (n: FlowGraphNodeVO) => {
  if (n.status === 'COMPLETED') return doneColor;
  if (n.status === 'CURRENT') return currentColor;
  return pendingColor;
};

const diamondPoints = (n: FlowGraphNodeVO) => `${n.x},${n.y - 26} ${n.x + 52},${n.y} ${n.x},${n.y + 26} ${n.x - 52},${n.y}`;

/** 连线端点：矩形/菱形按边界收缩，避免箭头压在图形上 */
const edgePoints = (e: FlowGraphEdgeVO) => {
  const nodes = graph.value?.nodes ?? [];
  const from = nodes.find(n => n.nodeCode === e.from);
  const to = nodes.find(n => n.nodeCode === e.to);
  if (!from || !to) return null;
  return { x1: from.x + 50, y1: from.y, x2: to.x - 52, y2: to.y };
};

/** 启动 Warm-Flow 实例（演示用：把当前业务单据挂到引擎） */
const onStartInstance = async () => {
  const taskNo = String(props.payload?.taskNo ?? '');
  if (!taskNo) return;
  starting.value = true;
  try {
    const instanceId = await startFlowInstance(taskNo);
    ElMessage.success(`已启动流程实例：${instanceId}`);
    trace.value = await getFlowTrace(taskNo, String(props.payload?.detailType ?? 'create'));
  } finally {
    starting.value = false;
  }
};

/** 当前进行中步骤（无则取第一个非完成步骤） */
const currentStep = computed<FlowTraceStepVO | undefined>(() => {
  const steps = trace.value?.steps ?? [];
  return steps.find(s => s.status === 'CURRENT') ?? steps.find(s => s.status === 'PENDING');
});

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

onMounted(async () => {
  loading.value = true;
  try {
    trace.value = await getFlowTrace(
      String(props.payload?.taskNo ?? ''),
      String(props.payload?.detailType ?? 'create')
    );
  } finally {
    loading.value = false;
  }
});
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

/* BPMN 流程图 */
.ft-bpmn {
  margin-top: 14px;

  h4 {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0 0 8px;
    font-size: 14px;
  }
}

.ft-svg {
  width: 100%;
  height: 190px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.ft-node-label {
  font-size: 11px;
  fill: var(--el-text-color-primary);
}

.ft-edge-label {
  font-size: 10px;
  fill: var(--el-text-color-secondary);
}

/* 步骤条 */
.ft-steps-wrap {
  margin-top: 14px;
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

.ft-steps {
  display: flex;
  align-items: stretch;
  gap: 4px;
  margin-top: 12px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.ft-step {
  min-width: 88px;
  max-width: 110px;
  text-align: center;

  .ft-node {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    padding: 8px 4px 4px;
    border-radius: 6px;
    border: 1px solid var(--el-border-color-lighter);
    background: var(--el-fill-color-lighter);
    height: 100%;
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

.ft-arrow {
  align-self: flex-start;
  margin-top: 14px;
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

/* 当前任务 */
.ft-task {
  margin-top: 16px;
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
