import request from './request'

export interface QuizQuestion {
  index: number
  question: string
  options?: string[]
  answer?: string
  explanation?: string
  source?: string
}

export interface QuizResult {
  type: string
  count: number
  questions: QuizQuestion[]
}

/** 生成练习题（选择题 / 问答题） */
export function generateQuiz(data: {
  knowledgeBaseId?: number
  type?: string
  count?: number
}) {
  return request.post<QuizResult>('/quiz/generate', data)
}
