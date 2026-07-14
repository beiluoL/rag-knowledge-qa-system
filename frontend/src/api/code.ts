import request from './request'

export interface CodeRunRequest {
  code: string
  functionName: string
  testCases: { input: any[]; expected: any }[]
}

export interface CodeRunResult {
  success: boolean
  error?: string | null
  results?: { ms: number; pass: boolean; got: any }[]
  logs?: string[]
}

/** 提交用户 Java 代码到后端沙箱执行（进程隔离 + 超时） */
export function runJavaCode(data: CodeRunRequest) {
  return request.post<CodeRunResult>('/code/run', data)
}
