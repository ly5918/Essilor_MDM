<template>
  <div class="poc-dialog-body">
    <!-- 待归位客户信息 -->
    <el-alert type="warning" :closable="false" show-icon class="m-b-12">
      <template #title>
        <b>待归位主数据：</b>{{ info.name || '—' }}
        <span class="note-sep">·</span>
        <span>{{ info.oneId || '—' }}</span>
        <span class="note-sep">·</span>
        <span>BU {{ info.bu || '—' }}</span>
      </template>
      <div class="assign-hint">
        该客户已审批通过、正式成为主数据（Golden Record）。把它挂到某个上级节点之下，
        它才会出现在左侧 A3-A2-A1 层级树里；在此之前它只在本页「待归位主数据」区可见。
      </div>
    </el-alert>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="140px">
      <el-form-item label="目标父节点" prop="parentId">
        <el-select
          v-model="form.parentId"
          filterable
          placeholder="选择上级 A3 / A2 节点"
          style="width: 100%"
          @change="runCheck"
        >
          <el-option
            v-for="item in parentOptions"
            :key="item.oneId"
            :label="`[${item.level}] ${item.name}（${item.oneId}）`"
            :value="item.oneId"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="归位后级别">
        <el-input :model-value="derivedLevel" readonly />
        <div class="assign-tip">级别由服务端按父节点自动推导：A3 → A2 → A1，最多三级，系统不做人工指定。</div>
      </el-form-item>

      <el-form-item label="变更原因" prop="changeReason">
        <el-input v-model="form.changeReason" placeholder="如 新门店归属确认" />
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
      </el-form-item>
    </el-form>

    <!-- 归位预检：与提交时完全一致的后端校验（Loop Check + 级别推导 + 路径预览 + 跨 BU） -->
    <div class="assign-check">
      <div class="assign-check-head">
        <span class="assign-check-title">归位预检（实时读库）</span>
        <el-button size="small" :loading="checking" @click="runCheck">重新校验</el-button>
      </div>

      <el-table v-loading="checking" :data="check.checks" border size="small" class="data-table">
        <el-table-column label="检测项" prop="label" min-width="150" />
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              effect="dark"
              :type="row.checkResult === 'PASS' ? 'success' : row.checkResult === 'FAIL' ? 'danger' : 'warning'"
            >
              {{ row.checkResult }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结论" prop="message" min-width="280" show-overflow-tooltip />
        <el-table-column label="冲突路径 / 建议" min-width="200">
          <template #default="{ row }">
            <div v-if="row.conflictPath" class="assign-path-error">{{ row.conflictPath }}</div>
            <span v-else-if="row.suggestion">{{ row.suggestion }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
      </el-table>

      <el-alert
        v-if="check.checks.length"
        :type="check.passed ? 'success' : 'error'"
        :closable="false"
        show-icon
        class="m-t-8"
      >
        <template #title>
          <template v-if="check.passed">
            预检通过 · 归位后完整路径：<b>{{ check.previewPathNames || check.previewPath || '—' }}</b>
          </template>
          <template v-else>校验未通过 · BLOCKED：{{ check.blockedReason || '存在阻塞项' }}</template>
        </template>
      </el-alert>
      <el-empty v-else description="请选择目标父节点，系统将实时校验并给出归位后的完整路径" :image-size="60" />

      <el-alert
        v-if="check.requiresGcApproval"
        type="warning"
        :closable="false"
        show-icon
        class="m-t-8"
      >
        <template #title>跨 BU 归位：本操作需升级 GC Scope 审批后方可生效。</template>
      </el-alert>
    </div>

    <el-alert type="info" :closable="false" show-icon class="m-t-12">
      <template #title>
        <b>归位时系统会做什么：</b>环路检测 → 级别推导 → 完整路径重建 → 祖先节点计数刷新 → 层级关系留痕（不覆盖历史）。
      </template>
    </el-alert>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { assignHierarchyNode, getHierarchy, validateHierarchyRelation } from '@/api/demo/cmdPoc';
import type { HierarchyAssignForm, HierarchyNodeVO, HierarchyValidateVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocHierarchyAssignDialog' });

const props = defineProps<{
  payload?: {
    oneId?: string;
    name?: string;
    bu?: string;
    suggestedLevel?: string;
    parentId?: string;
    remark?: string;
  };
}>();

const { markHierarchyChanged } = useCmdPoc();

const formRef = ref<FormInstance>();
const checking = ref(false);

const info = computed(() => ({
  oneId: props.payload?.oneId ?? '',
  name: props.payload?.name ?? '',
  bu: props.payload?.bu ?? ''
}));

const form = reactive<HierarchyAssignForm>({
  oneId: props.payload?.oneId ?? '',
  parentId: '',
  changeReason: '新门店归属确认',
  remark: props.payload?.remark as string | undefined
});

/** 可选父节点：只有 A3 / A2 能作为父节点（A1 已是末端） */
interface ParentOption {
  oneId: string;
  name: string;
  level: string;
  path: string;
  depth: number;
}
const parentOptions = ref<ParentOption[]>([]);

const rules: FormRules<HierarchyAssignForm> = {
  parentId: [{ required: true, message: '请选择目标父节点', trigger: 'change' }],
  changeReason: [{ required: true, message: '请填写变更原因', trigger: 'blur' }]
};

const selectedParent = computed(() => parentOptions.value.find(item => item.oneId === form.parentId));

const emptyCheck = (): HierarchyValidateVO => ({
  checkCode: '',
  passed: false,
  relationLabel: '',
  parentName: '',
  childName: '',
  parentLevel: '',
  parentDepth: 0,
  childLevel: '',
  childDepth: 0,
  previewPath: '',
  previewPathNames: '',
  crossBu: false,
  requiresGcApproval: false,
  relationType: '',
  maxDepth: 3,
  childMounted: false,
  childCurrentParentOneId: '',
  childCurrentLevel: '',
  checks: [],
  executeTime: '',
  durationMs: 0
});

/** 预检结论：直接来自后端，与提交时口径一致 */
const check = ref<HierarchyValidateVO>(emptyCheck());

/** 归位后级别：优先取服务端推导结果，未校验时按父节点深度本地兜底 */
const derivedLevel = computed(() => {
  if (check.value.childLevel) return check.value.childLevel;
  const parent = selectedParent.value;
  if (!parent) return '—（选择父节点后自动推导）';
  const depth = parent.depth + 1;
  if (depth > 3) return '不可归位：父节点已是末端 A1';
  return depth === 2 ? 'A2' : 'A1';
});

const runCheck = async () => {
  if (!form.parentId || !info.value.oneId) {
    check.value = emptyCheck();
    return;
  }
  checking.value = true;
  try {
    check.value = await validateHierarchyRelation({
      parentOneId: form.parentId,
      childOneId: info.value.oneId,
      changeReason: form.changeReason || undefined
    });
  } finally {
    checking.value = false;
  }
};

watch(
  () => props.payload?.parentId,
  value => {
    if (value) form.parentId = value;
  }
);

onMounted(async () => {
  const tree = await getHierarchy();
  const options: ParentOption[] = [];
  const walk = (nodes: HierarchyNodeVO[], depth: number) => {
    nodes.forEach(node => {
      // 只收集还能挂子节点的层级（A3 / A2）
      if (depth < 3) {
        options.push({
          oneId: node.oneId,
          name: node.name,
          level: node.level,
          path: node.path,
          depth
        });
      }
      walk(node.children ?? [], depth + 1);
    });
  };
  walk(tree, 1);
  parentOptions.value = options;
  if (!form.parentId && options.length) {
    // 默认选中第一个 A2（最常见的门店归属），没有 A2 时退回第一个可选项
    const preferred = options.find(item => item.level === 'A2') ?? options[0];
    form.parentId = preferred.oneId;
  }
  await runCheck();
});

const submit = async (): Promise<string> => {
  await formRef.value?.validate();
  await runCheck();
  if (check.value.checks.length && !check.value.passed) {
    throw new Error(check.value.blockedReason || '校验未通过，请修正后再归位');
  }
  const message = await assignHierarchyNode(form);
  // 通知客户层级页与客户列表重新拉取数据
  markHierarchyChanged();
  return message;
};

defineExpose({ submit });
</script>

<style lang="scss" scoped>
.assign-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
}

.assign-tip {
  font-size: 12px;
  color: var(--g-text2);
  line-height: 1.6;
}

.assign-check {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--g-divider);
  border-radius: 8px;
  background: #fafcfe;
}

.assign-check-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.assign-check-title {
  font-size: 13px;
  font-weight: 700;
}

.assign-path-error {
  padding: 4px 6px;
  background: #fff0f2;
  border: 1px solid #f3c3c6;
  border-radius: 6px;
  color: #9e3c48;
  font-size: 12px;
  line-height: 1.5;
}
</style>
