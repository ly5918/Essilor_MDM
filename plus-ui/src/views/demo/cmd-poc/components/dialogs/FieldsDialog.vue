<template>
  <div class="poc-dialog-body">
    <el-tabs v-model="activeTab">
      <!-- 字段目录 -->
      <el-tab-pane label="字段目录" name="fields">
        <div class="d-toolbar">
          <el-button type="primary" plain size="small" icon="Plus" @click="onNewField">新建字段</el-button>
          <el-button size="small" @click="onNewValueSet">新建值集</el-button>
          <el-tag type="success" size="small" effect="plain">发布后动态进入Business User表单</el-tag>
        </div>
        <el-table border :data="metadataFields" class="data-table" max-height="360">
          <el-table-column label="字段编码" prop="code" min-width="150" />
          <el-table-column label="显示名称" prop="label" min-width="160" />
          <el-table-column label="层级" prop="scope" width="140" align="center" />
          <el-table-column label="类型" prop="type" width="110" align="center" />
          <el-table-column label="必填" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.required ? 'danger' : 'info'" size="small">{{ row.required ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="onEditField(row)">编辑</el-button>
          </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 值集 -->
      <el-tab-pane label="值集" name="valueSet">
        <el-table border :data="valueSets" class="data-table" max-height="360">
          <el-table-column label="值集编码" prop="code" min-width="160" />
          <el-table-column label="值集名称" prop="name" min-width="140" />
          <el-table-column label="类型" prop="type" width="100" align="center" />
          <el-table-column label="取值范围" prop="values" min-width="220" />
          <el-table-column label="状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'Published' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 模型版本 -->
      <el-tab-pane label="模型版本" name="version">
        <el-table border :data="versions" class="data-table" max-height="360">
          <el-table-column label="版本" prop="version" min-width="140" />
          <el-table-column label="规则数" prop="ruleCount" width="100" align="center" />
          <el-table-column label="状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'Current' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发布时间" prop="publishedAt" min-width="140" align="center" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-alert
      class="m-t-12"
      type="info"
      :closable="false"
      show-icon
      title="新增字段先保存为Draft。发布模型版本后，新字段按适用实体、BU、Product Line和Source System动态出现在Business User的新建/上传表单中。"
    />

    <!-- 嵌套弹窗：新建 / 编辑字段 -->
    <el-dialog
      v-model="fieldDialogVisible"
      class="poc-dialog"
      :title="editingField ? '编辑元数据字段' : '新建元数据字段'"
      width="760px"
      append-to-body
      destroy-on-close
    >
      <NewFieldDialog ref="fieldFormRef" :field="editingField" @saved="onFieldSaved" />
      <template #footer>
        <el-button @click="fieldDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmitField">保存为Draft</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { listModelVersions, listValueSets } from '@/api/demo/cmdPoc';
import type { MetadataFieldVO, ModelVersionVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import NewFieldDialog from './NewFieldDialog.vue';

defineOptions({ name: 'CmdPocFieldsDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const { metadataFields } = useCmdPoc();

const activeTab = ref('fields');
const valueSets = ref<Awaited<ReturnType<typeof listValueSets>>>([]);
const versions = ref<ModelVersionVO[]>([]);

const fieldDialogVisible = ref(false);
const editingField = ref<MetadataFieldVO | undefined>();
const fieldFormRef = ref<InstanceType<typeof NewFieldDialog>>();
const saving = ref(false);

const onNewField = () => {
  editingField.value = undefined;
  fieldDialogVisible.value = true;
};

const onEditField = (row: unknown) => {
  const field = row as MetadataFieldVO;
  editingField.value = { ...field };
  fieldDialogVisible.value = true;
  ElMessage.info(`已打开字段：${field.label}`);
};

const onNewValueSet = () => ElMessage.info('值集管理已打开');

const onSubmitField = async () => {
  saving.value = true;
  try {
    const message = await fieldFormRef.value?.submit();
    ElMessage.success(message || '字段已保存为Draft');
    fieldDialogVisible.value = false;
  } finally {
    saving.value = false;
  }
};

const onFieldSaved = () => {
  fieldDialogVisible.value = false;
};

onMounted(async () => {
  [valueSets.value, versions.value] = await Promise.all([listValueSets(), listModelVersions()]);
});
</script>
