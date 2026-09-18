<template>
  <section class="page">
    <!-- 变更概览：原型 changePage 仅 4 张指标卡（发起属性变更 / 申请逻辑停用按钮在页标题区） -->
    <div class="stat-grid">
      <el-card
        v-for="item in metrics"
        :key="item.label"
        class="stat-card"
        shadow="never"
        :body-style="{ padding: '18px 20px' }"
      >
        <div class="stat-top"></div>
        <div class="stat-label">{{ item.label }}</div>
        <div class="stat-value">{{ item.value }}</div>
        <div class="stat-foot">Demo data</div>
      </el-card>
    </div>
  </section>
</template>

<script setup lang="ts">
defineOptions({ name: 'CmdPocChangePanel' });

/** 原型 changePage 固定指标（与 2.2 原型完全一致） */
const metrics = [
  { label: '待审批变更', value: 4 },
  { label: '待审批停用', value: 2 },
  { label: '本月已生效', value: 11 },
  { label: 'One ID重生成', value: 0 }
];
</script>
