<template>
  <div class="poc-dialog-body">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="父级" prop="parentId">
        <el-select v-model="form.parentId" placeholder="请选择父级节点" style="width: 100%">
          <el-option v-for="node in nodeOptions" :key="node.id" :label="node.label" :value="node.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="子级" prop="childId">
        <el-select v-model="form.childId" placeholder="请选择子级客户" style="width: 100%">
          <el-option v-for="node in nodeOptions" :key="node.id" :label="node.label" :value="node.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="关系" prop="relation">
        <el-select v-model="form.relation" style="width: 100%">
          <el-option v-for="item in HIER_RELATION_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="生效日期" prop="effectiveDate">
        <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="原因" prop="reason">
        <el-input v-model="form.reason" placeholder="如 新门店归属确认" />
      </el-form-item>
    </el-form>

    <el-alert type="warning" :closable="false" show-icon title="提交前执行直接和间接Loop Check。" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { addHierarchyRelation, getHierarchy } from '@/api/demo/cmdPoc';
import type { HierarchyNodeVO, HierarchyRelationForm } from '@/api/demo/cmdPoc/types';
import { HIER_RELATION_OPTIONS } from '../../constants/options';

defineOptions({ name: 'CmdPocHierarchyAddDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const formRef = ref<FormInstance>();
const treeData = ref<HierarchyNodeVO[]>([]);

/** 层级树拉平为可选项：父子级共用同一份节点字典 */
const nodeOptions = computed(() => {
  const flat: Array<{ id: string; label: string }> = [];
  const walk = (nodes: HierarchyNodeVO[]) => {
    nodes.forEach(node => {
      flat.push({ id: node.id, label: node.label });
      if (node.children?.length) walk(node.children);
    });
  };
  walk(treeData.value);
  return flat;
});

/** 默认值取自原型：A2 · 上海重点客户 → A1 · 上海清视南京西路店 */
const form = reactive<HierarchyRelationForm>({
  parentId: 'A2-0188',
  childId: 'A1-000128',
  relation: HIER_RELATION_OPTIONS[0],
  effectiveDate: '2026-09-15',
  reason: '新门店归属确认'
});

const rules: FormRules<HierarchyRelationForm> = {
  parentId: [{ required: true, message: '请选择父级节点', trigger: 'change' }],
  childId: [{ required: true, message: '请选择子级客户', trigger: 'change' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }]
};

const submit = async (): Promise<string> => {
  await formRef.value?.validate();
  return addHierarchyRelation(form);
};

onMounted(async () => {
  treeData.value = await getHierarchy();
});

defineExpose({ submit });
</script>
