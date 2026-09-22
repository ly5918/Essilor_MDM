<template>
  <div class="poc-dialog-body">
    <!--
      发起客户合并（总设计 MERGE 场景「发现候选 → 证据准备」入口）：
      存量主档之间确认疑似重复后，由 Steward 发起合并请求。
      提交后创建 sceneCode=MERGE 的审批待办（BU Scope 初审 → GC Scope 决策），
      批准后自动执行 Golden Record 合并、Legacy 交叉引用与审计。
    -->
    <el-alert
      class="m-b-12"
      type="warning"
      :closable="false"
      show-icon
      title="合并语义：源记录将并入目标 One ID（Golden Record 保持目标稳定），源记录状态变为「已合并」并保留 Legacy 交叉引用；该请求需 BU Scope 初审，跨BU时升级 GC Scope 决策。"
    />

    <el-form label-width="110px">
      <el-form-item label="合并源">
        <el-input :model-value="`${payload?.oneId ?? ''} · ${payload?.name ?? ''}`" disabled />
      </el-form-item>
      <el-form-item label="合并目标" required>
        <el-select
          v-model="targetOneId"
          filterable
          :disabled="candidates.length === 0"
          :placeholder="candidates.length ? '选择要并入的存量主档（One ID）' : '未发现重复候选（需同名或同信用代码）'"
          style="width: 100%"
        >
          <el-option
            v-for="item in candidates"
            :key="item.oneId"
            :value="item.oneId"
            :label="`${item.oneId} · ${item.legalName}（${item.bu || '—'}）${item.reason ? ' · ' + item.reason : ''}`"
          />
        </el-select>
        <div v-if="!candidates.length" class="md-hint">仅当存在疑似重复（名称或信用代码命中）时才可发起合并。</div>
      </el-form-item>
      <el-form-item label="发起原因">
        <el-input
          v-model="reason"
          type="textarea"
          :rows="3"
          maxlength="200"
          placeholder="说明业务背景与证据（如：同一客户在两个 BU 分别建档，信用代码 / 名称一致）"
        />
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { launchCustomerMerge, listCustomers, getCustomerDetail } from '@/api/demo/cmdPoc';
import type { CustomerVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocMergeDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

type MergeCandidate = CustomerVO & { reason?: string };
const candidates = ref<MergeCandidate[]>([]);
const targetOneId = ref('');
const reason = ref('');
const submitting = ref(false);

/** 提交（DialogHost 底部「发起合并申请」按钮调用） */
const submit = async (): Promise<string> => {
  const sourceOneId = String(props.payload?.oneId ?? '');
  if (!targetOneId.value) {
    throw new Error('请选择合并目标 One ID');
  }
  submitting.value = true;
  try {
    const taskNo = await launchCustomerMerge(sourceOneId, targetOneId.value, reason.value);
    return `合并请求已发起：${taskNo}（${sourceOneId} → ${targetOneId.value}），请到治理与审批跟踪`;
  } finally {
    submitting.value = false;
  }
};

onMounted(async () => {
  const sourceOneId = String(props.payload?.oneId ?? '');
  // 取源主档详情（名称 / 信用代码），用于收敛重复候选
  const source = sourceOneId ? await getCustomerDetail(sourceOneId).catch(() => null) : null;
  const srcName = (source?.legalName ?? String(props.payload?.name ?? '')).trim();
  const srcCode = (source?.creditCode ?? '').trim();
  // 候选目标 = 存量 active 主档中与源构成重复命中的记录（同名 或 同信用代码），排除自身与已合并
  const page = await listCustomers({ pageNum: 1, pageSize: 200 });
  const rows = (page.rows ?? []) as MergeCandidate[];
  candidates.value = rows
    .filter(item => {
      if (!item.oneId || item.oneId === sourceOneId || item.status !== 'active') return false;
      const sameName = !!srcName && item.legalName?.trim() === srcName;
      const sameCode = !!srcCode && !!item.creditCode && item.creditCode.trim() === srcCode;
      if (!sameName && !sameCode) return false;
      item.reason = sameName && sameCode ? '同名且同信用代码' : sameCode ? '信用代码相同' : '名称相同';
      return true;
    })
    .toSorted((a, b) => String(a.reason).localeCompare(String(b.reason)));
});

defineExpose({ submit });
</script>

<style scoped>
.md-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  margin-top: 4px;
}
</style>
