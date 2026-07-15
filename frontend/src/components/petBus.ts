/**
 * 宠物事件总线
 * ---------------------------------------------------------------
 * 解耦「学习会话」与「桌面宠物」：任意模块 emit 状态机事件名，
 * StudyPet 订阅后驱动精灵图状态机。事件名即 petConfig.json 中
 * stateMachine.transitions 的 key（pomodoro_start / studying /
 * distraction_detected / task_complete / study_stop ...）。
 */
import mitt from 'mitt'

export type PetEvents = Record<string, unknown>

export const petBus = mitt<PetEvents>()

/** 触发一次宠物状态机事件（等价 petBus.emit(event)） */
export function triggerPet(event: string): void {
  petBus.emit(event)
}

export default petBus
