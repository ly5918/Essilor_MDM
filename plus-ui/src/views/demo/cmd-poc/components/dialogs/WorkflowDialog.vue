<template>
  <div class="poc-dialog-body wf-config">
    <!-- 场景概要：平台固定项（不可修改） -->
    <el-descriptions class="m-b-12" :column="4" size="small" border>
      <el-descriptions-item label="业务场景">{{ config.sceneName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="流程编码">
        <span class="wf-mono">{{ config.flowCode || '—' }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="流程版本">v{{ config.version ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="部署状态">
        <el-tag :type="config.deployed ? 'success' : 'info'" size="small">
          {{ config.deployed ? '已部署' : '未部署' }}
        </el-tag>
      </el-descriptions-item>
    </el-descriptions>

    <el-tabs v-model="activeTab">
      <!-- ---------------- 1. 流程节点 ---------------- -->
      <el-tab-pane label="流程节点" name="nodes">
        <div class="wf-hint">
          <el-icon class="wf-hint-icon"><info-filled /></el-icon>
          <span class="wf-hint-text">
            场景编码、流程编码与主干 BU 初审为<b>平台固定项</b>（与 Warm-Flow 流程定义、引擎节点定位绑定）；
            其余节点规则按场景配置：命中条件、办理角色、会签或签、节点 SLA，以及是否启用。
          </span>
          <el-button link type="primary" size="small" icon="Share" @click="onViewGraph">查看泳道图</el-button>
        </div>

        <div class="wf-sub-title">
          泳道节点蓝图
          <span class="wf-sub-count">共 {{ config.nodes.length }} 个节点，其中 {{ configurableCount }} 个可配置</span>
        </div>
        <el-table v-loading="loading" :data="config.nodes" border size="small" class="data-table">
          <el-table-column label="阶段" width="90" prop="phaseName" />
          <el-table-column label="节点" min-width="140" prop="nodeName" show-overflow-tooltip />
          <el-table-column label="泳道 / 角色" min-width="150" prop="lane" show-overflow-tooltip />
          <el-table-column label="类型" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.nodeType === 'MANUAL' ? 'primary' : row.nodeType === 'GATEWAY' ? 'warning' : 'info'">
                {{ NODE_TYPE_LABELS[row.nodeType ?? ''] ?? row.nodeType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="约束" width="92" align="center">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="row.locked ? 'danger' : 'success'">
                {{ row.locked ? '平台固定' : '可配置' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="360" prop="constraint" show-overflow-tooltip />
        </el-table>

        <div class="wf-sub-title">
          节点路由规则
          <span class="wf-sub-count">停用即从该场景的工作流中移除该节点</span>
          <el-button class="wf-sub-action" link type="primary" size="small" icon="Plus" @click="onAddRule">
            新增节点规则
          </el-button>
        </div>
        <el-table :data="config.rules" border size="small" class="data-table">
          <el-table-column label="节点" width="176">
            <template #default="{ row }">
              <el-select v-if="row.id == null" v-model="row.nodeCode" size="small" style="width: 100%">
                <el-option v-for="opt in WORKFLOW_NODE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
              <template v-else>
                <span>{{ row.nodeName }}</span>
                <span class="wf-node-code">{{ row.nodeCode }}</span>
              </template>
            </template>
          </el-table-column>
          <el-table-column label="命中条件" min-width="240">
            <template #default="{ row }">
              <el-input v-model="row.conditionExpr" size="small" placeholder="留空表示命中所有申请" />
            </template>
          </el-table-column>
          <el-table-column label="办理角色" width="186">
            <template #default="{ row }">
              <el-select v-model="row.assigneeValue" size="small" style="width: 100%">
                <el-option v-for="opt in WORKFLOW_ASSIGNEE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="会签 / 或签" width="186">
            <template #default="{ row }">
              <el-select v-model="row.multiMode" size="small" style="width: 100%">
                <el-option v-for="opt in WORKFLOW_MULTI_MODE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="节点 SLA" width="118" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.slaHours" :min="1" :max="720" size="small" controls-position="right" style="width: 96px" />
            </template>
          </el-table-column>
          <el-table-column label="启用" width="84" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.status" active-value="0" inactive-value="1" :disabled="row.locked" />
            </template>
          </el-table-column>
        </el-table>
        <div class="table-foot">
          平台固定：主干 BU 初审不可停用；可增删：GC 决策等节点可停用或按场景新增（V6.1 待确认项 ——
          High End 必经 GC 决策，Mainstream 可只走 BU 初审）。
        </div>
      </el-tab-pane>

      <!-- ---------------- 2. 路由条件 ---------------- -->
      <el-tab-pane label="路由条件" name="route">
        <el-form :model="config" label-width="140px">
          <el-form-item label="场景启动条件">
            <el-input
              v-model="config.startConditions"
              style="width: 520px"
              placeholder="留空表示全部申请都走审批流程（如 risk_level == &quot;High&quot; || cross_bu）"
            />
          </el-form-item>
          <el-form-item label="表单标识">
            <el-input :model-value="config.formKey" disabled style="width: 240px" />
            <span class="wf-field-tip">平台固定：由业务场景决定的前端动态表单路由</span>
          </el-form-item>
        </el-form>

        <div class="wf-sub-title">当前节点路由规则（在「流程节点」页签维护）</div>
        <el-table :data="config.rules" border size="small" class="data-table">
          <el-table-column label="节点" width="120" prop="nodeName" />
          <el-table-column label="命中条件" min-width="260">
            <template #default="{ row }">
              <span class="wf-mono">{{ row.conditionExpr || '（无条件，命中所有申请）' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="目标角色" width="140" prop="assigneeValue" />
          <el-table-column label="范围" width="110" align="center" prop="scopeType" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === '1' ? 'info' : 'success'" effect="plain">
                {{ row.status === '1' ? '已停用' : '启用中' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="wf-sub-title">可用路由变量（只读，取值由引擎与业务上下文提供）</div>
        <el-table :data="WORKFLOW_ROUTE_VARIABLES" border size="small" class="data-table">
          <el-table-column label="变量" width="180">
            <template #default="{ row }">
              <span class="wf-mono">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="330" prop="desc" />
          <el-table-column label="取值来源" width="160" prop="source" />
        </el-table>
      </el-tab-pane>

      <!-- ---------------- 3. SLA 与升级 ---------------- -->
      <el-tab-pane label="SLA 与升级" name="sla">
        <el-form :model="config" label-width="140px">
          <el-form-item label="场景 SLA">
            <el-select v-model="config.slaHours" style="width: 240px">
              <el-option v-for="hour in WORKFLOW_SLA_HOURS_OPTIONS" :key="hour" :label="`${hour} 小时`" :value="hour" />
            </el-select>
            <span class="wf-field-tip">整体时限，超时按下方规则处理</span>
          </el-form-item>
          <el-form-item label="超时动作">
            <el-select v-model="config.timeoutAction" style="width: 240px">
              <el-option v-for="item in WORKFLOW_TIMEOUT_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="超时升级">
            <el-select v-model="config.escalateRule" style="width: 240px">
              <el-option v-for="item in WORKFLOW_ESCALATE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="邮件通知">
            <el-select v-model="config.notifyMode" style="width: 240px">
              <el-option v-for="item in WORKFLOW_NOTIFY_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="通知对象">
            <el-select v-model="config.notifyTargets" multiple style="width: 420px">
              <el-option v-for="item in WORKFLOW_NOTIFY_TARGET_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="table-foot">
          场景 SLA 为整体时限；节点级 SLA 在「流程节点」页签按规则单独设置（取更严者生效）。
        </div>
      </el-tab-pane>

      <!-- ---------------- 4. 版本与发布 ---------------- -->
      <el-tab-pane label="版本与发布" name="publish">
        <el-descriptions class="m-b-12" :column="3" size="small" border>
          <el-descriptions-item label="当前版本">v{{ config.version ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="发布状态">
            <el-tag :type="config.deployed ? 'success' : 'info'" size="small">
              {{ config.deployed ? '已发布到 Warm-Flow' : '未发布' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="配置归属">平台管理 · Workflow</el-descriptions-item>
        </el-descriptions>

        <el-form :model="config" label-width="140px">
          <el-form-item label="变更说明">
            <el-input v-model="config.changeNote" type="textarea" :rows="2" placeholder="填写本次配置变更原因，便于审计追溯" />
          </el-form-item>
        </el-form>

        <div class="wf-sub-title">版本历史</div>
        <el-table :data="versionHistory" border size="small" class="data-table">
          <el-table-column label="版本" width="90" prop="version" />
          <el-table-column label="变更说明" min-width="320" prop="note" show-overflow-tooltip />
          <el-table-column label="来源" width="140" prop="source" />
          <el-table-column label="时间" width="170" prop="time" />
        </el-table>

        <el-alert class="m-t-12" type="warning" :closable="false" show-icon>
          <template #title>
            POC 建议与边界：Workflow 部署位置与后台物理模型、High End / Mainstream 最终审批节点属待客户确认项；
            配置保存后写入平台配置表，下一次流程实例启动按新配置路由。
          </template>
        </el-alert>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { InfoFilled } from '@element-plus/icons-vue';
import { getFlowSceneConfig, saveFlowSceneConfig } from '@/api/demo/cmdPoc';
import type { FlowSceneConfigVO, FlowSceneRuleVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import {
  WORKFLOW_ASSIGNEE_OPTIONS,
  WORKFLOW_ESCALATE_OPTIONS,
  WORKFLOW_MULTI_MODE_OPTIONS,
  WORKFLOW_NODE_OPTIONS,
  WORKFLOW_NOTIFY_OPTIONS,
  WORKFLOW_NOTIFY_TARGET_OPTIONS,
  WORKFLOW_ROUTE_VARIABLES,
  WORKFLOW_SLA_HOURS_OPTIONS,
  WORKFLOW_TIMEOUT_OPTIONS
} from '../../constants/options';

/**
 * Workflow 配置（平台管理 › 平台管理 › Workflow › 工作流定义 → 某一行「配置」）
 *
 * 对齐 V6.1 总设计第 16 页「Workflow配置」：流程节点、路由条件、SLA、超时升级和邮件通知。
 * 四个页签：
 *   1) 流程节点 —— 泳道节点蓝图（标注平台固定 / 可配置）+ 节点路由规则（可增删）；
 *   2) 路由条件 —— 场景启动条件 + 当前节点命中条件 + 可用路由变量；
 *   3) SLA 与升级 —— 场景 SLA / 超时动作 / 超时升级 / 邮件通知；
 *   4) 版本与发布 —— 版本、发布状态、变更说明与 POC 边界。
 *
 * 说明：原「流程设计」页签（只读 el-steps 阶段链）已移除 —— 它就是泳道图的重复展示，
 * 现改为「查看泳道图」按钮直达 FlowGraphDialog，节点本身则在「流程节点」页签内配置。
 */
defineOptions({ name: 'CmdPocWorkflowDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const { openDialog } = useCmdPoc();

/** 由「工作流定义」页某一行「配置」带入的业务场景 */
const sceneCode = computed(() => (props.payload?.sceneCode as string | undefined) ?? '');
const sceneName = computed(() => (props.payload?.sceneName as string | undefined) ?? '');

const NODE_TYPE_LABELS: Record<string, string> = { AUTO: '自动', MANUAL: '人工', GATEWAY: '网关' };

const activeTab = ref('nodes');
const loading = ref(false);
const config = ref<FlowSceneConfigVO>(emptyConfig());

function emptyConfig(): FlowSceneConfigVO {
  return {
    sceneCode: '',
    sceneName: '',
    flowCode: '',
    deployed: false,
    notifyTargets: [],
    nodes: [],
    rules: []
  };
}

const configurableCount = computed(() => config.value.nodes.filter(node => node.configurable).length);

/** 版本历史：POC 阶段取当前版本 + 变更说明渲染（后续可接 Warm-Flow 版本表） */
const versionHistory = computed(() => [
  {
    version: `v${config.value.version ?? 1}`,
    note: config.value.changeNote || '平台初始化配置',
    source: '当前生效',
    time: '2026-09-18 10:47'
  }
]);

const load = async () => {
  if (!sceneCode.value) return;
  loading.value = true;
  try {
    config.value = await getFlowSceneConfig(sceneCode.value);
  } finally {
    loading.value = false;
  }
};

/** 查看泳道图：图形化流程设计统一由泳道图承载，本弹窗只做配置 */
const onViewGraph = () => {
  openDialog('flowGraph', {
    sceneCode: sceneCode.value,
    sceneName: config.value.sceneName || sceneName.value,
    flowCode: config.value.flowCode
  });
};

/** 新增节点规则（场景级「增加工作流节点」） */
const onAddRule = () => {
  const draft: FlowSceneRuleVO = {
    id: null,
    nodeCode: 'gc_review',
    nodeName: '',
    conditionExpr: '',
    assigneeType: 'ROLE',
    assigneeValue: 'GC_STEWARD',
    scopeType: 'GC',
    multiMode: 'ANY',
    slaHours: config.value.slaHours ?? 48,
    status: '0',
    locked: false
  };
  config.value.rules.push(draft);
};

/** 保存：只提交场景级可配置项与节点规则，平台固定项由后端保护 */
const submit = async (): Promise<string> => {
  if (!sceneCode.value) return '未指定业务场景，未执行保存';
  return saveFlowSceneConfig(sceneCode.value, {
    slaHours: config.value.slaHours,
    escalateRule: config.value.escalateRule,
    startConditions: config.value.startConditions,
    timeoutAction: config.value.timeoutAction,
    notifyMode: config.value.notifyMode,
    notifyTargets: config.value.notifyTargets,
    changeNote: config.value.changeNote,
    rules: config.value.rules.map(rule => ({
      id: rule.id ?? null,
      nodeCode: rule.nodeCode,
      nodeName: rule.nodeName,
      conditionExpr: rule.conditionExpr,
      assigneeType: rule.assigneeType,
      assigneeValue: rule.assigneeValue,
      scopeType: rule.scopeType,
      multiMode: rule.multiMode,
      slaHours: rule.slaHours,
      status: rule.status,
      remark: rule.remark
    }))
  });
};

onMounted(load);

defineExpose({ submit });
</script>

<style scoped>
.wf-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 10px;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-regular);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 6px;
}

.wf-hint-icon {
  flex-shrink: 0;
  color: var(--el-color-primary);
}

.wf-hint-text {
  flex: 1;
}

.wf-sub-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 14px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.wf-hint + .wf-sub-title {
  margin-top: 4px;
}

.wf-sub-count {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.wf-sub-action {
  margin-left: auto;
}

.wf-node-code {
  margin-left: 6px;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.wf-mono {
  font-family: var(--el-font-family-mono, 'SFMono-Regular', Consolas, monospace);
  font-size: 12px;
}

.wf-field-tip {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
