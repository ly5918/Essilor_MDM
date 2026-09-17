<template>
  <section class="page">
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
        <el-input v-model="query.keyword" placeholder="名称、One ID、信用代码" clearable style="width: 280px" @keyup.enter="onSearch" />
        <el-select v-model="query.bu" placeholder="全部BU" clearable style="width: 160px">
          <el-option v-for="bu in BU_OPTIONS" :key="bu" :label="bu" :value="bu" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="Active" value="active" />
          <el-option label="Pending" value="pending" />
        </el-select>
        <el-button type="primary" plain icon="Search" @click="onSearch">查询</el-button>
      </div>
    </el-card>

    <!-- 客户列表 -->
    <el-card class="page-card" shadow="never" :body-style="{ padding: '0' }">
      <template #header><span class="card-title">客户列表</span></template>
      <el-table v-loading="loading" border :data="tableData" class="data-table">
        <el-table-column label="One ID" prop="oneId" width="130" fixed="left">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="onViewDetail(row)">{{ row.oneId }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="客户名称" prop="legalName" min-width="200" show-overflow-tooltip />
        <el-table-column label="BU" prop="bu" min-width="180" />
        <el-table-column label="来源" prop="sourceSystem" min-width="160" />
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="CUSTOMER_STATUS_MAP[row.status].type" size="small">{{ CUSTOMER_STATUS_MAP[row.status].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="onViewDetail(row)">{{ row.status === 'pending' ? '查看申请' : '查看客户' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type { CustomerVO } from '@/api/demo/cmdPoc/types';
import { useCmdPoc } from '../../composables/useCmdPoc';
import { BU_OPTIONS, CUSTOMER_STATUS_MAP } from '../../constants/options';

defineOptions({ name: 'CmdPocCustomersPanel' });

const { readOnly, role, openDialog, customers, loadCustomers } = useCmdPoc();

const loading = ref(false);
const query = ref({ keyword: '', bu: '', status: '' });

const tableData = computed(() =>
  customers.value.filter(row => {
    const keyword = query.value.keyword.trim().toLowerCase();
    const matchKeyword =
      !keyword ||
      row.legalName.toLowerCase().includes(keyword) ||
      row.oneId.toLowerCase().includes(keyword) ||
      row.creditCode.toLowerCase().includes(keyword);
    const matchBu = !query.value.bu || row.bu.includes(query.value.bu);
    const matchStatus = !query.value.status || row.status === query.value.status;
    return matchKeyword && matchBu && matchStatus;
  })
);

/** 查询：本地过滤后打开「客户查询结果」弹窗 */
const onSearch = () => {
  openDialog('search', { rows: tableData.value.map(row => ({ ...row })), keyword: query.value.keyword });
};

/** 行数据类型由 el-table 统一为 DefaultRow，此处收敛断言，保证模板调用无需类型体操 */
const onViewDetail = (row: unknown) => {
  const customer = row as CustomerVO;
  openDialog('search', { rows: [{ ...customer }], keyword: customer.oneId });
};

onMounted(async () => {
  if (!customers.value.length) {
    loading.value = true;
    await loadCustomers();
    loading.value = false;
  }
});
</script>
