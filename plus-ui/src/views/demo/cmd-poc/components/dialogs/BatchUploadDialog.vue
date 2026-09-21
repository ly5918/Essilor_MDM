<template>
  <div class="poc-dialog-body">
    <el-form ref="formRef" :model="form" label-width="130px">
      <!-- 业务上下文（设计节点①：选择业务场景、BU、来源系统与模板版本并创建任务） -->
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="业务场景" prop="scene">
            <el-select v-model="form.scene" style="width: 100%" placeholder="请选择业务场景">
              <el-option v-for="item in SCENE_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="BU" prop="buScope">
            <el-select v-model="form.buScope" style="width: 100%" placeholder="请选择 BU">
              <el-option v-for="item in BU_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="来源系统" prop="sourceSystem">
            <el-select v-model="form.sourceSystem" style="width: 100%" placeholder="请选择来源系统">
              <el-option v-for="item in SOURCE_SYSTEM_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="导入模板" prop="templateCode">
            <el-select v-model="form.templateCode" style="width: 100%" @change="onTemplateChange">
              <el-option v-for="item in templates" :key="item.templateCode" :label="templateLabel(item)" :value="item.templateCode" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <div v-if="currentTemplate" class="tpl-hint">
        模板版本：{{ currentTemplate.version }} · 字段数 {{ currentTemplate.fieldCount }} ·
        业务上下文：{{ templateContext(currentTemplate) }}
      </div>

      <!-- 模板表头要求（设计节点②「上传 Excel / CSV」：提交前确认文件与模板匹配） -->
      <div v-if="mappings.length" class="tpl-columns">
        <span class="tpl-columns-label">模板表头（{{ mappings.length }} 列，标 * 为必填）：</span>
        <span v-for="m in mappings" :key="m.sourceColumn" class="col-chip">
          {{ m.sourceColumn }}<i v-if="m.errorStrategy === 'Reject Row'">*</i>
        </span>
      </div>

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
      <!-- 文件详情（设计节点②：查看文件、Sheet 与行数） -->
      <div v-if="file" class="file-info">
        已选择：<b>{{ file.name }}</b>（{{ (file.size / 1024).toFixed(1) }} KB）· 行数与表头在提交后由文件级预检校验
      </div>
    </el-form>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="提交后先做文件级预检（表头缺列 / 无数据行则整批退回），通过后逐行执行 DQ、批次内去重与存量匹配，按 Exact / Suspected / New / Invalid 四类分流；存在 New 行时自动发起「批量导入确认」审批。"
    />
  </div>
</template>

<script setup lang="ts">
import { UploadFilled } from '@element-plus/icons-vue';
import type { FormInstance, UploadFile } from 'element-plus';
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import { listImportTemplates, listTemplateMappings, uploadImportJob } from '@/api/demo/cmdPoc';
import type { ImportTemplateVO, TemplateMappingVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocBatchUploadDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const SCENE_OPTIONS = ['DOOR', 'LEGAL', 'GROUP'];
const BU_OPTIONS = ['High End', 'Mainstream'];
const SOURCE_SYSTEM_OPTIONS = ['DMS+', 'Cloud', 'SAP', 'EXCEL', 'Manual'];

const formRef = ref<FormInstance>();
const templates = ref<ImportTemplateVO[]>([]);
const mappings = ref<TemplateMappingVO[]>([]);
const file = ref<File | null>(null);

const form = reactive({
  scene: 'DOOR',
  buScope: '',
  sourceSystem: '',
  templateCode: ''
});

const currentTemplate = computed(() => templates.value.find(item => item.templateCode === form.templateCode));

const templateLabel = (item: ImportTemplateVO) => `${item.name} · ${item.version}（${item.status}）`;

const templateContext = (item: ImportTemplateVO) =>
  [item.customerType, item.bu, item.productLine, item.sourceSystem].filter(Boolean).join(' · ') || item.context;

/** 模板切换：业务上下文自动回落模板定义，仍可手工调整；同时拉取表头要求明细 */
const onTemplateChange = async () => {
  const tpl = currentTemplate.value;
  if (!tpl) {
    mappings.value = [];
    return;
  }
  if (tpl.bu) form.buScope = tpl.bu;
  if (tpl.sourceSystem) form.sourceSystem = tpl.sourceSystem;
  try {
    mappings.value = await listTemplateMappings(tpl.templateCode);
  } catch {
    mappings.value = [];
  }
};

const onFileChange = (uploadFile: UploadFile) => {
  file.value = (uploadFile.raw as File) ?? null;
};

const onFileRemove = () => {
  file.value = null;
};

const submit = async (): Promise<string> => {
  await formRef.value?.validate();
  if (!form.scene) {
    throw new Error('请选择业务场景');
  }
  if (!form.buScope) {
    throw new Error('请选择 BU');
  }
  if (!form.templateCode) {
    throw new Error('请选择导入模板');
  }
  if (!file.value) {
    throw new Error('请选择要上传的文件');
  }
  const jobCode = await uploadImportJob({
    file: file.value,
    templateCode: form.templateCode,
    // 错误/重复策略按总设计固定规则执行（不属「新建导入任务」节点的用户输入项）
    errorStrategy: 'Reject Row',
    duplicateStrategy: 'Exact自动关联，Suspect进入治理',
    scene: form.scene,
    buScope: form.buScope,
    sourceSystem: form.sourceSystem
  });
  ElMessage.success(`任务 ${jobCode} 已创建，DQ 与去重分流完成`);
  return `导入任务 ${jobCode} 已创建：文件预检通过，行级 DQ 与四类分流完成，New 行已提交批量导入确认审批`;
};

onMounted(async () => {
  templates.value = await listImportTemplates();
  // 默认选中第一个已发布模板，没有则取第一个；业务上下文随之回落模板定义
  const published = templates.value.find(item => item.status === 'Published') ?? templates.value[0];
  if (published) {
    form.templateCode = published.templateCode;
    onTemplateChange();
  }
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
  margin: 0 0 8px 130px;
  font-size: 12px;
  color: var(--g-text2);
}

/* 模板表头要求（必填列标 *） */
.tpl-columns {
  margin: 0 0 14px 130px;
  font-size: 12px;
  color: var(--g-text2);
  line-height: 2;
}

.tpl-columns-label {
  margin-right: 6px;
}

.col-chip {
  display: inline-block;
  margin: 0 6px 6px 0;
  padding: 1px 8px;
  border-radius: 10px;
  background: #eef4f9;
  border: 1px solid #d3e2ee;
  color: #2c4b63;
  font-family: Consolas, Monaco, monospace;

  i {
    color: #b4392f;
    font-style: normal;
    margin-left: 2px;
  }
}

/* 文件详情 */
.file-info {
  margin: -6px 0 12px 130px;
  font-size: 12px;
  color: var(--g-text2);

  b {
    color: var(--g-text);
  }
}
</style>
