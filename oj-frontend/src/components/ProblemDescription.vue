<template>
  <div class="markdown-body" v-html="renderedHtml"></div>
</template>

<script lang="ts">
import { marked } from 'marked'
import markedKatex from 'marked-katex-extension'
import 'katex/dist/katex.min.css'

// 模块级只初始化一次，防止多次进出页面重复挂载插件
marked.use(
  markedKatex({
    throwOnError: false,
    errorColor: '#cc0000'
  })
)
</script>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  content?: string
}>()

const renderedHtml = computed(() => {
  if (!props.content) return '<p style="color: #909399;">暂无题目描述</p>'
  return marked.parse(props.content) as string
})
</script>

<style scoped>
.markdown-body {
  font-size: 15px;
  line-height: 1.75;
  color: #2c3e50;
}
.markdown-body :deep(h3) {
  margin-top: 16px;
  margin-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 6px;
}
.markdown-body :deep(pre) {
  background-color: #f6f8fa;
  border-radius: 6px;
  padding: 12px;
  overflow-x: auto;
  font-family: 'Fira Code', Consolas, monospace;
}
.markdown-body :deep(code) {
  font-family: 'Fira Code', Consolas, monospace;
}
</style>