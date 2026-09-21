<template>
  <div class="fg-body">
    <!-- 场景信息条 -->
    <div class="fg-head">
      <div class="fg-scene">
        <span class="fg-scene-tag">{{ isInstance ? '实例' : '场景' }}</span>
        <b>{{ sceneName }}（{{ sceneCode }}）</b>
        <span class="fg-scene-desc">
          {{ graph.flowCode }}<template v-if="isInstance"> · {{ taskNo }}</template>
        </span>
      </div>
      <div class="fg-head-meta">
        <el-tag size="small" type="success" effect="plain">Warm-Flow 引擎已部署</el-tag>
        <el-tag size="small" type="info" effect="plain">定义 ID {{ graph.definitionId ?? '—' }}</el-tag>
        <el-tag v-if="isInstance" size="small" :type="progressTagType" effect="plain">
          已完成 {{ doneCount }}/{{ graph.nodes.length }} 步
        </el-tag>
      </div>
    </div>

    <!-- 泳道图：横向为阶段（7 列），纵向为泳道（6 行） -->
    <div class="fg-lane-wrap">
      <h4>
        V6.1 泳道图
        <el-tag v-if="isInstance" size="small" type="success" effect="plain">实例视图 · 按实际进度点亮</el-tag>
        <el-tag v-else size="small" type="warning" effect="plain">定义视图 · 尚未启动实例</el-tag>
      </h4>
      <div class="fg-lane-scroll">
        <svg class="fg-svg" :viewBox="viewBox" :style="{ minWidth: `${CANVAS_W}px` }" preserveAspectRatio="xMinYMin meet">
          <defs>
            <marker id="fg-arrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto">
              <path d="M0,0 L7,3 L0,6 Z" :fill="arrowColor" />
            </marker>
          </defs>

          <!-- 泳道背景带 + 泳道标签 -->
          <g v-for="(lane, li) in laneRows" :key="`lane-${lane.name}`">
            <rect
              x="0"
              :y="lane.y - HALF_LANE"
              :width="CANVAS_W"
              :height="LANE_H"
              :fill="li % 2 === 0 ? 'var(--el-fill-color-lighter)' : 'var(--el-bg-color)'"
            />
            <rect x="0" :y="lane.y - HALF_LANE" :width="LABEL_W" :height="LANE_H" fill="var(--el-fill-color-light)" />
            <foreignObject :x="6" :y="lane.y - 20" :width="LABEL_W - 12" height="40">
              <div class="fg-lane-label">{{ lane.name }}</div>
            </foreignObject>
          </g>

          <!-- 泳道/阶段分隔线 -->
          <g stroke="var(--el-border-color-lighter)" stroke-width="1">
            <line v-for="lane in laneRows" :key="`ln-${lane.name}`" x1="0" :y1="lane.y - HALF_LANE" :x2="CANVAS_W" :y2="lane.y - HALF_LANE" />
            <line :x1="LABEL_W" y1="0" :x2="LABEL_W" :y2="CANVAS_H" />
            <line
              v-for="p in phaseCols"
              :key="`pv-${p.name}`"
              :x1="p.x - HALF_COL"
              y1="0"
              :x2="p.x - HALF_COL"
              :y2="CANVAS_H"
              stroke-dasharray="3 3"
            />
          </g>

          <!-- 阶段表头 -->
          <g v-for="p in phaseCols" :key="`ph-${p.name}`">
            <rect :x="p.x - HALF_COL + 4" y="6" :width="COL_GAP - 8" height="24" rx="4" fill="var(--el-color-primary-light-9)" />
            <text :x="p.x" y="23" class="fg-phase-label" text-anchor="middle">{{ p.index }}. {{ p.name }}</text>
          </g>

          <!-- 连线 -->
          <g>
            <template v-for="(e, i) in graph.edges" :key="`${e.from}-${e.to}-${i}`">
              <path
                v-if="edgePath(e)"
                :d="edgePath(e)!"
                fill="none"
                :stroke="e.passed ? doneColor : pendingColor"
                stroke-width="1.5"
                marker-end="url(#fg-arrow)"
              />
            </template>
          </g>

          <!-- 节点 -->
          <g
            v-for="n in graph.nodes"
            :key="n.nodeCode"
            class="fg-node-g"
            :class="{ 'is-selected': selected?.nodeCode === n.nodeCode }"
            @click="selected = n"
          >
            <title>{{ n.nodeName }}（{{ n.lane }} · {{ n.phaseName }}）{{ n.note ? ' — ' + n.note : '' }}</title>
            <rect
              v-if="n.shape === 'RECT'"
              :x="n.x - NODE_W / 2"
              :y="n.y - NODE_H / 2"
              :width="NODE_W"
              :height="NODE_H"
              rx="6"
              :fill="fillOf(n)"
              :stroke="strokeOf(n)"
              :stroke-width="selected?.nodeCode === n.nodeCode ? 2.5 : 1.5"
            />
            <polygon
              v-else-if="n.shape === 'DIAMOND'"
              :points="diamondPoints(n)"
              :fill="fillOf(n)"
              :stroke="strokeOf(n)"
              :stroke-width="selected?.nodeCode === n.nodeCode ? 2.5 : 1.5"
            />
            <circle
              v-else
              :cx="n.x"
              :cy="n.y"
              :r="16"
              :fill="fillOf(n)"
              :stroke="strokeOf(n)"
              :stroke-width="selected?.nodeCode === n.nodeCode ? 2.5 : 1.5"
            />
            <text :x="n.x" :y="n.y + 4" class="fg-node-icon" text-anchor="middle">{{ iconOf(n) }}</text>
            <text
              v-for="(line, idx) in wrapLabel(n.nodeName)"
              :key="idx"
              :x="n.x"
              :y="n.y + NODE_H / 2 + 13 + idx * 12"
              class="fg-node-label"
              text-anchor="middle"
            >
              {{ line }}
            </text>
          </g>
        </svg>
      </div>
    </div>

    <!-- 节点详情 -->
    <div class="fg-detail">
      <h4>节点详情</h4>
      <template v-if="selected">
        <p><b>{{ selected.nodeName }}</b> <span class="fg-code">{{ selected.nodeCode }}</span></p>
        <p>阶段：{{ selected.phaseName ?? '—' }}（P{{ selected.phase ?? '—' }}） · 泳道：{{ selected.lane ?? '—' }}</p>
        <p>类型：{{ nodeTypeText(selected) }} · 图形：{{ shapeText(selected.shape) }}</p>
        <p v-if="selected.note">{{ selected.note }}</p>
      </template>
      <p v-else class="fg-hint">点击上方泳道图中的任意节点，查看该节点的阶段、泳道与业务说明。</p>
    </div>

    <!-- 节点清单 -->
    <div class="fg-legend">
      <h4>节点清单（{{ graph.nodes.length }} 个节点 / {{ graph.edges.length }} 条连线）</h4>
      <el-table :data="graph.nodes" border size="small" class="data-table" max-height="260">
        <el-table-column label="顺序" type="index" width="60" align="center" />
        <el-table-column label="阶段" prop="phaseName" width="90" align="center" />
        <el-table-column label="泳道（角色）" prop="lane" min-width="150" show-overflow-tooltip />
        <el-table-column label="节点名称" min-width="140">
          <template #default="{ row }">
            <span class="fg-row-name" @click="selected = row as FlowGraphNodeVO">{{ row.nodeName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="节点编码" prop="nodeCode" width="120" />
        <el-table-column label="图形" width="90" align="center">
          <template #default="{ row }">{{ shapeText(row.shape) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="nodeStatusTagType(row.status)">{{ nodeStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 工作流步骤执行日志：按客户 One ID 串联每一步（cmd_workflow_step_log） -->
    <div v-if="isInstance" v-loading="stepsLoading" class="fg-steps">
      <h4>
        工作流步骤执行日志
        <el-tag size="small" type="success" effect="plain">One ID 全链路追溯</el-tag>
        <span class="fg-steps-hint">每一步均落库（提交 / 系统自动检查 / 人工决策），共 {{ steps.length }} 步</span>
      </h4>
      <el-table :data="steps" size="small" max-height="260">
        <el-table-column label="#" prop="stepSeq" width="46" align="center" />
        <el-table-column label="节点" prop="nodeName" min-width="130" show-overflow-tooltip />
        <el-table-column label="类型" width="88" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.stepType === 'BUSINESS' ? 'warning' : row.stepType === 'SUBMIT' ? 'primary' : 'info'" effect="plain">
              {{ row.stepType === 'BUSINESS' ? '人工决策' : row.stepType === 'SUBMIT' ? '提交' : '系统自动' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="动作" prop="actionName" width="100" />
        <el-table-column label="操作人" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.operatorName }}<span v-if="row.operatorRole" class="fg-role">（{{ row.operatorRole }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="状态流转" min-width="150">
          <template #default="{ row }">
            <template v-if="row.fromStatus || row.toStatus">{{ row.fromStatus ?? '—' }} → {{ row.toStatus ?? '—' }}</template>
            <span v-else class="fg-role">{{ row.opinion }}</span>
          </template>
        </el-table-column>
        <el-table-column label="意见" prop="opinion" min-width="200" show-overflow-tooltip />
        <el-table-column label="时间" prop="createTime" width="160" />
      </el-table>
    </div>

    <!-- 引擎关联说明 -->
    <div class="fg-engine">
      <b>Warm-Flow 关联：</b>流程编码 {{ graph.flowCode }} · 定义 ID {{ graph.definitionId ?? '—' }}。
      泳道图为总设计业务蓝图（7 阶段 × 6 泳道）；引擎侧将
      <b>BU Scope 初审 / GC Scope 决策</b> 建模为用户任务，其余自动节点由业务侧 Service 执行，
      结果以流程变量驱动路由。
      <template v-if="isInstance">
        当前为<b>实例视图</b>（{{ taskNo }}）：绿色 = 已完成，蓝色 = 当前节点，灰色 = 待执行，红色 = 已终止。
      </template>
      <template v-else>
        当前为<b>定义视图</b>（蓝图，节点全部待执行）。在「流程实例记录」中点某条记录的「泳道图」，
        可按该次执行的实际进度点亮节点。
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { FlowGraphEdgeVO, FlowGraphNodeVO, FlowGraphVO, WorkflowStepVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocFlowSwimlane' });

const props = defineProps<{
  /** 泳道图数据（场景蓝图或实例进度） */
  graph: FlowGraphVO;
  /** 场景编码（信息条展示） */
  sceneCode?: string;
  /** 场景名称（信息条展示） */
  sceneName?: string;
  /** 任务编号：传入 = 实例视图（按实际进度点亮），不传 = 定义视图（蓝图） */
  taskNo?: string;
  /** 是否实例视图 */
  isInstance?: boolean;
  /** 工作流步骤执行日志（实例视图展示，数据库 cmd_workflow_step_log） */
  steps?: WorkflowStepVO[];
  /** 步骤日志加载状态 */
  stepsLoading?: boolean;
}>();

// 与后端 CmdFlowEngineServiceImpl 布局常量保持一致（阶段分列 × 泳道分行）
const BASE_X = 300;
const COL_GAP = 168;
const BASE_Y = 70;
const LANE_GAP = 92;
const LABEL_W = 250;
const NODE_W = 84;
const NODE_H = 40;
const HALF_LANE = LANE_GAP / 2;
const LANE_H = LANE_GAP - 8;
const HALF_COL = COL_GAP / 2;

const CANVAS_W = BASE_X + 6 * COL_GAP + 80;
const CANVAS_H = BASE_Y + 5 * LANE_GAP + 70;

const DEFAULT_LANES = [
  'Business User',
  '系统自动处理',
  'Data Steward BU Scope',
  'Data Steward GC Scope',
  'Platform Admin',
  'Auditor（只读）'
];

const selected = ref<FlowGraphNodeVO | null>(null);

const viewBox = `0 0 ${CANVAS_W} ${CANVAS_H}`;

const doneColor = 'var(--el-color-success)';
const currentColor = 'var(--el-color-primary)';
const pendingColor = 'var(--el-border-color)';
const arrowColor = 'var(--el-text-color-secondary)';

/** 已完成节点数（实例视图的进度口径） */
const doneCount = computed(() => (props.graph?.nodes ?? []).filter(n => n.status === 'COMPLETED').length);
const progressTagType = computed(() => (doneCount.value === (props.graph?.nodes.length ?? 0) ? 'success' : 'warning'));

/** 泳道行：标签 + 行中心 y（泳道无节点时按序号推算，保证 6 行始终呈现） */
const laneRows = computed(() => {
  const lanes = props.graph?.lanes?.length ? props.graph.lanes : DEFAULT_LANES;
  return lanes.map((name, i) => ({ name, y: BASE_Y + i * LANE_GAP }));
});

/** 阶段列：名称 + 列中心 x */
const phaseCols = computed(() => {
  const nodes = props.graph?.nodes ?? [];
  const cols: { index: number; name: string; x: number }[] = [];
  const seen = new Set<number>();
  for (const n of nodes) {
    const phase = n.phase ?? 1;
    if (n.phaseName && !seen.has(phase)) {
      seen.add(phase);
      cols.push({ index: phase, name: n.phaseName, x: BASE_X + (phase - 1) * COL_GAP });
    }
  }
  return cols;
});

const fillOf = (n: FlowGraphNodeVO) => {
  if (n.status === 'COMPLETED') return 'var(--el-color-success-light-9)';
  if (n.status === 'CURRENT') return 'var(--el-color-primary-light-9)';
  if (n.status === 'TERMINATED') return 'var(--el-color-danger-light-9)';
  return 'var(--el-fill-color-lighter)';
};

const strokeOf = (n: FlowGraphNodeVO) => {
  if (n.status === 'COMPLETED') return doneColor;
  if (n.status === 'CURRENT') return currentColor;
  if (n.status === 'TERMINATED') return 'var(--el-color-danger)';
  return pendingColor;
};

const NODE_STATUS_TEXT: Record<string, string> = {
  COMPLETED: '已完成',
  CURRENT: '进行中',
  TERMINATED: '已终止',
  PENDING: '待执行'
};
const NODE_STATUS_TAG: Record<string, 'success' | 'primary' | 'danger' | 'info'> = {
  COMPLETED: 'success',
  CURRENT: 'primary',
  TERMINATED: 'danger',
  PENDING: 'info'
};
const nodeStatusText = (v?: string) => NODE_STATUS_TEXT[v ?? ''] ?? '待执行';
const nodeStatusTagType = (v?: string) => NODE_STATUS_TAG[v ?? ''] ?? 'info';

const diamondPoints = (n: FlowGraphNodeVO) =>
  `${n.x},${n.y - 24} ${n.x + 40},${n.y} ${n.x},${n.y + 24} ${n.x - 40},${n.y}`;

/** 连线：同泳道直连，跨泳道折线（水平→垂直→水平），避免穿过节点 */
const edgePath = (e: FlowGraphEdgeVO) => {
  const nodes = props.graph?.nodes ?? [];
  const from = nodes.find(n => n.nodeCode === e.from);
  const to = nodes.find(n => n.nodeCode === e.to);
  if (!from || !to) return null;
  if (from.y === to.y) {
    const x1 = from.x + NODE_W / 2;
    const x2 = to.x - NODE_W / 2;
    return `M${x1},${from.y} L${x2},${to.y}`;
  }
  // 跨泳道：先向右出，再垂直换道，再进入目标节点左侧
  const x1 = from.x + NODE_W / 2;
  const x2 = to.x - NODE_W / 2;
  const midX = x1 + 26;
  if (x2 <= midX) {
    // 目标在左侧（回退类连线）：走节点下方绕行
    const detourY = Math.max(from.y, to.y) + 34;
    return `M${x1},${from.y} L${x1},${detourY} L${x2 - 20},${detourY} L${x2 - 20},${to.y} L${x2},${to.y}`;
  }
  return `M${x1},${from.y} L${midX},${from.y} L${midX},${to.y} L${x2},${to.y}`;
};

const shapeText = (shape: string) => (shape === 'CIRCLE' ? '开始/结束' : shape === 'DIAMOND' ? '网关' : '任务');
const nodeTypeText = (n: FlowGraphNodeVO) => (n.shape === 'DIAMOND' ? '分支网关（规则路由）' : n.shape === 'CIRCLE' ? '起点 / 终点' : n.nodeType === 3 ? '网关任务' : '处理节点');

/** 节点图标：网关 ◇ / 开始结束 ○ / 其余 ● */
const iconOf = (n: FlowGraphNodeVO) => (n.shape === 'DIAMOND' ? '◇' : n.shape === 'CIRCLE' ? '○' : '●');

/** 节点名称折行（CJK 与拉丁混排按字符数切分，最多 2 行） */
const wrapLabel = (name: string): string[] => {
  const text = name ?? '';
  const LIMIT = 9;
  if (text.length <= LIMIT) return [text];
  const lines: string[] = [];
  for (let i = 0; i < text.length && lines.length < 2; i += LIMIT) {
    lines.push(text.slice(i, i + LIMIT));
  }
  if (text.length > LIMIT * 2) {
    lines[1] = `${lines[1].slice(0, LIMIT - 1)}…`;
  }
  return lines;
};
</script>

<style scoped lang="scss">
.fg-body {
  min-height: 200px;
}

.fg-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.fg-scene {
  display: flex;
  align-items: center;
  gap: 8px;

  .fg-scene-tag {
    padding: 1px 8px;
    border-radius: 4px;
    font-size: 12px;
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }

  .fg-scene-desc {
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}

.fg-head-meta {
  display: flex;
  gap: 6px;
}

/* 泳道图 */
.fg-lane-wrap {
  margin-top: 14px;

  h4 {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0 0 8px;
    font-size: 14px;
  }
}

.fg-lane-scroll {
  overflow-x: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.fg-svg {
  display: block;
  width: 100%;
  height: 520px;
}

.fg-phase-label {
  font-size: 11px;
  font-weight: 600;
  fill: var(--el-color-primary);
}

.fg-lane-label {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  font-size: 11px;
  font-weight: 600;
  text-align: center;
  line-height: 1.2;
  color: var(--el-text-color-regular);
}

.fg-node-g {
  cursor: pointer;

  &:hover .fg-node-label {
    fill: var(--el-color-primary);
  }
}

.fg-node-icon {
  font-size: 12px;
  fill: var(--el-text-color-secondary);
}

.fg-node-label {
  font-size: 10px;
  fill: var(--el-text-color-primary);
}

/* 节点详情 */
.fg-detail {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);

  h4 {
    margin: 0 0 6px;
    font-size: 14px;
  }

  p {
    margin: 3px 0;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .fg-code {
    margin-left: 6px;
    padding: 0 6px;
    border-radius: 3px;
    font-size: 11px;
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color-dark);
  }

  .fg-hint {
    margin: 0;
  }
}

/* 节点清单 */
.fg-legend {
  margin-top: 14px;

  h4 {
    margin: 0 0 8px;
    font-size: 14px;
  }
}

.fg-row-name {
  color: var(--el-color-primary);
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}

.fg-engine {
  margin-top: 14px;
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

/* 工作流步骤日志 */
.fg-steps {
  margin-top: 14px;

  h4 {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    margin: 0 0 8px;
    font-size: 14px;
  }

  .fg-steps-hint {
    font-size: 12px;
    font-weight: 400;
    color: var(--el-text-color-secondary);
  }

  .fg-role {
    color: var(--el-text-color-secondary);
  }
}
</style>
