<template>
  <el-tooltip :content="statusConfig.tip" placement="top">
    <span class="status-badge" :class="statusConfig.className">
      <el-icon v-if="status === 'SOLVED'" :size="18"><CircleCheckFilled /></el-icon>
      <el-icon v-else-if="status === 'ATTEMPTED'" :size="18"><RemoveFilled /></el-icon>
      <span v-else class="not-started-dot">-</span>
    </span>
  </el-tooltip>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheckFilled, RemoveFilled } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    status?: 'NOT_STARTED' | 'ATTEMPTED' | 'SOLVED' | string
  }>(),
  {
    status: 'NOT_STARTED'
  }
)

const statusConfig = computed(() => {
  switch (props.status) {
    case 'SOLVED':
      return { tip: '已通过 (Accepted)', className: 'is-solved' }
    case 'ATTEMPTED':
      return { tip: '尝试过但尚未通过', className: 'is-attempted' }
    default:
      return { tip: '未开始', className: 'is-none' }
  }
})
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  vertical-align: middle;
}
.is-solved {
  color: #67c23a;
}
.is-attempted {
  color: #e6a23c;
}
.is-none {
  color: #dcdfe6;
  font-weight: bold;
}
</style>