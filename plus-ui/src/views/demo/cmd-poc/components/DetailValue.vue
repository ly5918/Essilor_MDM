<template>
  <span class="dv">
    <el-tooltip :disabled="!empty || !tip" :content="tip" placement="top" :show-after="150">
      <span v-if="empty" class="dv-empty">{{ EMPTY_TEXT }}</span>
      <span v-else class="dv-val" :class="{ 'dv-mono': mono }">{{ text }}</span>
    </el-tooltip>
  </span>
</template>

<!--
  详情类页面的统一取值单元。

  存在的意义：空值如果和真实数据用同样的颜色渲染，一屏几十个破折号会把页面「糊」成一片，
  真正有价值的数据反而找不到。这里把空值降为淡灰占位（EMPTY_TEXT），
  并挂一个悬浮解释，鼠标停上去才知道「不是没查到，是这一列本来就没填」。

  注意：本项目 vite 配置带 check-transition 插件，模板必须单根节点，
  故外层用 <span class="dv"> 包一层，不要改成并列根节点。
-->
<script setup lang="ts">
import { computed } from 'vue';
import { EMPTY_TEXT, isEmptyValue } from '../constants/options';

defineOptions({ name: 'CmdPocDetailValue' });

const props = withDefaults(
  defineProps<{
    /** 原始值；undefined / null / '' 视为空 */
    value?: unknown;
    /** 编码类字段（One ID / 信用代码 / 税号 / 来源主键）用等宽字体 */
    mono?: boolean;
    /** 空值悬浮提示；传空串则不弹提示 */
    tip?: string;
  }>(),
  { mono: false, tip: '该字段在当前主档中为空，可由 Data Steward 通过「变更与停用」补充' }
);

const empty = computed(() => isEmptyValue(props.value));
const text = computed(() => (empty.value ? '' : String(props.value)));
</script>

<style scoped lang="scss">
.dv {
  display: inline;
}

/* 空值：淡灰 + 降透明度，视觉上主动「退后」，不与真实数据争焦点 */
.dv-empty {
  color: var(--app-text-muted);
  opacity: 0.5;
  letter-spacing: 1px;
  cursor: help;
  user-select: none;
}

.dv-mono {
  font-family: Consolas, Monaco, monospace;
}
</style>
