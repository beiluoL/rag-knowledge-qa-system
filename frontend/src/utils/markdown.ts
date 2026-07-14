import { Marked, Renderer } from 'marked'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/atom-one-dark.css'

// 隔离的 Markdown 实例：代码块语法高亮 + XSS 消毒
const renderer = new Renderer()
renderer.code = function ({ text, lang }: { text: string; lang?: string }) {
  const langClass = lang ? ` language-${lang}` : ''
  const highlighted = lang && hljs.getLanguage(lang)
    ? hljs.highlight(text, { language: lang }).value
    : hljs.highlightAuto(text).value
  return `<pre><code class="hljs${langClass}">${highlighted}</code></pre>`
}

const markedInstance = new Marked({ breaks: true, gfm: true, renderer })

/** 将 Markdown（含代码块）渲染为安全的 HTML 字符串 */
export function renderMarkdown(text?: string | null): string {
  if (!text) return ''
  const raw = markedInstance.parse(text) as string
  return DOMPurify.sanitize(raw, { USE_PROFILES: { html: true } })
}
