<template>
  <div class="page-container code-practice">
    <!-- 顶部栏 -->
    <div class="cp-top">
      <div class="cp-title">
        <el-select v-model="problemId" size="default" class="prob-select" @change="onProblemChange">
          <el-option v-for="p in problems" :key="p.id" :label="p.title" :value="p.id" />
        </el-select>
        <el-tag :type="diffType" size="small" effect="light">{{ currentProblem.difficulty }}</el-tag>
        <el-tag size="small" effect="plain">函数：{{ currentProblem.functionName }}</el-tag>
      </div>
      <div class="cp-actions">
        <el-select v-model="lang" size="default" class="lang-select" @change="onLangChange">
          <el-option label="JavaScript" value="javascript" />
          <el-option label="Python" value="python" />
          <el-option label="Java" value="java" />
        </el-select>
        <el-button :icon="RotateCcw" @click="resetCode">重置</el-button>
        <el-button type="primary" :icon="Play" :loading="running" @click="runCode">运行</el-button>
      </div>
    </div>

    <div class="cp-body">
      <!-- 左：题目描述 -->
      <section class="cp-problem">
        <div class="cp-problem-head">
          <h2 class="cp-problem-title">{{ currentProblem.title }}</h2>
          <el-tag :type="diffType" size="small" effect="light">{{ currentProblem.difficulty }}</el-tag>
        </div>
        <div class="md-content" v-html="problemHtml"></div>
      </section>

      <!-- 右：编辑器 + 控制台 -->
      <section class="cp-editor-pane">
        <div class="editor-wrap">
          <div class="editor-gutter" ref="gutterRef"><div v-for="n in lineCount" :key="n" class="gutter-line">{{ n }}</div></div>
          <textarea ref="editorRef" v-model="code" class="editor-area" spellcheck="false"
            @input="onEditorInput" @scroll="syncScroll" @keydown.tab.prevent="insertTab"></textarea>
        </div>

        <!-- 控制台 -->
        <div class="console">
          <div class="console-head">
            <span><el-icon><Terminal /></el-icon> 控制台</span>
            <el-button v-if="result" text size="small" @click="clearResult">清除</el-button>
          </div>
          <div class="console-body">
            <div v-if="running" class="run-hint">
              <el-icon class="is-loading"><Loader2 /></el-icon> {{ runHint }}
            </div>
            <div v-else-if="!result" class="console-empty">点击「运行」执行代码，结果将显示在这里。</div>
            <template v-else>
              <div v-if="result.notice" class="notice">
                <el-icon><Info /></el-icon> {{ result.notice }}
              </div>
              <template v-if="result.results && result.results.length">
                <div v-for="(r, i) in result.results" :key="i" class="case" :class="r.pass ? 'ok' : 'fail'">
                  <span class="case-badge">{{ r.pass ? '通过' : '未通过' }}</span>
                  <div class="case-detail">
                    <div><b>输入</b>：{{ fmt(r.input) }}</div>
                    <div><b>期望</b>：{{ fmt(r.expected) }}</div>
                    <div><b>实际</b>：{{ fmt(r.got) }} <span class="case-ms">{{ r.ms }} ms</span></div>
                  </div>
                </div>
                <div class="summary" :class="result.summary.pass ? 'ok' : 'fail'">{{ result.summary.text }}</div>
              </template>
              <div v-if="result.logs && result.logs.length" class="logs">
                <div class="logs-title">输出</div>
                <pre class="logs-pre">{{ result.logs.join('\n') }}</pre>
              </div>
            </template>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { Play, RotateCcw, Loader2, Terminal, Info } from 'lucide-vue-next'
import { renderMarkdown } from '@/utils/markdown'
import { runJavaCode } from '@/api/code'

// ── 题库（内置，可扩展） ──
interface Problem {
  id: string
  title: string
  difficulty: string
  functionName: string
  description: string
  testCases: { input: any[]; expected: any }[]
}
const problems: Problem[] = [
  {
    id: 'two-sum',
    title: '两数之和',
    difficulty: '简单',
    functionName: 'twoSum',
    description: `## 题目描述

给定一个整数数组 \`nums\` 和一个整数目标值 \`target\`，请在数组中找出 **和为目标值** \`target\` 的那 **两个** 整数，并返回它们的数组下标。

你可以假设每种输入只会对应一个答案，且同一个元素不能使用两遍。

### 示例
- 输入：\`nums = [2,7,11,15], target = 9\`
- 输出：\`[0,1]\`（因为 \`nums[0] + nums[1] == 9\`）

### 提示
- \`2 <= nums.length <= 10^4\`
- \`-10^9 <= nums[i] <= 10^9\`
- 返回的下标顺序不限（\`[1,0]\` 与 \`[0,1]\` 均算通过）`,
    testCases: [
      { input: [[2, 7, 11, 15], 9], expected: [0, 1] },
      { input: [[3, 2, 4], 6], expected: [1, 2] },
      { input: [[3, 3], 6], expected: [0, 1] }
    ]
  },
  {
    id: 'reverse-string',
    title: '反转字符串',
    difficulty: '简单',
    functionName: 'reverseString',
    description: `## 题目描述

编写一个函数，接收字符串 \`s\`，返回将其字符顺序 **反转** 后的新字符串。

### 示例
- 输入：\`"hello"\`
- 输出：\`"olleh"\`

### 提示
- \`0 <= s.length <= 10^4\`
- 字符串仅由可打印 ASCII 字符组成`,
    testCases: [
      { input: ['hello'], expected: 'olleh' },
      { input: ['leetcode'], expected: 'edocteel' },
      { input: ['a'], expected: 'a' }
    ]
  }
]

// 每个题目 × 每种语言的起始模板（Java 仅写方法体，后端会包一层 Solution 类）
const PROBLEM_STUBS: Record<string, Record<string, string>> = {
  'two-sum': {
    javascript: `/**
 * @param {number[]} nums
 * @param {number} target
 * @return {number[]}
 */
function twoSum(nums, target) {
  // 在此编写你的代码
}
`,
    python: `def twoSum(nums, target):
    # 在此编写你的代码
    pass
`,
    java: `public int[] twoSum(int[] nums, int target) {
    // 在此编写你的代码
    return new int[]{};
}
`
  },
  'reverse-string': {
    javascript: `/**
 * @param {string} s
 * @return {string}
 */
function reverseString(s) {
  // 在此编写你的代码
}
`,
    python: `def reverseString(s):
    # 在此编写你的代码
    pass
`,
    java: `public String reverseString(String s) {
    // 在此编写你的代码
    return "";
}
`
  }
}

const problemId = ref(problems[0].id)
const currentProblem = computed(() => problems.find(p => p.id === problemId.value) || problems[0])
const problemHtml = computed(() => renderMarkdown(currentProblem.value.description))
const diffType = computed<'success' | 'warning' | 'danger'>(() => {
  const d = currentProblem.value.difficulty
  return d === '简单' ? 'success' : d === '中等' ? 'warning' : 'danger'
})

function stubFor(lang: string): string {
  return PROBLEM_STUBS[problemId.value][lang]
}

// ── 编辑器状态 ──
const lang = ref<'javascript' | 'python' | 'java'>('javascript')
const codeByLang = ref<Record<string, string>>({
  javascript: PROBLEM_STUBS[problemId.value].javascript,
  python: PROBLEM_STUBS[problemId.value].python,
  java: PROBLEM_STUBS[problemId.value].java
})
const code = ref(codeByLang.value[lang.value])
const editorRef = ref<HTMLTextAreaElement>()
const gutterRef = ref<HTMLElement>()
const lineCount = computed(() => code.value.split('\n').length)

function onEditorInput() { codeByLang.value[lang.value] = code.value }
function onLangChange() {
  code.value = codeByLang.value[lang.value] || stubFor(lang.value)
  nextTick(syncScroll)
}
function onProblemChange() {
  // 切换题目时，按当前语言重新写入对应题目+语言的模板
  codeByLang.value = {
    javascript: PROBLEM_STUBS[problemId.value].javascript,
    python: PROBLEM_STUBS[problemId.value].python,
    java: PROBLEM_STUBS[problemId.value].java
  }
  code.value = codeByLang.value[lang.value]
  result.value = null
  nextTick(syncScroll)
}
function resetCode() {
  codeByLang.value[lang.value] = stubFor(lang.value)
  code.value = codeByLang.value[lang.value]
}
function syncScroll() {
  if (gutterRef.value && editorRef.value) gutterRef.value.scrollTop = editorRef.value.scrollTop
}
function insertTab() {
  const ta = editorRef.value
  if (!ta) return
  const start = ta.selectionStart, end = ta.selectionEnd
  const v = code.value
  code.value = v.slice(0, start) + '  ' + v.slice(end)
  codeByLang.value[lang.value] = code.value
  nextTick(() => { ta.selectionStart = ta.selectionEnd = start + 2; ta.focus() })
}

// ── 运行结果 ──
const running = ref(false)
const runHint = ref('')
const result = ref<{
  notice?: string
  results?: { input: any; expected: any; got: any; ms: number; pass: boolean }[]
  logs?: string[]
  summary?: { pass: boolean; text: string }
} | null>(null)

function fmt(v: any) { try { return JSON.stringify(v) } catch { return String(v) } }
function eq(a: any, b: any): boolean {
  if (Array.isArray(a) && Array.isArray(b)) {
    if (a.length !== b.length) return false
    const sa = [...a].sort(), sb = [...b].sort()
    return sa.every((x, i) => x === sb[i])
  }
  return a === b
}

function clearResult() { result.value = null }

async function runCode() {
  if (lang.value === 'java') {
    await runJava()
    return
  }
  result.value = null
  running.value = true
  try {
    if (lang.value === 'javascript') await runJs()
    else await runPython()
  } finally {
    running.value = false
  }
}

// ── Java：提交后端沙箱（独立 JVM 进程隔离 + 超时）执行 ──
async function runJava() {
  result.value = null
  running.value = true
  runHint.value = '正在提交到后端沙箱执行 Java…'
  try {
    const resp = await runJavaCode({
      code: code.value,
      functionName: currentProblem.value.functionName,
      testCases: currentProblem.value.testCases
    })
    if (!resp.success) {
      result.value = { notice: '运行错误：' + (resp.error || '未知错误') }
      return
    }
    const tc = currentProblem.value.testCases
    const results = (resp.results || []).map((r, i) => ({
      input: tc[i]?.input,
      expected: tc[i]?.expected,
      got: r.got,
      ms: r.ms,
      pass: r.pass
    }))
    finish(results, resp.logs || [])
  } catch (e: any) {
    result.value = { notice: '请求失败：' + (e?.message || e) }
  } finally {
    running.value = false
  }
}

// ── JavaScript：Web Worker 隔离执行 ──
async function runJs() {
  const workerSrc = `
self.onmessage = (e) => {
  const { userCode, fnName, testCases } = e.data
  const logs = []
  const orig = console.log
  console.log = (...a) => logs.push(a.map(x => (x && x.stack) ? x.stack : String(x)).join(' '))
  const post = (type, payload) => self.postMessage({ type, payload })
  function safe(v){ try { return JSON.parse(JSON.stringify(v)) } catch { return String(v) } }
  function eq(a,b){
    if (Array.isArray(a)&&Array.isArray(b)){ if(a.length!==b.length) return false; const sa=[...a].sort(),sb=[...b].sort(); return sa.every((x,i)=>x===sb[i]) }
    return a===b
  }
  try {
    const factory = new Function(userCode + ';return ' + fnName + ';')
    const fn = factory()
    if (typeof fn !== 'function') { post('error', { message: fnName + ' 不是一个函数，请确认已实现该函数。' }); return }
    const results = []
    for (const tc of testCases) {
      const start = performance.now()
      const got = fn(...tc.input)
      const ms = Math.round(performance.now() - start)
      results.push({ input: tc.input, expected: tc.expected, got: safe(got), ms, pass: eq(tc.expected, got) })
    }
    post('done', { results, logs })
  } catch (err) {
    post('error', { message: String((err && err.stack) || err) })
  } finally { console.log = orig }
}
`
  runHint.value = '正在执行 JavaScript…'
  await new Promise<void>((resolve) => {
    const worker = new Worker(URL.createObjectURL(new Blob([workerSrc], { type: 'application/javascript' })))
    const timer = setTimeout(() => {
      worker.terminate()
      result.value = { results: [], logs: [], notice: '执行超时（可能存在死循环），已终止。' }
      resolve()
    }, 6000)
    worker.onmessage = (e: MessageEvent) => {
      if (e.data.type === 'done') {
        clearTimeout(timer)
        finish(e.data.payload.results, e.data.payload.logs)
        worker.terminate(); resolve()
      } else if (e.data.type === 'error') {
        clearTimeout(timer)
        result.value = { results: [], logs: [], notice: '运行错误：' + e.data.payload.message }
        worker.terminate(); resolve()
      }
    }
    worker.onerror = (e) => {
      clearTimeout(timer)
      result.value = { results: [], logs: [], notice: '运行错误：' + e.message }
      worker.terminate(); resolve()
    }
    worker.postMessage({ userCode: code.value, fnName: currentProblem.value.functionName, testCases: currentProblem.value.testCases })
  })
}

// ── Python：Pyodide (WASM) 浏览器内执行 ──
let pyodideInstance: any = null
let pyodideLoading: Promise<any> | null = null
async function loadPyodideRuntime(): Promise<any> {
  if (pyodideInstance) return pyodideInstance
  if (!pyodideLoading) {
    pyodideLoading = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = 'https://cdn.jsdelivr.net/pyodide/v0.26.4/full/pyodide.js'
      script.onload = async () => {
        try {
          const py = await (window as any).loadPyodide()
          pyodideInstance = py
          resolve(py)
        } catch (e: any) { reject(new Error('Python 运行时初始化失败：' + e.message)) }
      }
      script.onerror = () => reject(new Error('Pyodide 加载失败，请检查网络（需联网首次加载 Python 运行时）。'))
      document.head.appendChild(script)
    })
  }
  return pyodideLoading
}

async function runPython() {
  runHint.value = '正在加载 Python 运行时…'
  let py: any
  try {
    py = await loadPyodideRuntime()
  } catch (e: any) {
    result.value = { results: [], logs: [], notice: e.message }
    return
  }
  runHint.value = '正在执行 Python…'
  const logs: string[] = []
  try {
    py.setStdout({ batched: (s: string) => logs.push(s) })
    await py.runPythonAsync(code.value)
    const fn = py.globals.get(currentProblem.value.functionName)
    if (typeof fn !== 'function' && !fn) {
      result.value = { results: [], logs, notice: `未找到函数 ${currentProblem.value.functionName}，请确认已实现。` }
      return
    }
    const results: any[] = []
    for (const tc of currentProblem.value.testCases) {
      const start = performance.now()
      const res = fn(...tc.input)
      const got = res && res.toJs ? res.toJs() : res
      if (res && res.destroy) res.destroy()
      const ms = Math.round(performance.now() - start)
      results.push({ input: tc.input, expected: tc.expected, got, ms, pass: eq(tc.expected, got) })
    }
    py.globals.delete(currentProblem.value.functionName)
    finish(results, logs)
  } catch (e: any) {
    result.value = { results: [], logs, notice: '运行错误：' + String(e.message || e) }
  }
}

function finish(results: any[], logs: string[]) {
  const passed = results.filter(r => r.pass).length
  result.value = {
    results,
    logs,
    summary: {
      pass: passed === results.length,
      text: `测试用例 ${passed}/${results.length} 通过` + (passed === results.length ? ' 🎉' : '')
    }
  }
}
</script>

<style scoped>
.code-practice { padding-bottom: var(--space-2xl); }
.cp-top {
  display: flex; align-items: center; justify-content: space-between; gap: var(--space-md);
  flex-wrap: wrap; margin-bottom: var(--space-lg);
}
.cp-title { display: flex; align-items: center; gap: var(--space-sm); flex-wrap: wrap; }
.prob-select { width: 180px; }
.cp-actions { display: flex; align-items: center; gap: var(--space-sm); flex-wrap: wrap; }
.lang-select { width: 140px; }

/* 主体：左右分栏 */
.cp-body { display: grid; grid-template-columns: 1fr 1.1fr; gap: var(--space-lg); align-items: stretch; }
@media (max-width: 900px) { .cp-body { grid-template-columns: 1fr; } }

/* 左：题目描述 */
.cp-problem {
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg);
  padding: var(--space-xl); overflow-y: auto; max-height: 72vh;
}
.cp-problem-head { display: flex; align-items: center; gap: var(--space-md); margin-bottom: var(--space-lg); }
.cp-problem-title { margin: 0; font-size: var(--text-xl); font-weight: 800; color: var(--text-primary); }

/* 右：编辑器 + 控制台 */
.cp-editor-pane { display: flex; flex-direction: column; gap: var(--space-md); min-width: 0; }

/* 编辑器（LeetCode 暗色风格） */
.editor-wrap {
  display: flex; background: #1e1e2e; border: 1px solid #2d2d3f; border-radius: var(--radius-md);
  overflow: hidden; height: 42vh; min-height: 280px;
}
.editor-gutter {
  flex-shrink: 0; width: 44px; padding: 12px 0; background: #181824; color: #5b5b73;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace; font-size: 13px; line-height: 20px;
  text-align: right; overflow: hidden; user-select: none;
}
.gutter-line { padding: 0 10px; }
.editor-area {
  flex: 1; border: none; outline: none; resize: none; background: transparent; color: #e2e8f0;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace; font-size: 13px; line-height: 20px;
  padding: 12px 14px; caret-color: #7dd3fc; tab-size: 2; white-space: pre; overflow: auto;
}
.editor-area::selection { background: #3b4a6b; }

/* 控制台 */
.console { flex: 1; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md); display: flex; flex-direction: column; min-height: 160px; }
.console-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--space-sm) var(--space-md); border-bottom: 1px solid var(--divider);
  font-size: var(--text-sm); font-weight: 600; color: var(--text-primary);
}
.console-head :deep(.el-icon) { vertical-align: -2px; margin-right: 4px; }
.console-body { padding: var(--space-md); overflow-y: auto; flex: 1; }
.console-empty, .run-hint { color: var(--text-muted); font-size: var(--text-sm); display: flex; align-items: center; gap: 6px; padding: var(--space-md) 0; }
.run-hint .is-loading { animation: rotating 1.2s linear infinite; }
@keyframes rotating { from { transform: rotate(0); } to { transform: rotate(360deg); } }

.notice {
  display: flex; align-items: flex-start; gap: 6px; background: var(--primary-50);
  border: 1px solid var(--primary-100); color: var(--text-secondary);
  border-radius: var(--radius-sm); padding: var(--space-md); font-size: var(--text-sm); line-height: 1.6;
}
.notice :deep(.el-icon) { color: var(--primary-600); margin-top: 2px; flex-shrink: 0; }

.case { display: flex; gap: var(--space-md); padding: var(--space-md); border-radius: var(--radius-sm); margin-bottom: var(--space-sm); border: 1px solid var(--border); }
.case.ok { background: var(--success-light); border-color: var(--success); }
.case.fail { background: var(--danger-light); border-color: var(--danger); }
.case-badge { font-size: var(--text-xs); font-weight: 700; padding: 2px 8px; border-radius: var(--radius-sm); flex-shrink: 0; align-self: flex-start; }
.case.ok .case-badge { background: var(--success); color: #fff; }
.case.fail .case-badge { background: var(--danger); color: #fff; }
.case-detail { font-size: var(--text-sm); color: var(--text-primary); line-height: 1.7; }
.case-detail b { color: var(--text-secondary); font-weight: 600; }
.case-ms { color: var(--text-muted); font-size: var(--text-xs); margin-left: 6px; }

.summary { text-align: center; font-weight: 700; font-size: var(--text-sm); padding: var(--space-sm); border-radius: var(--radius-sm); margin-top: var(--space-sm); }
.summary.ok { background: var(--success-light); color: var(--success); }
.summary.fail { background: var(--danger-light); color: var(--danger); }

.logs { margin-top: var(--space-md); }
.logs-title { font-size: var(--text-xs); color: var(--text-muted); margin-bottom: 4px; }
.logs-pre { background: #1e1e2e; color: #e2e8f0; border-radius: var(--radius-sm); padding: var(--space-md); font-size: var(--text-xs); line-height: 1.6; white-space: pre-wrap; overflow-x: auto; font-family: 'SFMono-Regular', Consolas, monospace; }

/* 题目描述 Markdown 渲染 */
.md-content { font-size: var(--text-sm); line-height: 1.8; color: var(--text-primary); word-break: break-word; }
.md-content :deep(h1), .md-content :deep(h2), .md-content :deep(h3) { margin: 14px 0 8px; line-height: 1.4; }
.md-content :deep(h2) { font-size: var(--text-lg); }
.md-content :deep(h3) { font-size: var(--text-md); }
.md-content :deep(p) { margin: 8px 0; }
.md-content :deep(a) { color: var(--primary-600); text-decoration: none; }
.md-content :deep(ul), .md-content :deep(ol) { padding-left: 22px; margin: 8px 0; }
.md-content :deep(li) { margin: 4px 0; }
.md-content :deep(blockquote) { border-left: 3px solid var(--border); padding-left: 12px; color: var(--text-secondary); margin: 8px 0; }
.md-content :deep(code):not(.hljs) { background: var(--surface-3); padding: 2px 6px; border-radius: var(--radius-sm); font-size: 0.9em; }
.md-content :deep(pre) { background: #1e293b; border-radius: var(--radius-md); padding: 14px 16px; overflow-x: auto; margin: 12px 0; }
.md-content :deep(pre code.hljs) { background: none; padding: 0; color: #e2e8f0; font-size: var(--text-sm); line-height: 1.6; }
</style>
