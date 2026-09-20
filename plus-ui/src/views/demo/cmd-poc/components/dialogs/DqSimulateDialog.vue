<template>
  <div class="poc-dialog-body">
    <el-alert type="info" :closable="false" show-icon title="DQ规则模拟测试：查看、新增、删除规则；点击模拟执行测试。" />
    <el-table border :data="rules" class="data-table m-t-12" max-height="360">
      <el-table-column label="规则编码" prop="ruleCode" min-width="140" />
      <el-table-column label="规则名称" prop="ruleName" min-width="160" />
      <el-table-column label="维度" prop="dimension" width="120" align="center" />
      <el-table-column label="作用" prop="role" width="120" align="center" />
      <el-table-column label="阈值" prop="threshold" width="100" align="center" />
      <el-table-column label="结果" prop="result" width="100" align="center" />
      <el-table-column label="启用" width="80" align="center">
        <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="onSimulate(asRule(row))">模拟</el-button>
          <el-button link type="danger" @click="onDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button class="m-t-12" type="primary" plain size="small" @click="onAddRule">新增规则</el-button>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { listDqRules, saveDqRule, deleteDqRule } from '@/api/demo/cmdPoc';
import type { DqRuleRow } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocDqSimulateDialog' });

const rules = ref<DqRuleRow[]>([]);

/** el-table 插槽行类型为 DefaultRow，此处收敛断言，保证模板调用无需类型体操 */
const asRule = (row: unknown): DqRuleRow => row as DqRuleRow;

onMounted(async () => {
  rules.value = await listDqRules();
});

const onSimulate = async (row: DqRuleRow) => {
  await saveDqRule(row);
  ElMessage.success(`模拟完成：${row.ruleName ?? ''} → ${row.result ?? ''}`);
};

const onDelete = async (id: number | undefined) => {
  if (!id) return;
  await deleteDqRule(id);
  rules.value = rules.value.filter(item => item.id !== id);
  ElMessage.success('规则已删除');
};

const onAddRule = () => {
  const newRule: DqRuleRow = {
    ruleCode: `RULE_${Date.now().toString().slice(-6)}`,
    ruleName: '新DQ规则',
    dimension: '有效性',
    role: 'GC Core',
    threshold: '待配置',
    result: 'Warning',
    enabled: true
  };
  rules.value.unshift(newRule);
  ElMessage.info('请在表格中编辑规则名称后点击模拟保存');
};
</script>