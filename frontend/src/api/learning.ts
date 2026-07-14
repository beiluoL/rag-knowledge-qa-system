import request from './request'

export interface StudyTask {
  id: number
  userId: number
  knowledgeBaseId?: number
  title: string
  mode: string
  cycle?: string
  targetCount: number
  progressCount: number
  status: string
  dueAt?: string
  createdAt?: string
}

export interface Achievement {
  code: string
  name: string
  description?: string
  icon?: string
  metric?: string
  threshold?: number
  unlocked?: boolean
}

export interface StudyCard {
  id: number
  documentId: number
  documentTitle: string
  front: string
  back: string
}

export interface Dashboard {
  xp: number
  level: number
  title: string
  levelProgress: number
  xpToNextLevel: number
  currentStreak: number
  longestStreak: number
  cardsStudied: number
  todayTasks: StudyTask[]
  recentAchievements: Achievement[]
}

export function getDashboard() {
  return request.get<Dashboard>('/learning/dashboard')
}

export function getTasks(cycle?: string) {
  return request.get<StudyTask[]>('/learning/tasks', { params: cycle ? { cycle } : {} })
}

export function generateTasks(cycle = 'daily') {
  return request.post<StudyTask[]>('/learning/tasks/generate', null, { params: { cycle } })
}

export function createTask(data: {
  title: string
  mode: string
  cycle?: string
  knowledgeBaseId?: number
  targetCount?: number
}) {
  return request.post<StudyTask>('/learning/tasks', data)
}

export function completeStudy(data: { cards: number; knowledgeBaseId?: number; mode?: string }) {
  return request.post('/learning/study/complete', data)
}

export function getAchievements() {
  return request.get<Achievement[]>('/learning/achievements')
}

export function getStudyCards(knowledgeBaseId: number, mode = 'flashcard') {
  return request.get<StudyCard[]>('/learning/cards', { params: { knowledgeBaseId, mode } })
}

export interface ReviewDay {
  day: number
  focus: string
  tasks: string[]
  minutes: number
  topics: string[]
}

export interface ReviewPlan {
  knowledgeBase: string
  goal: string
  days: number
  dailyMinutes: number
  docCount: number
  plan: ReviewDay[]
}

/** 生成个性化复习计划 */
export function generateReviewPlan(data: {
  knowledgeBaseId?: number
  goal?: string
  days?: number
  dailyMinutes?: number
}) {
  return request.post<ReviewPlan>('/learning/review-plan', data)
}
