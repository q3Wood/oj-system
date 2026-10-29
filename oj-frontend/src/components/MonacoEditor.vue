<template>
  <div ref="editorContainer" class="monaco-editor-container"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as monaco from 'monaco-editor'

// 1. 利用 Vite 原生 worker 特性导入各语言专用的 Web Worker
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker'
import cssWorker from 'monaco-editor/esm/vs/language/css/css.worker?worker'
import htmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker'
import tsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker'

// 2. 注入 Monaco 全局环境，彻底杜绝 blob 路径解析失败导致的数秒卡顿
self.MonacoEnvironment = {
  getWorker(_: any, label: string) {
    if (label === 'json') {
      return new jsonWorker()
    }
    if (label === 'css' || label === 'scss' || label === 'less') {
      return new cssWorker()
    }
    if (label === 'html' || label === 'handlebars' || label === 'razor') {
      return new htmlWorker()
    }
    if (label === 'typescript' || label === 'javascript') {
      return new tsWorker()
    }
    return new editorWorker()
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
    automaticLayout: true, // 自适应分屏大小
    scrollBeyondLastLine: false,
    tabSize: 4,
    bracketPairColorization: { enabled: true },
    cursorBlinking: 'smooth',
    smoothScrolling: true
  })

  // 监听内容变化同步给外部
  editor.onDidChangeModelContent(() => {
    if (editor) {
      emit('update:modelValue', editor.getValue())
    }
  })
})

// 监听代码外部变更 (如重置模板)
watch(
  () => props.modelValue,
  (newVal) => {
    if (editor && editor.getValue() !== newVal) {
      editor.setValue(newVal)
    }
  }
)

// 监听语言切换
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