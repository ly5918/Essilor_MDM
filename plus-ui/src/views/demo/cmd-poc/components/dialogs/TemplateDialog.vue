<template>
  <div class="poc-dialog-body">
    <el-tabs v-model="activeTab">
      <!-- 下载模板：按业务上下文 4 个维度筛选已发布模板（对应原型 Template 下载弹窗） -->
      <el-tab-pane label="下载模板" name="download">
        <el-row :gutter="12" class="filter-row">
          <el-col :span="6">
            <div class="filter-label">Customer Type</div>
            <el-select v-model="filters.customerType" clearable placeholder="全部" style="width: 100%">
              <el-option v-for="item in customerTypeOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-col>
          <el-col :span="6">
            <div class="filter-label">BU</div>
            <el-select v-model="filters.bu" clearable placeholder="全部" style="width: 100%">
              <el-option v-for="item in buOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-col>
          <el-col :span="6">
            <div class="filter-label">Product Line</div>
            <el-select v-model="filters.productLine" clearable placeholder="全部" style="width: 100%">
              <el-option v-for="item in productLineOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-col>
          <el-col :span="6">
            <div class="filter-label">Source System</div>
            <el-select v-model="filters.sourceSystem" clearable placeholder="全部" style="width: 100%">
              <el-option v-for="item in sourceSystemOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-col>
        </el-row>

        <el-table v-loading="loading" border :data="filteredTemplates" class="data-table m-t-12">
          <el-table-column label="模板" prop="name" min-width="180" />
          <el-table-column label="业务上下文" min-width="170">
            <template #default="{ row }">
              <span>{{ contextText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="版本" prop="version" width="90" align="center" />
          <el-table-column label="字段数" prop="fieldCount" width="90" align="center" />
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'Published' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center">
            <template #default="{ row }">
              <el-button link type="primary" :loading="downloading === row.templateCode" @click="onDownload(row)">
                下载
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <span class="empty-hint">当前筛选条件下没有匹配的模板</span>
          </template>
        </el-table>

        <el-alert
          class="m-t-12"
          type="info"
          :closable="false"
          show-icon
          title="模板由平台在数据库中维护（cmd_import_template + 字段映射表）。下载后按表头填写，再回到「新建导入任务」上传。"
        />
      </el-tab-pane>

      <!-- 字段映射：查看模板的 Excel 列 ↔ 主数据字段（只读预览） -->
      <el-tab-pane label="字段映射" name="mapping">
        <el-form label-width="90px">
          <el-form-item label="选择模板">
            <el-select v-model="mappingTemplateCode" style="width: 100%" @change="loadMappings">
              <el-option v-for="item in templates" :key="item.templateCode" :label="item.name" :value="item.templateCode" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-table v-loading="mappingLoading" border :data="mappings" class="data-table">
          <el-table-column label="来源列" prop="sourceColumn" min-width="150" />
          <el-table-column label="目标字段" prop="targetField" min-width="150" />
          <el-table-column label="转换规则" prop="transform" min-width="160" />
          <el-table-column label="错误策略" prop="errorStrategy" width="130" align="center" />
          <template #empty>
            <span class="empty-hint">该模板暂无字段映射</span>
          </template>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { downloadImportTemplate, listImportTemplates, listTemplateMappings } from '@/api/demo/cmdPoc';
import type { ImportTemplateVO, TemplateMappingVO } from '@/api/demo/cmdPoc/types';

defineOptions({ name: 'CmdPocTemplateDialog' });

defineProps<{ payload?: Record<string, unknown> }>();

const activeTab = ref('download');
const loading = ref(false);
const mappingLoading = ref(false);
const downloading = ref('');

const templates = ref<ImportTemplateVO[]>([]);
const mappings = ref<TemplateMappingVO[]>([]);
const mappingTemplateCode = ref('');

/** 4 个维度筛选条件（对应原型下拉） */
const filters = reactive({
  customerType: '',
  bu: '',
  productLine: '',
  sourceSystem: ''
});

/** 下拉选项：取实际模板中出现过的值，保证筛选一定能命中 */
const distinctOf = (key: 'customerType' | 'bu' | 'productLine' | 'sourceSystem') =>
  computed(() =>
    Array.from(new Set(templates.value.map(item => item[key]).filter(Boolean) as string[]))
  );

const customerTypeOptions = distinctOf('customerType');
const buOptions = distinctOf('bu');
const productLineOptions = distinctOf('productLine');
const sourceSystemOptions = distinctOf('sourceSystem');

const filteredTemplates = computed(() =>
  templates.value.filter(
    item =>
      (!filters.customerType || item.customerType === filters.customerType) &&
      (!filters.bu || item.bu === filters.bu) &&
      (!filters.productLine || item.productLine === filters.productLine) &&
      (!filters.sourceSystem || item.sourceSystem === filters.sourceSystem)
  )
);

/**
 * 业务上下文展示：Customer Type · BU · Product Line · Source System
 * 说明：el-table 插槽的 row 类型为 DefaultRow，这里按模板结构取字段。
 */
const contextText = (row: ImportTemplateVO | Record<string, unknown>) => {
  const item = row as ImportTemplateVO;
  return [item.customerType, item.bu, item.productLine, item.sourceSystem].filter(Boolean).join(' · ') || item.context;
};

/** 下载模板：后端按字段映射动态生成仅含表头的 Excel */
const onDownload = async (row: ImportTemplateVO | Record<string, unknown>) => {
  const item = row as ImportTemplateVO;
  downloading.value = item.templateCode;
  try {
    await downloadImportTemplate(item.templateCode, `${item.name}_${item.version}.xlsx`);
    ElMessage.success(`模板已下载：${item.name}_${item.version}.xlsx`);
  } finally {
    downloading.value = '';
  }
};

const loadMappings = async () => {
  if (!mappingTemplateCode.value) {
    mappings.value = [];
    return;
  }
  mappingLoading.value = true;
  try {
    mappings.value = await listTemplateMappings(mappingTemplateCode.value);
  } finally {
    mappingLoading.value = false;
  }
};

const submit = async (): Promise<string> => `已按业务上下文定位 ${filteredTemplates.value.length} 个模板`;

onMounted(async () => {
  loading.value = true;
  try {
    templates.value = await listImportTemplates();
    mappingTemplateCode.value = templates.value[0]?.templateCode ?? '';
    await loadMappings();
  } finally {
    loading.value = false;
  }
});

defineExpose({ submit });
</script>

<style lang="scss" scoped>
.filter-row {
  margin-bottom: 4px;
}

.filter-label {
  font-size: 12px;
  color: var(--g-text2);
  margin-bottom: 6px;
}

.empty-hint {
  font-size: 12px;
  color: var(--g-text2);
}
</style>
