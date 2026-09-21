<template>
  <section class="page list-page">
    <!-- 筛选条件 / 操作区 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '16px 20px' }">
      <template #header>
        <span class="card-title">筛选条件</span>
      </template>

      <el-alert class="permission-note poc-note" :type="readOnly ? 'info' : 'success'" :closable="false" show-icon>
        <template #title>
          <b>当前数据权限：{{ role.scope }}</b>
          <span class="note-sep">·</span>
          <span>{{ readOnly ? '只读查询，不显示创建、编辑、停用按钮。' : '记录、字段和操作按钮按角色与Scope动态控制。' }}</span>
        </template>
      </el-alert>

      <div class="card-toolbar m-t-12">
        <el-input
          v-model="query.keyword"
          placeholder="名称、One ID、信用代码"
          clearable
          style="width: 260px"
          @keyup.enter="onSearch"
        />
        <el-select v-model="query.bu" placeholder="全部BU" clearable style="width: 150px">
          <el-option v-for="bu in BU_OPTIONS" :key="bu" :label="bu" :value="bu" />
        </el-select>
        <el-select v-model="query.customerType" placeholder="全部客户类型" clearable style="width: 150px">
          <el-option v-for="type in CUSTOMER_TYPE_OPTIONS" :key="type" :label="type" :value="type" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="item in CUSTOMER_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" plain icon="Search" @click="onSearch">查询</el-button>
        <el-button icon="Refresh" @click="onReset">重置</el-button>
        <span class="query-hint">条件变化后会自动查询数据库，无需重复点击</span>
      </div>
    </el-card>

    <!-- 客户列表 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '0' }">
      <template #header>
        <span class="card-title">客户主档列表</span>
        <span class="card-title-note">共 {{ total }} 条 · 当前页 {{ rows.length }} 条</span>
      </template>

      <!-- 指标概览：后端按同一筛选条件实时统计，与下方列表口径一致 -->
      <div class="cust-kpis">
        <div v-for="item in kpis" :key="item.label" class="cust-kpi">
          <b>{{ item.value }}</b>
          <span>{{ item.label }}</span>
        </div>
      </div>

      <!-- 列宽合计≈950px，可在 1280 宽窗口下完整放下（1280 视口内容区约 975px），因此不出现横向滚动条 -->
      <el-table
        ref="tableRef"
        v-loading="loading"
        border
        :data="rows"
        :height="tableHeight"
        class="data-table cust-table"
        :row-class-name="rowClass"
      >
        <el-table-column label="One ID" prop="oneId" width="108" fixed="left">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="onViewDetail(row)">{{ row.oneId }}</el-link>
          </template>
        </el-table-column>

        <el-table-column label="客户名称" min-width="176" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="cust-name-cell">
              <span class="cust-name-cn"><DetailValue :value="row.legalName" tip="" /></span>
              <span v-if="row.legalNameEn || row.shortName" class="cust-name-sub">
                {{ row.legalNameEn || row.shortName }}
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="层级归属" width="80" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="hierarchyOf(row).mounted"
              size="small"
              :type="levelTagType(hierarchyOf(row).level)"
              effect="plain"
            >
              {{ hierarchyOf(row).level }}
            </el-tag>
            <el-tooltip
              v-else-if="hierarchyOf(row).isMaster"
              content="已批准成为主数据，但尚未归位到 A3-A2-A1 层级树；请到「客户层级 → 待归位主数据」归位"
              placement="top"
            >
              <el-tag size="small" type="warning" effect="plain">待归位</el-tag>
            </el-tooltip>
            <DetailValue v-else value="" tip="" />
          </template>
        </el-table-column>

        <el-table-column label="所属 BU" min-width="92">
          <template #default="{ row }">
            <DetailValue :value="row.bu" tip="" />
            <el-tooltip v-if="row.gcScopeFlag === 'Y'" content="跨 BU，全局可见" placement="top">
              <el-tag size="small" type="danger" effect="plain" class="m-l-8">跨BU</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column label="统一社会信用代码" min-width="138">
          <template #default="{ row }">
            <DetailValue :value="row.creditCode" mono tip="" />
          </template>
        </el-table-column>

        <el-table-column label="来源" min-width="80">
          <template #default="{ row }">
            <DetailValue :value="row.sourceSystem" tip="" />
          </template>
        </el-table-column>

        <el-table-column label="状态" width="84" align="center">
          <template #default="{ row }">
            <el-tag :type="customerStatusMeta(row.status).type" size="small">{{ customerStatusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="最近更新" min-width="88" align="center">
          <template #default="{ row }">
            <!-- 单元格只留日期（列窄），完整时间戳放悬浮提示 -->
            <el-tooltip :content="fmtDateTime(row.updatedAt)" :disabled="!row.updatedAt" placement="top">
              <span class="cust-mono">{{ fmtDate(row.updatedAt) || '—' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>

        <!-- 操作列只留「查看客户」：One ID 生命周期历史已并入详情弹窗的页签，不再单开按钮 -->
        <el-table-column label="操作" width="88" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="onViewDetail(row)">查看客户</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="cust-pager">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
        />
      </div>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { getCustomerStats, listCustomers } from '@/api/demo/cmdPoc';
import type { CustomerQuery, CustomerStats, CustomerVO } from '@/api/demo/cmdPoc/types';
import DetailValue from '../DetailValue.vue';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { useListTableHeight } from '../../composables/useListTableHeight';
import {
  BU_OPTIONS,
  CUSTOMER_STATUS_OPTIONS,
  CUSTOMER_TYPE_OPTIONS,
  customerStatusMeta
} from '../../constants/options';

defineOptions({ name: 'CmdPocCustomersPanel' });

const { readOnly, role, openDialog, hierarchyIndex, loadHierarchyIndex, badgeVersion } = useCmdPoc();

/** 表格高度自适应：分页条固定在内容区底部，不随数据条数浮动 */
const { tableRef, tableHeight, recalc } = useListTableHeight(70);

const loading = ref(false);
const query = ref<{ keyword: string; bu: string; customerType: string; status: CustomerQuery['status'] }>({
  keyword: '',
  bu: '',
  customerType: '',
  status: undefined
});
const page = ref({ current: 1, size: 10 });

/** 当前页列表（服务端分页，每次查询都实时读库） */
const rows = ref<CustomerVO[]>([]);
/** 当前筛选条件命中的总条数（用于分页器） */
const total = ref(0);
/** 指标概览（后端按同一筛选条件统计） */
const stats = ref<CustomerStats>({
  total: 0,
  activeCount: 0,
  pendingCount: 0,
  crossBuCount: 0,
  duplicateCount: 0,
  avgDqScore: 0
});

/**
 * 客户 → 层级归属（客户列表「层级归属」列）
 * - 已在 A3-A2-A1 树上：显示 A3 / A2 / A1
 * - 已批准成为主数据但尚未归位：显示「待归位」，并引导到「客户层级 → 待归位主数据」
 * - 其余（待审批 / 驳回 / 停用）：不参与层级，显示 —
 */
const hierarchyOf = (row: unknown) => {
  const customer = row as CustomerVO;
  const hit = hierarchyIndex.value.get(customer.oneId);
  if (hit) return { ...hit, isMaster: true };
  return { level: '—', path: '', mounted: false, isMaster: customer.status === 'active' };
};

const levelTagType = (level: string) => (level === 'A3' ? 'primary' : level === 'A2' ? 'warning' : 'success');

/** 单元格只展示日期（列宽有限），完整到分钟的时间戳交给 tooltip */
const fmtDate = (value?: string) => (value ? value.replace('T', ' ').slice(0, 10) : '');
const fmtDateTime = (value?: string) => (value ? value.replace('T', ' ').slice(0, 16) : '');

/** 疑似重复行整行淡红，扫列表时最先看到风险数据 */
const rowClass = ({ row }: { row: CustomerVO }) => (row.duplicateFlag === 'Y' ? 'cust-row-warn' : '');

/** 当前筛选条件（列表与指标带共用，保证口径一致） */
const currentFilters = (): CustomerQuery => ({
  keyword: query.value.keyword,
  bu: query.value.bu,
  customerType: query.value.customerType,
  status: query.value.status || undefined
});

/**
 * 查询：条件 + 分页一起提交后端，列表与指标带并行实时查库。
 * 不再做前端本地过滤，数据以数据库当前值为准（他人在别处改动后刷新即可看到）。
 */
const doQuery = async () => {
  loading.value = true;
  try {
    const filters = currentFilters();
    const [pageResult, statsResult] = await Promise.all([
      listCustomers({ ...filters, pageNum: page.value.current, pageSize: page.value.size }),
      getCustomerStats(filters)
    ]);
    rows.value = pageResult.rows;
    total.value = pageResult.total;
    stats.value = statsResult;
    // 条件收紧把当前页挤出范围时（例如第 3 页筛完只剩 1 页），自动落到最后一个可用页
    const maxPage = Math.max(1, Math.ceil(pageResult.total / page.value.size));
    if (page.value.current > maxPage) {
      page.value.current = maxPage;
      await doQuery();
    }
  } catch {
    // 查询失败时清空列表，避免残留上一次的结果造成误读
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
    recalc();
  }
};

let queryTimer: ReturnType<typeof setTimeout> | undefined;

/**
 * 合并短时间内的多次条件变化，避免一次交互打出多个请求。
 *
 * @param delayMs   延迟（关键字输入用防抖，下拉/分页立即执行）
 * @param resetPage 是否回到第一页（条件变化必须回第一页，否则可能落在越界页）
 */
const scheduleQuery = (delayMs = 0, resetPage = false) => {
  if (resetPage) page.value.current = 1;
  if (queryTimer) clearTimeout(queryTimer);
  queryTimer = setTimeout(() => void doQuery(), delayMs);
};

/** 关键字输入：防抖 400ms 自动查库（输入即查，无需点按钮） */
watch(
  () => query.value.keyword,
  () => scheduleQuery(400, true)
);
/** 下拉条件：变更即查库 */
watch(() => [query.value.bu, query.value.customerType, query.value.status], () => scheduleQuery(0, true));
/** 分页：翻页 / 改每页条数即查库（不回第一页） */
watch(() => [page.value.current, page.value.size], () => scheduleQuery(0));
/** 新建 / 审批等操作后（badgeVersion 自增）自动刷新，保证看到最新库值 */
watch(badgeVersion, () => scheduleQuery(0));

const kpis = computed(() => [
  { label: '客户总数', value: stats.value.total },
  { label: 'Active', value: stats.value.activeCount },
  { label: '待处理', value: stats.value.pendingCount },
  { label: '跨 BU 全局', value: stats.value.crossBuCount },
  { label: '疑似重复', value: stats.value.duplicateCount },
  { label: '平均质量分', value: stats.value.avgDqScore }
]);

/** 查询按钮：按当前条件立即查库，列表直接刷新（不再弹出结果弹窗） */
const onSearch = () => scheduleQuery(0, true);

const onReset = () => {
  query.value = { keyword: '', bu: '', customerType: '', status: undefined };
  page.value.current = 1;
  scheduleQuery(0, true);
};

/** 行数据类型由 el-table 统一为 DefaultRow，此处收敛断言，保证模板调用无需类型体操 */
const onViewDetail = (row: unknown) => {
  const customer = row as CustomerVO;
  openDialog('customerDetail', { oneId: customer.oneId, row: { ...customer } });
};

onMounted(async () => {
  await doQuery();
  // 层级归属列的数据来源：层级树（已归位）+ 待归位主数据
  await loadHierarchyIndex();
});

onUnmounted(() => {
  if (queryTimer) clearTimeout(queryTimer);
});
</script>

<style scoped lang="scss">
.card-title-note {
  margin-left: 10px;
  font-size: 12px;
  font-weight: 400;
  color: var(--g-text2);
}

.query-hint {
  margin-left: 4px;
  font-size: 12px;
  color: var(--g-text2);
}

.cust-kpis {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  padding: 12px 16px 4px;
}

.cust-kpi {
  flex: 1;
  min-width: 110px;
  background: var(--g-content);
  border: 1px solid var(--g-divider);
  border-radius: 8px;
  padding: 8px 12px;

  b {
    display: block;
    font-size: 18px;
    color: var(--g-text);
    line-height: 1.3;
    margin-bottom: 2px;
  }

  span {
    font-size: 12px;
    color: var(--g-text2);
  }
}

.cust-name-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
  min-width: 0;
}

/* 两行都做省略号，避免窄列下文字被硬切（完整值由列级 tooltip 提供） */
.cust-name-cn,
.cust-name-sub {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cust-name-cn {
  color: var(--g-text);
}

.cust-name-sub {
  font-size: 12px;
  color: var(--g-text2);
}

/* 等宽 / One ID 链接统一不折行，保证行高一致（列窄时靠省略号而不是换行） */
.cust-mono {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.cust-pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
}

/**
 * 紧凑表格：列多且要在一屏内放下，通过收紧内边距与字号换取横向空间。
 * 每列横向内边距从框架默认 24px 收到 16px，9 列合计省下约 72px。
 */
.cust-table {
  :deep(.el-table__cell) {
    padding: 7px 0;
  }

  :deep(.cell) {
    padding: 0 8px;
    font-size: 12.5px;
    line-height: 1.45;
  }

  :deep(.el-table__header .cell) {
    font-weight: 600;
  }

  :deep(.el-tag) {
    height: 20px;
    padding: 0 6px;
    font-size: 11.5px;
    line-height: 18px;
  }

  :deep(.el-button.is-link) {
    padding: 2px 0;
    font-size: 12.5px;
  }

  /* One ID 链接不折行，避免窄列把 ID 拆成两行 */
  :deep(.el-link) {
    font-size: 12.5px;
    white-space: nowrap;
  }
}

:deep(.cust-row-warn) {
  --el-table-tr-bg-color: #fdf3f3;
}
</style>
