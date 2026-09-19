<template>
  <div class="poc-dialog-body">
    <el-form ref="formRef" :model="form" label-width="130px">
      <el-row :gutter="12">
        <el-col :span="24">
          <el-form-item label="导入模板" prop="templateCode">
            <el-select v-model="form.templateCode" style="width: 100%" @change="onTemplateChange">
              <el-option v-for="item in templates" :key="item.templateCode" :label="templateLabel(item)" :value="item.templateCode" />
            </el-select>
            <div v-if="currentTemplate" class="tpl-hint">
              业务上下文：{{ templateContext(currentTemplate) }} · 字段数 {{ currentTemplate.fieldCount }} · 版本
              {{ currentTemplate.version }}
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="上传Excel / CSV" prop="file">
            <el-upload
              drag
              action="#"
              :auto-upload="false"
              :on-change="onFileChange"
              :on-remove="onFileRemove"
              :limit="1"
              accept=".xlsx,.xls,.csv"
              class="upload-zone"
            >
              <el-icon class="upload-ico"><UploadFilled /></el-icon>
              <div class="upload-text">点击或拖拽文件到此处上传</div>
              <div class="upload-hint">请使用「下载模板」得到的表头填写，单个文件不超过 10MB</div>
            </el-upload>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="错误策略" prop="errorStrategy">
            <el-select v-model="form.errorStrategy" style="width: 100%">
              <el-option v-for="item in ERROR_STRATEGY_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="重复策略" prop="duplicateStrategy">
            <el-select v-model="form.duplicateStrategy" style="width: 100%">
              <el-option v-for="item in DUPLICATE_STRATEGY_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="提交后文件落盘并按字段映射解析：文件级写入 cmd_import_job，行级写入 cmd_import_row，随后进入 DQ 与去重分流。"
    />
  </div>
</template>

<script setup lang="ts">
import { UploadFilled } from '@element-plus/icons-vue';
import type { FormInstance, UploadFile } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import { listImportTemplates, uploadImportJob } from '@/api/demo/cmdPoc';
import type { ImportTemplateVO } from '@/api/demo/cmdPoc/types';
import { ERROR_STRATEGY_OPTIONS } from '../../constants/options';

defineOptions({ name: 'CmdPocBatchUploadDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const DUPLICATE_STRATEGY_OPTIONS = ['Exact自动关联，Suspect进入治理', '全部进入人工复核', '拒绝重复行'];

const formRef = ref<FormInstance>();
const templates = ref<ImportTemplateVO[]>([]);
const file = ref<File | null>(null);

const form = reactive({
  templateCode: '',
  errorStrategy: ERROR_STRATEGY_OPTIONS[0],
  duplicateStrategy: DUPLICATE_STRATEGY_OPTIONS[0]
});

const currentTemplate = computed(() => templates.value.find(item => item.templateCode === form.templateCode));

const templateLabel = (item: ImportTemplateVO) => `${item.name} · ${item.version}（${item.status}）`;

const templateContext = (item: ImportTemplateVO) =>
  [item.customerType, item.bu, item.productLine, item.sourceSystem].filter(Boolean).join(' · ') || item.context;

const onTemplateChange = () => {
  // 模板切换仅影响落库时的字段映射，无需额外处理
};

const onFileChange = (uploadFile: UploadFile) => {
  file.value = (uploadFile.raw as File) ?? null;
};

const onFileRemove = () => {
  file.value = null;
};

const submit = async (): Promise<string> => {
  await formRef.value?.validate();
  if (!form.templateCode) {
    throw new Error('请选择导入模板');
  }
  if (!file.value) {
    throw new Error('请选择要上传的文件');
  }
  return uploadImportJob({
    file: file.value,
    templateCode: form.templateCode,
    errorStrategy: form.errorStrategy,
    duplicateStrategy: form.duplicateStrategy
  });
};

onMounted(async () => {
  templates.value = await listImportTemplates();
  // 默认选中第一个已发布模板，没有则取第一个
  const published = templates.value.find(item => item.status === 'Published');
  form.templateCode = published?.templateCode ?? templates.value[0]?.templateCode ?? '';
});

defineExpose({ submit });
</script>

<style lang="scss" scoped>
.upload-zone {
  width: 100%;

  :deep(.el-upload-dragger) {
    padding: 20px;
  }
}

.upload-ico {
  font-size: 28px;
  color: var(--g-text2);
}

.upload-text {
  font-size: 13px;
  color: var(--g-text);
  margin-top: 6px;
}

.upload-hint {
  font-size: 12px;
  color: var(--g-text2);
  margin-top: 4px;
}

.tpl-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--g-text2);
}
</style>
