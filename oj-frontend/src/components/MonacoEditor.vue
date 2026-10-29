<template>
  <div ref="editorContainer" class="monaco-editor-container"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as monaco from 'monaco-editor'

// 配置全局 Monaco 环境，使用安全的标准 Inline Worker，兼容 Vite 8 / Rolldown 且不依赖子路径解析
if (typeof self !== 'undefined' && !self.MonacoEnvironment) {
  self.MonacoEnvironment = {
    getWorker() {
      const blob = new Blob([
        `self.onmessage = function () {
          // 极简基础 Worker，满足代码高亮、缩进与括号匹配需求
        };`
      ], { type: 'text/javascript' })
      return new Worker(URL.createObjectURL(blob))
    }
  }
}

interface Props {
  modelValue: string
  language?: string
  theme?: string
  readOnly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  language: 'cpp',
  theme: 'vs-dark',
  readOnly: false
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const editorContainer = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null

onMounted(() => {
  if (!editorContainer.value) return

  editor = monaco.editor.create(editorContainer.value, {
    value: props.modelValue,
    language: props.language,
    theme: props.theme,
    readOnly: props.readOnly,
    fontSize: 14,
    fontFamily: "'Fira Code', Consolas, 'Courier New', monospace",
    lineNumbers: 'on',
    minimap: { enabled: false },
    automaticLayout: true,
    scrollBeyondLastLine: false,
    tabSize: 4,
    bracketPairColorization: { enabled: true },
    cursorBlinking: 'smooth',
    smoothScrolling: true
  })

  editor.onDidChangeModelContent(() => {
    if (editor) {
      emit('update:modelValue', editor.getValue())
    }
  })
})

watch(
  () => props.modelValue,
  (newVal) => {
    if (editor && editor.getValue() !== newVal) {
      editor.setValue(newVal)
    }
  }
)

watch(
  () => props.language,
  (newLang) => {
    if (editor) {
      const model = editor.getModel()
      if (model) {
        monaco.editor.setModelLanguage(model, newLang)
      }
    }
  }
)

onBeforeUnmount(() => {
  if (editor) {
    editor.dispose()
  }
})
</script>

<style scoped>
.monaco-editor-container {
  width: 100%;
  height: 100%;
}
</style>