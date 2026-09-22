<template>
  <div class="poc-dialog-body">
    <!-- 平台管理 › 导入Template（总设计 V6.1 第 15 页：模板版本 / 来源列 / 目标字段 / 转换与错误策略）。
         管线全链路由 cmd_import_template_mapping 驱动：这里新增/编辑字段后，
         模板下载表头、上传表头预检、行级 DQ（必填）、行明细 JSON 自动跟上，无需改代码。 -->
    <div class="tpl-toolbar">
      <el-select v-model="currentCode" style="width: 340px" @change="onTemplateChange">
        <el-option v-for="item in templates" :key="item.templateCode" :value="item.templateCode"
          :label="`${item.name} · ${item.version}（${item.status}）`" />
      </el-select>
      <el-tag v-if="currentTemplate" :type="currentTemplate.status === 'Published' ? 'success' : 'warning'" size="small">
        {{ currentTemplate.status }}
      </el-tag>
      <span class="tpl-toolbar-spacer" />
      <el-button plain size="small" icon="Download" :loading="downloading === currentCode" @click="onDownload">
        下载模板
      </el-button>
      <el-button v-if="isAdmin" type="primary" size="small" icon="Plus" @click="onAdd">新增上传字段</el-button>
    </div>

    <el-table v-loading="loading" border :data="mappings" class="data-table" max-height="380">
      <el-table-column label="源列" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.sourceColumn }}</template>
      </el-table-column>
      <el-table-column label="目标字段" prop="targetField" min-width="130" show-overflow-tooltip />
      <el-table-column label="字段名称" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.fieldName || '—' }}</template>
      </el-table-column>
      <el-table-column label="类型" width="80" align="center">
        <template #default="{ row }">{{ row.dataType || 'Text' }}</template>
      </el-table-column>
      <el-table-column label="必填" width="70" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isRequired === 'Y' ? 'danger' : 'info'" size="small">
            {{ row.isRequired === 'Y' ? '必填' : '选填' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="错误策略" width="110" align="center">
        <template #default="{ row }">
          <span class="tpl-strategy">{{ row.errorStrategy || (row.isRequired === 'Y' ? 'Reject Row' : 'Warning Row') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="转换规则" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.transform || '—' }}</template>
      </el-table-column>
      <el-table-column v-if="isAdmin" label="操作" width="130" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="onEdit(row)">编辑</el-button>
          <el-button
            link
            :type="isCore(row) ? 'info' : 'danger'"
            size="small"
            :disabled="isCore(row)"
            :title="isCore(row) ? '主键字段（去重 / 存量匹配锚点）不允许删除' : '从模板中移除该列'"
            @click="onDelete(row)"
          >删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="empty-hint">暂无字段映射</span>
      </template>
    </el-table>

    <el-alert
      class="m-t-12"
      type="info"
      :closable="false"
      show-icon
      title="可配置化：新增字段即时生效——模板下载自动带新列；上传预检强制全部必填列（选填列缺失按默认值 / 空值放行，存量文件可继续导入）；必填列缺失走行级 DQ（Reject Row）；整行数据落 cmd_import_row（raw/parsed JSON）。客户名称与统一社会信用代码是去重 / 存量匹配的主键字段，受保护不可删除。"
    />

    <!-- 新增 / 编辑字段表单（内层弹窗） -->
    <el-dialog
      v-model="formVisible"
      :title="form.id ? `编辑字段：${form.columnName}` : '新增上传字段'"
      width="520px"
      append-to-body
    >
      <el-form :model="form" label-width="96px">
        <el-form-item label="源列名" required>
          <el-input v-model="form.columnName" placeholder="上传文件的表头名，如：客户邮箱" maxlength="60" />
        </el-form-item>
        <el-form-item label="字段编码" :required="!form.id">
          <el-input v-model="form.fieldCode" :disabled="!!form.id" placeholder="如 customer_email（小写下划线）" maxlength="60" />
        </el-form-item>
        <el-form-item label="字段名称">
          <el-input v-model="form.fieldName" placeholder="如：客户邮箱" maxlength="60" />
        </el-form-item>
        <el-form-item label="数据类型">
          <el-select v-model="form.dataType" style="width: 100%">
            <el-option label="Text 文本" value="Text" />
            <el-option label="Number 数值" value="Number" />
            <el-option label="Date 日期" value="Date" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否必填">
          <el-switch v-model="form.requiredBool" :disabled="isCoreForm" />
          <span v-if="isCoreForm" class="tpl-core-hint">主键字段必须保持必填</span>
        </el-form-item>
        <el-form-item label="默认值">
          <el-input v-model="form.defaultValue" placeholder="上传缺失该列时的兜底值（可空）" maxlength="120" />
        </el-form-item>
        <el-form-item label="转换规则">
          <el-input v-model="form.convertRule" placeholder="如 Trim / Upper / DateFormat(yyyy-MM-dd)（可空）" maxlength="120" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  deleteTemplateMapping,
  downloadImportTemplate,
  listImportTemplates,
  listTemplateMappings,
  saveTemplateMapping
} from '@/api/demo/cmdPoc';
import type { ImportTemplateVO, TemplateMappingSaveForm, TemplateMappingVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocTemplateDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const { roleKey } = useCmdPoc();
/**
 * 管理能力仅 Platform Admin（总设计第 15 页：导入模板是平台管理页）。
 * 其他角色（批量导入页的「下载模板」入口）打开同一弹窗时为只读视图：
 * 仍能看到字段映射与下载——因为下载表头由同一份 mapping 动态生成，
 * Admin 在这里增删字段后，所有角色下载到的模板即时同步。
 */
const isAdmin = computed(() => roleKey.value === 'admin');

const loading = ref(false);
const saving = ref(false);
const downloading = ref('');
const templates = ref<ImportTemplateVO[]>([]);
const mappings = ref<TemplateMappingVO[]>([]);
const currentCode = ref('');

/** 管线主键字段：去重 / 存量匹配 / 发布建主档的锚点，前端与后端双重保护 */
const CORE_FIELDS = ['legal_name', 'credit_code'];

const currentTemplate = computed(() => templates.value.find(item => item.templateCode === currentCode.value));
const isCore = (row: TemplateMappingVO | Record<string, unknown>) =>
  CORE_FIELDS.includes((row as TemplateMappingVO).targetField);

const defaultForm = (): TemplateMappingSaveForm & { requiredBool: boolean } => ({
  id: undefined,
  templateCode: '',
  columnName: '',
  fieldCode: '',
  fieldName: '',
  dataType: 'Text',
  isRequired: 'N',
  defaultValue: '',
  convertRule: '',
  remark: '',
  requiredBool: false
});
const form = ref(defaultForm());
const formVisible = ref(false);
const isCoreForm = computed(() => !!form.value.id && CORE_FIELDS.includes(form.value.fieldCode ?? ''));

const loadMappings = async () => {
  if (!currentCode.value) return;
  loading.value = true;
  try {
    mappings.value = await listTemplateMappings(currentCode.value);
  } finally {
    loading.value = false;
  }
};

const onTemplateChange = () => loadMappings();

/** 下载模板：后端按当前字段映射动态生成仅含表头的 Excel（新增列自动包含） */
const onDownload = async () => {
  const item = currentTemplate.value;
  if (!item) return;
  downloading.value = item.templateCode;
  try {
    await downloadImportTemplate(item.templateCode, `${item.name}_${item.version}.xlsx`);
    ElMessage.success(`模板已下载：${item.name}_${item.version}.xlsx`);
  } finally {
    downloading.value = '';
  }
};

const onAdd = () => {
  form.value = { ...defaultForm(), templateCode: currentCode.value };
  formVisible.value = true;
};

const onEdit = (row: TemplateMappingVO | Record<string, unknown>) => {
  const item = row as TemplateMappingVO;
  form.value = {
    id: item.id,
    templateCode: item.templateCode ?? currentCode.value,
    columnName: item.sourceColumn,
    fieldCode: item.targetField,
    fieldName: item.fieldName ?? '',
    dataType: item.dataType ?? 'Text',
    isRequired: item.isRequired ?? 'N',
    defaultValue: item.defaultValue ?? '',
    convertRule: item.transform ?? '',
    remark: '',
    requiredBool: item.isRequired === 'Y'
  };
  formVisible.value = true;
};

const onSave = async () => {
  if (!form.value.columnName.trim()) {
    ElMessage.warning('源列名不能为空');
    return;
  }
  if (!form.value.id && !form.value.fieldCode?.trim()) {
    ElMessage.warning('新增字段必须填写字段编码');
    return;
  }
  form.value.isRequired = form.value.requiredBool ? 'Y' : 'N';
  saving.value = true;
  try {
    const msg = await saveTemplateMapping(form.value);
    ElMessage.success(msg);
    formVisible.value = false;
    await loadMappings();
  } finally {
    saving.value = false;
  }
};

const onDelete = async (row: TemplateMappingVO | Record<string, unknown>) => {
  const item = row as TemplateMappingVO;
  if (item.id === undefined) return;
  try {
    await ElMessageBox.confirm(
      `确定从模板中移除「${item.sourceColumn}」？已上传的历史批次数据不受影响。`,
      '删除字段',
      { type: 'warning' }
    );
  } catch {
    return;
  }
  const msg = await deleteTemplateMapping(item.id);
  ElMessage.success(msg);
  await loadMappings();
};

const submit = async (): Promise<string> =>
  `模板 ${templates.value.length} 个；当前模板 ${mappings.value.length} 个字段（配置即时生效）`;

onMounted(async () => {
  loading.value = true;
  try {
    templates.value = await listImportTemplates();
    const published = templates.value.find(item => item.status === 'Published') ?? templates.value[0];
    if (published) {
      currentCode.value = published.templateCode;
      await loadMappings();
    }
  } finally {
    loading.value = false;
  }
});

defineExpose({ submit });
</script>

<style lang="scss" scoped>
.tpl-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;

  &-spacer {
    flex: 1;
  }
}

.tpl-strategy {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.tpl-core-hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-color-warning);
}

.empty-hint {
  font-size: 12px;
  color: var(--g-text2);
}
</style>
