// @vitest-environment jsdom
import { describe, it, expect, beforeEach } from 'vitest'
import { lastTabOf, rememberLastTab } from './task-last-tab'

describe('任务上次看到的标签', () => {
  beforeEach(() => localStorage.clear())
  it('记会话与文件，按任务各记各的', () => {
    rememberLastTab('/w/a', { session: 's1', file: '/w/a/query.py' })
    rememberLastTab('/w/b', { session: 's2' })
    expect(lastTabOf('/w/a')).toEqual({ session: 's1', file: '/w/a/query.py' })
    expect(lastTabOf('/w/b')).toEqual({ session: 's2' })
    expect(lastTabOf('/w/c')).toBeUndefined()
  })
  it('空的不记；坏数据当没记过', () => {
    rememberLastTab('/w/a', {})
    expect(lastTabOf('/w/a')).toBeUndefined()
    localStorage.setItem('roam.taskLastTab', '{bad')
    expect(lastTabOf('/w/a')).toBeUndefined()
  })
  it('超过上限淘汰最久没用的', () => {
    for (let i = 0; i < 100; i++) rememberLastTab(`/t${i}`, { session: `s${i}` })
    rememberLastTab('/t0', { session: 's0' }) // 刚用过
    rememberLastTab('/new', { session: 'n' })
    expect(lastTabOf('/t0')).toBeDefined()
    expect(lastTabOf('/t1')).toBeUndefined()
  })
})
