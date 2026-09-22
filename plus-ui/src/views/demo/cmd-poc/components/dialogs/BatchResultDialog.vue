<template>
  <div class="poc-dialog-body">
    <!-- 页签：默认落在「上传数据明细」（列表点「查看结果」直达），分流统计与治理为第二页签 -->
    <el-tabs v-model="viewMode" class="br-tabs">
      <!-- ═══════════ 页签一：上传数据明细（原「查看上传数据」弹窗内容，合并于此） ═══════════ -->
      <el-tab-pane label="上传数据明细" name="source">
        <div class="src-meta">
          <span><b>文件</b>{{ srcInfo.fileName || '—' }}</span>
          <span><b>业务上下文</b>{{ [srcInfo.scene, srcInfo.buScope].filter(Boolean).join(' · ') || '—' }}</span>
          <span><b>上传行数</b>{{ srcInfo.totalRows ?? srcTotal }}</span>
          <span><b>模板列</b>{{ columns.length }} 列</span>
        </div>

        <div class="src-tip">
          下面是这份文件被系统读到的<b>逐行原始内容</b>（按模板表头顺序展示，最后一列是该行的分流结果），
          用于核对数据是否按模板填写、上传后是否被正确解析。
        </div>

        <el-table v-loading="srcLoading" border :data="srcTableRows" class="data-table" max-height="420" size="small">
          <el-table-column label="行号" prop="rowNo" width="70" align="center" />
          <el-table-column
            v-for="col in columns"
            :key="col"
            :label="col"
            :prop="col"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column label="分流结果" prop="resultType" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="RESULT_TAG[row.resultType as string] ?? 'info'" size="small">
                {{ row.resultType }}
              </el-tag>
            </template>
          </el-table-column>
          <template #empty>
            <span class="src-empty">该任务没有可展示的上传行</span>
          </template>
        </el-table>

        <div class="src-foot">
          <span class="src-foot-text">共 {{ srcTotal }} 行</span>
          <el-pagination
            v-if="srcTotal > SRC_PAGE_SIZE"
            v-model:current-page="srcPageNum"
            :page-size="SRC_PAGE_SIZE"
            :total="srcTotal"
            layout="prev, pager, next"
            small
            @current-change="loadSourceRows"
          />
        </div>
      </el-tab-pane>

      <!-- ═══════════ 页签二：分流统计与治理（原「批量结果分流」内容） ═══════════ -->
      <el-tab-pane label="分流统计与治理" name="routes">
        <div class="kpi-row">
          <div
            v-for="item in kpis"
            :key="item.label"
            class="kpi clickable"
            :class="{ disabled: item.value === 0 }"
            @click="item.value > 0 && selectType(item.type)"
          >
            <b>{{ item.value }}</b>
            <span>{{ item.label }} · 点击查看明细</span>
          </div>
        </div>

        <el-table border :data="result.routes" class="data-table">
          <el-table-column label="结果" prop="result" min-width="110" />
          <el-table-column label="处理方式" prop="handling" min-width="190" />
          <el-table-column label="责任角色" prop="owner" min-width="140" />
          <el-table-column label="明细" min-width="120" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="selectType(routeTypeOf(row.result))">{{ row.detail }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分流明细下钻（真实行明细：cmd_import_row） -->
        <div class="detail-block">
          <div class="detail-head">
            <span class="detail-title">{{ activeLabel }} 明细（{{ rowTotal }} 条）</span>
            <el-pagination
              v-if="rowTotal > rowPageSize"
              v-model:current-page="rowPageNum"
              :page-size="rowPageSize"
              :total="rowTotal"
              layout="prev, pager, next"
              small
              @current-change="loadRows"
            />
          </div>
          <el-table v-loading="rowLoading" border :data="rows" class="data-table" max-height="320" size="small">
            <el-table-column label="行号" prop="rowNo" width="70" align="center" />
            <el-table-column label="客户名称" prop="legalName" min-width="180" show-overflow-tooltip />
            <el-table-column label="信用代码" prop="creditCode" min-width="170" show-overflow-tooltip />
            <el-table-column label="One ID / 候选" min-width="140">
              <template #default="{ row }">{{ row.oneId || '—' }}</template>
            </el-table-column>
            <el-table-column label="质量分" prop="dqScore" width="80" align="center" />
            <el-table-column label="问题 / 说明" prop="errorSummary" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.errorSummary || '—' }}</template>
            </el-table-column>
            <el-table-column v-if="activeType === 'SUSPECTED'" label="治理操作" width="220" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="onLink(row)">关联已有</el-button>
                <el-button link type="warning" @click="onReturn(row)">退回修复</el-button>
                <el-button link type="danger" @click="onExclude(row)">排除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <span class="empty-hint">该分流暂无行明细</span>
            </template>
          </el-table>
          <div v-if="activeType === 'NEW'" class="impact">
            New 行已随任务提交「批量导入确认」审批（工作流场景 IMPORT_BATCH）；审批通过后自动生成 One ID 并激活主档。
          </div>
          <div v-else-if="activeType === 'INVALID'" class="impact">
            Invalid 行按错误策略退回修复（Fix）；修复后可在「新建导入任务」重新上传。
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getBatchResult, importRowAction, listImportJobRows, listTemplateMappings } from '@/api/demo/cmdPoc';
import type { BatchResultVO, ImportRowVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocBatchResultDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const { closeDialog } = useCmdPoc();

/** 默认展示「上传数据明细」——列表点「查看结果」直达原始行核对页 */
const viewMode = ref<'source' | 'routes'>('source');

/** 四类分流的标签配色（Exact 关联 / Suspected 待治理 / New 待审批 / Invalid 退回修复） */
const RESULT_TAG: Record<string, 'success' | 'warning' | 'primary' | 'danger'> = {
  EXACT: 'success',
  SUSPECTED: 'warning',
  NEW: 'primary',
  INVALID: 'danger'
};

const result = ref<BatchResultVO>({
  jobId: '-',
  exact: 0,
  suspected: 0,
  created: 0,
  review: 0,
  invalid: 0,
  routes: []
});

const rowLoading = ref(false);
const rows = ref<ImportRowVO[]>([]);
const rowTotal = ref(0);
const rowPageNum = ref(1);
const rowPageSize = 20;
const activeType = ref<'EXACT' | 'SUSPECTED' | 'NEW' | 'INVALID'>('SUSPECTED');

const jobCode = () => (props.payload?.jobId as string) ?? 'IMP-001';

/* ─────────────── 上传数据明细（页签一） ─────────────── */

const srcLoading = ref(false);
const srcRows = ref<ImportRowVO[]>([]);
const srcTotal = ref(0);
const srcPageNum = ref(1);
const SRC_PAGE_SIZE = 20;
/** 模板表头顺序 = 展示列顺序（取字段映射的 column_name，与下载的模板一致） */
const columns = ref<string[]>([]);

const srcInfo = computed(() => ({
  fileName: (props.payload?.fileName as string) ?? '',
  scene: (props.payload?.scene as string) ?? '',
  buScope: (props.payload?.buScope as string) ?? '',
  totalRows: props.payload?.totalRows as number | undefined
}));

/** rawJson 的键就是 Excel 列名（后端按列名写入），解析失败按空对象处理，不打断展示 */
const parseRaw = (raw?: string): Record<string, unknown> => {
  if (!raw) {
    return {};
  }
  try {
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return {};
  }
};

/** 行数据摊平成「列名 → 单元格文本」，空值统一显示 — */
const srcTableRows = computed(() =>
  srcRows.value.map(row => {
    const raw = parseRaw(row.rawJson);
    const cells: Record<string, string | number> = {
      rowNo: row.rowNo ?? 0,
      resultType: row.resultType ?? '—'
    };
    for (const col of columns.value) {
      const value = raw[col];
      cells[col] = value === undefined || value === null || value === '' ? '—' : String(value);
    }
    return cells;
  })
);

const loadSourceRows = async () => {
  srcLoading.value = true;
  try {
    const page = await listImportJobRows(jobCode(), '', srcPageNum.value, SRC_PAGE_SIZE);
    srcRows.value = page.rows;
    srcTotal.value = page.total;
    if (!columns.value.length) {
      // 映射拿不到列名时，回退用第一条 rawJson 的键（保证弹窗仍能展示上传内容）
      const first = page.rows[0];
      columns.value = Object.keys(parseRaw(first?.rawJson));
    }
  } finally {
    srcLoading.value = false;
  }
};

/** 列顺序取模板字段映射（后端按 order_num 排序），与用户下载的模板表头一致 */
const loadSourceColumns = async () => {
  const templateCode = props.payload?.templateCode as string | undefined;
  if (!templateCode) {
    return;
  }
  const mappings = await listTemplateMappings(templateCode);
  const names = mappings.map(item => item.sourceColumn).filter(Boolean);
  if (names.length) {
    columns.value = names;
  }
};

/* ─────────────── 分流统计与治理（页签二） ─────────────── */

/** 原型 KPI：Exact / Suspected / New / Review / Invalid（四类分流 + 待复核） */
const kpis = computed(() => [
  { label: 'Exact', value: result.value.exact, type: 'EXACT' as const },
  { label: 'Suspected', value: result.value.suspected, type: 'SUSPECTED' as const },
  { label: 'New', value: result.value.created, type: 'NEW' as const },
  { label: 'Review', value: result.value.review, type: 'SUSPECTED' as const },
  { label: 'Invalid', value: result.value.invalid, type: 'INVALID' as const }
]);

const activeLabel = computed(
  () => ({ EXACT: 'Exact', SUSPECTED: 'Suspected', NEW: 'New', INVALID: 'Invalid' })[activeType.value] ?? activeType.value
);

const routeTypeOf = (result: string) => {
  const upper = (result ?? '').toUpperCase();
  if (upper === 'NEW' || upper === 'CREATED') {
    return 'NEW' as const;
  }
  if (upper === 'INVALID') {
    return 'INVALID' as const;
  }
  if (upper === 'REVIEW') {
    return 'SUSPECTED' as const;
  }
  return 'EXACT' as const;
};

const loadResult = async () => {
  result.value = await getBatchResult(jobCode());
};

/** 点击 KPI 或 明细：加载该分流的真实行明细 */
const selectType = async (type: 'EXACT' | 'SUSPECTED' | 'NEW' | 'INVALID') => {
  activeType.value = type;
  rowPageNum.value = 1;
  await loadRows();
};

const loadRows = async () => {
  rowLoading.value = true;
  try {
    const page = await listImportJobRows(jobCode(), activeType.value, rowPageNum.value, rowPageSize);
    rows.value = page.rows;
    rowTotal.value = page.total;
  } finally {
    rowLoading.value = false;
  }
};

/** BU Scope 治理：批量关联、排除或退回修复（设计场景二泳道第 5 阶段） */
const onLink = async (row: ImportRowVO) => {
  const { value } = await ElMessageBox.prompt('输入要关联的已有 One ID', '关联已有 One ID', {
    inputValue: row.oneId ?? '',
    inputPattern: /^GC-[0-9A-Z]{6,}$/,
    inputErrorMessage: 'One ID 格式形如 GC-000128',
    confirmButtonText: '确认关联',
    cancelButtonText: '取消'
  });
  const note = await importRowAction(row.id!, 'LINK', value);
  ElMessage.success(note);
  await refresh();
};

const onReturn = async (row: ImportRowVO) => {
  await ElMessageBox.confirm(`确认将「${row.legalName ?? `第 ${row.rowNo} 行`}」退回修复？`, '退回修复', {
    type: 'warning',
    confirmButtonText: '确认退回',
    cancelButtonText: '取消'
  });
  const note = await importRowAction(row.id!, 'RETURN');
  ElMessage.success(note);
  await refresh();
};

const onExclude = async (row: ImportRowVO) => {
  await ElMessageBox.confirm(`确认排除「${row.legalName ?? `第 ${row.rowNo} 行`}」？排除后不纳入主档。`, '排除', {
    type: 'warning',
    confirmButtonText: '确认排除',
    cancelButtonText: '取消'
  });
  const note = await importRowAction(row.id!, 'EXCLUDE');
  ElMessage.success(note);
  await refresh();
};

/** 治理动作后刷新统计、行明细与上传数据页签的分流标记 */
const refresh = async () => {
  await Promise.all([loadResult(), loadRows(), loadSourceRows()]);
};

onMounted(async () => {
  await Promise.all([loadResult(), loadSourceColumns()]);
  await Promise.all([selectType('SUSPECTED'), loadSourceRows()]);
});

const submit = async (): Promise<string> => {
  // 关闭弹窗触发列表页刷新（BatchPanel 监听 batchResult 关闭后重查）
  closeDialog();
  return `导入任务 ${result.value.jobId} 处理完成：Exact 关联已有，Suspected 治理，New 审批后生成 One ID`;
};

defineExpose({ submit });
</script>

<style lang="scss" scoped>
.br-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 10px;
  }
}

.clickable {
  cursor: pointer;

  &:hover {
    border-color: var(--el-color-primary-light-5);
    background: var(--g-card);
  }

  &.disabled {
    cursor: not-allowed;
    opacity: 0.6;

    &:hover {
      border-color: var(--g-divider);
      background: var(--g-content);
    }
  }
}

.detail-block {
  margin-top: 12px;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.detail-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--g-text);
}

.empty-hint {
  font-size: 12px;
  color: var(--g-text2);
}

.impact {
  margin-top: 12px;
  padding: 10px 12px;
  background: #eaf2f8;
  border-radius: 6px;
  font-size: 12px;
  color: var(--g-text2);
}

/* 上传数据明细页（自 BatchSourceDialog 合并） */
.src-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  padding: 8px 12px;
  margin-bottom: 8px;
  background: var(--g-content);
  border: 1px solid var(--g-divider);
  border-radius: 6px;
  font-size: 13px;
  color: var(--g-text);

  b {
    margin-right: 6px;
    font-weight: 600;
    color: var(--g-text2);
  }
}

.src-tip {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--g-text2);
  line-height: 1.6;
}

.src-empty {
  font-size: 12px;
  color: var(--g-text2);
}

.src-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.src-foot-text {
  font-size: 12px;
  color: var(--g-text2);
}
</style>
