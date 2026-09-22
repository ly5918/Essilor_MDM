<template>
  <div class="poc-dialog-body">
    <el-tabs v-model="activeTab">
      <!-- 字段目录 -->
      <el-tab-pane label="字段目录" name="fields">
        <div class="d-toolbar">
          <el-button type="primary" plain size="small" icon="Plus" @click="onNewField">新建字段</el-button>
          <el-tag type="success" size="small" effect="plain">发布后动态进入Business User表单</el-tag>
        </div>
        <el-table border :data="metadataFields" class="data-table" max-height="360">
          <el-table-column label="字段编码" prop="code" min-width="150" />
          <el-table-column label="显示名称" prop="label" min-width="160" />
          <el-table-column label="层级" prop="scope" width="140" align="center" />
          <el-table-column label="类型" prop="type" width="110" align="center" />
          <el-table-column label="版本" prop="versionNo" width="110" align="center" />
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

      <!-- 值集（总设计 Demo Topic 12 仅要求字段扩展；值集按第 11 页「字段与值集的角色化业务操作」要求提供维护入口） -->
      <el-tab-pane label="值集" name="valueSet">
        <el-alert
          class="m-b-8"
          type="info"
          :closable="false"
          show-icon
          title="值集由平台统一维护（编码 / 名称 / 取值范围），可在此编辑；字段可引用值集编码渲染下拉选项。"
        />
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
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="onEditValueSet(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 模型版本（总设计：元数据版本化配置；可新建多版本、Draft→发布演进） -->
      <el-tab-pane label="模型版本" name="version">
        <div class="d-toolbar">
          <el-button class="btn-create-version" type="primary" plain size="small" icon="Plus" @click="onCreateVersion">新建版本</el-button>
          <el-tag type="info" size="small" effect="plain">基于当前已发布版本克隆为 Draft，可编辑后发布演进</el-tag>
        </div>
        <el-table border :data="versions" class="data-table" max-height="360">
          <el-table-column label="版本" prop="version" min-width="140" />
          <el-table-column label="规则数" prop="ruleCount" width="100" align="center" />
          <el-table-column label="状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'Current' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发布时间" prop="publishedAt" min-width="160" align="center" />
          <el-table-column label="操作" width="100" align="center">
            <template #default="{ row }">
              <el-button
                v-if="row.status !== 'Current'"
                type="primary"
                link
                @click="onPublishVersion(row)"
              >发布</el-button>
              <el-tag v-else type="success" size="small">当前生效</el-tag>
            </template>
          </el-table-column>
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
      <NewFieldDialog ref="fieldFormRef" :field="editingField" :versions="versions" :default-version="workingVersion" @saved="onFieldSaved" />
      <template #footer>
        <el-button @click="fieldDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmitField">保存为Draft</el-button>
      </template>
    </el-dialog>

    <!-- 嵌套弹窗：编辑值集 -->
    <el-dialog
      v-model="vsDialogVisible"
      class="poc-dialog vs-edit-dialog"
      title="编辑值集"
      width="640px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="vsFormRef" :model="vsForm" :rules="vsRules" label-width="110px">
        <el-form-item label="值集编码" prop="code">
          <el-input v-model="vsForm.code" :disabled="!!editingValueSet" />
        </el-form-item>
        <el-form-item label="值集名称" prop="name">
          <el-input v-model="vsForm.name" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="vsForm.type" style="width: 100%">
            <el-option label="Enum" value="Enum" />
            <el-option label="Reference" value="Reference" />
          </el-select>
        </el-form-item>
        <el-form-item label="取值范围" prop="values">
          <el-select
            v-model="vsValues"
            class="vs-values-select"
            multiple
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            placeholder="选择已有取值，输入关键字回车可直接创建，或点「+ 新增取值」"
            style="width: 100%"
            @change="onValuesChange"
          >
            <el-option v-for="v in valueOptions" :key="v" :label="v" :value="v" />
            <el-option class="vs-add-option" label="+ 新增取值" :value="ADD_SENTINEL" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="vsForm.status" style="width: 100%">
            <el-option label="已发布" value="Published" />
            <el-option label="Draft" value="Draft" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="vsDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="vsSaving" @click="onSubmitValueSet">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import {
  createModelVersion,
  listModelVersions,
  listValueSets,
  publishModelVersion,
  saveValueSet
} from '@/api/demo/cmdPoc';
import type { MetadataFieldVO, ModelVersionVO, ValueSetForm } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import NewFieldDialog from './NewFieldDialog.vue';

defineOptions({ name: 'CmdPocFieldsDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const { metadataFields, upsertMetadataField, loadMetadataFields, publishMetadata } = useCmdPoc();

const activeTab = ref('fields');
const valueSets = ref<Awaited<ReturnType<typeof listValueSets>>>([]);
const versions = ref<ModelVersionVO[]>([]);

const fieldDialogVisible = ref(false);
const editingField = ref<MetadataFieldVO | undefined>();
const fieldFormRef = ref<InstanceType<typeof NewFieldDialog>>();
const saving = ref(false);

const vsDialogVisible = ref(false);
const editingValueSet = ref<ValueSetForm | undefined>();
const vsFormRef = ref<FormInstance>();
const vsSaving = ref(false);
const vsForm = reactive<ValueSetForm>({ code: '', name: '', type: 'Enum', values: '', status: 'Draft' });
const vsRules: FormRules<ValueSetForm> = {
  code: [{ required: true, message: '值集编码不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '值集名称不能为空', trigger: 'blur' }]
};

/* -------- 取值范围：标签式多选（下拉可选 + 点「+ 新增取值」/直接输入回车创建） -------- */
const ADD_SENTINEL = '__ADD_NEW_VALUE__';

/** 多选框的选中项（与 vsForm.values 字符串双向换算，存储仍是 / 分隔字符串） */
const vsValues = ref<string[]>([]);
/** 下拉候选池：编辑时带入的已有取值 + 运行时新增的取值 */
const vsPool = ref<string[]>([]);

const valueOptions = computed(() => Array.from(new Set([...vsPool.value, ...vsValues.value])));

/** 把后端存的字符串拆成数组：支持 / , ，、 ; ；与换行等常见分隔符 */
const parseValues = (raw: string): string[] =>
  (raw || '')
    .split(/[/,，、;；\n]/)
    .map(s => s.trim())
    .filter(Boolean);

/** 勾到哨兵项「+ 新增取值」时弹输入框；取消则只移除哨兵、不改动已选项 */
const onValuesChange = async (arr: string[]) => {
  if (!arr.includes(ADD_SENTINEL)) return;
  vsValues.value = arr.filter(v => v !== ADD_SENTINEL);
  try {
    const { value } = await ElMessageBox.prompt('输入新的取值，可一次输入多个（用 / 或逗号分隔）', '新增取值', {
      confirmButtonText: '添加',
      cancelButtonText: '取消',
      inputPattern: /\S/,
      inputErrorMessage: '取值不能为空'
    });
    const parts = parseValues(value);
    vsPool.value = Array.from(new Set([...vsPool.value, ...parts]));
    vsValues.value = Array.from(new Set([...vsValues.value, ...parts]));
  } catch {
    /* 用户取消，保持现状 */
  }
};

/** 工作版本：优先取最新的 Draft 版本，否则取当前生效（Current）版本；新字段默认归入工作版本 */
const workingVersion = computed(() => {
  const draft = [...versions.value].toReversed().find(v => v.status === 'Draft');
  if (draft) return draft.version;
  return versions.value.find(v => v.status === 'Current')?.version;
});

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

const onEditValueSet = (row: unknown) => {
  const vs = row as ValueSetForm;
  editingValueSet.value = { ...vs };
  Object.assign(vsForm, { code: vs.code, name: vs.name, type: vs.type, values: vs.values, status: vs.status });
  const parts = parseValues(vs.values);
  vsValues.value = [...parts];
  vsPool.value = [...parts];
  vsDialogVisible.value = true;
};

/**
 * DialogHost「确认」= 发布模型版本（真实动作，非模拟）：
 * 调用 publishMetadata 将当前版本全部 Draft 字段转为 Published，
 * 并刷新模型版本台账（新版本 Current、其余退役为 Draft）。
 */
const submit = async () => {
  const message = await publishMetadata();
  versions.value = await listModelVersions();
  // 发布会退役其余版本（字段转 Draft），需重新拉取字段状态，本地乐观更新不准
  await loadMetadataFields();
  return message;
};
defineExpose({ submit });

const onSubmitField = async () => {
  saving.value = true;
  try {
    const message = await fieldFormRef.value?.submit();
    ElMessage.success(message || '字段已保存为Draft');
    await loadMetadataFields();
    fieldDialogVisible.value = false;
  } finally {
    saving.value = false;
  }
};

const onSubmitValueSet = async () => {
  await vsFormRef.value?.validate();
  vsSaving.value = true;
  try {
    // 多选数组序列化回存储格式（/ 分隔字符串）
    vsForm.values = vsValues.value.join(' / ');
    const message = await saveValueSet({ ...vsForm });
    ElMessage.success(message);
    valueSets.value = await listValueSets();
    vsDialogVisible.value = false;
  } finally {
    vsSaving.value = false;
  }
};

const onFieldSaved = () => {
  fieldDialogVisible.value = false;
};

/** 基于当前已发布版本克隆一条新的 Draft 版本 */
const onCreateVersion = async () => {
  try {
    const next = await createModelVersion();
    versions.value = await listModelVersions();
    await loadMetadataFields();
    ElMessage.success(`已创建新版本 ${next}（Draft），可在字段目录中为其新增/调整字段后发布`);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '创建版本失败');
  }
};

/** 发布指定版本：该版本字段转 Published，其余版本退役为 Draft */
const onPublishVersion = async (row: unknown) => {
  const version = (row as ModelVersionVO).version;
  try {
    const message = await publishModelVersion(version);
    versions.value = await listModelVersions();
    await loadMetadataFields();
    ElMessage.success(message);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '发布版本失败');
  }
};

onMounted(async () => {
  [valueSets.value, versions.value] = await Promise.all([listValueSets(), listModelVersions()]);
});
</script>

<style lang="scss" scoped>
/* 「+ 新增取值」哨兵项：主色 + 前缀加号区分普通选项 */
.vs-add-option {
  color: var(--el-color-primary);
  font-weight: 600;
}

/* 多选标签较多时允许换行撑高，不截断 */
.vs-values-select {
  :deep(.el-select__tags) {
    max-width: 100%;
  }
}
</style>
