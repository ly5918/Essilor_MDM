<template>
  <div class="poc-dialog-body">
    <!-- 文件信息：告诉用户"我看的是哪份文件的哪一版数据" -->
    <div class="src-meta">
      <span><b>文件</b>{{ info.fileName || '—' }}</span>
      <span><b>业务上下文</b>{{ [info.scene, info.buScope].filter(Boolean).join(' · ') || '—' }}</span>
      <span><b>上传行数</b>{{ info.totalRows ?? total }}</span>
      <span><b>模板列</b>{{ columns.length }} 列</span>
    </div>

    <div class="src-tip">
      下面是这份文件被系统读到的<b>逐行原始内容</b>（按模板表头顺序展示，最后一列是该行的分流结果），
      用于核对数据是否按模板填写、上传后是否被正确解析。
    </div>

    <el-table v-loading="loading" border :data="tableRows" class="data-table" max-height="420" size="small">
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
      <span class="src-foot-text">共 {{ total }} 行</span>
      <el-pagination
        v-if="total > pageSize"
        v-model:current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        small
        @current-change="loadRows"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { listImportJobRows, listTemplateMappings } from '@/api/demo/cmdPoc';
import type { ImportRowVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';

defineOptions({ name: 'CmdPocBatchSourceDialog' });

const props = defineProps<{ payload?: Record<string, unknown> }>();

const { closeDialog } = useCmdPoc();

/** 四类分流的标签配色（Exact 关联 / Suspected 待治理 / New 待审批 / Invalid 退回修复） */
const RESULT_TAG: Record<string, 'success' | 'warning' | 'primary' | 'danger'> = {
  EXACT: 'success',
  SUSPECTED: 'warning',
  NEW: 'primary',
  INVALID: 'danger'
};

const loading = ref(false);
const rows = ref<ImportRowVO[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = 20;
/** 模板表头顺序 = 展示列顺序（取字段映射的 column_name，与下载的模板一致） */
const columns = ref<string[]>([]);

const info = computed(() => ({
  jobId: (props.payload?.jobId as string) ?? '',
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
const tableRows = computed(() =>
  rows.value.map(row => {
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

const loadRows = async () => {
  loading.value = true;
  try {
    const page = await listImportJobRows(info.value.jobId, '', pageNum.value, pageSize);
    rows.value = page.rows;
    total.value = page.total;
    if (!columns.value.length) {
      // 映射拿不到列名时，回退用第一条 rawJson 的键（保证弹窗仍能展示上传内容）
      const first = page.rows[0];
      columns.value = Object.keys(parseRaw(first?.rawJson));
    }
  } finally {
    loading.value = false;
  }
};

/** 列顺序取模板字段映射（后端按 order_num 排序），与用户下载的模板表头一致 */
const loadColumns = async () => {
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

onMounted(async () => {
  await loadColumns();
  await loadRows();
});

/** 只读查看器：底部只有「关闭」，不产生任何写操作 */
const submit = async (): Promise<string> => {
  closeDialog();
  return `已查看导入任务 ${info.value.jobId} 的上传数据（共 ${total.value} 行）`;
};

defineExpose({ submit });
</script>

<style lang="scss" scoped>
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
