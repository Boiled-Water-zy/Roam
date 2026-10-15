import { afterEach, expect, it, vi } from 'vitest'
import { openCurrentFileFind, registerFileFind } from './file-find'

const cleanups: (() => void)[] = []
afterEach(() => { cleanups.splice(0).forEach((cleanup) => cleanup()) })

it('opens only the focused file and falls back after it closes', () => {
  const background = vi.fn()
  const focused = vi.fn()
  cleanups.push(registerFileFind(background, 0))
  const closeFocused = registerFileFind(focused, 1)
  cleanups.push(closeFocused)

  expect(openCurrentFileFind()).toBe(true)
  expect(focused).toHaveBeenCalledOnce()
  expect(background).not.toHaveBeenCalled()

  closeFocused()
  expect(openCurrentFileFind()).toBe(true)
  expect(background).toHaveBeenCalledOnce()
})

it('leaves browser find available when no file is open', () => {
  expect(openCurrentFileFind()).toBe(false)
})
