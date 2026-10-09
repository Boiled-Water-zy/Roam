import { describe, expect, it } from 'vitest'
import { appShellVersion, newer } from './app-update'

describe('app-update', () => {
  it('从 UA 里认出壳的版本', () => {
    expect(appShellVersion('Mozilla/5.0 (Linux; Android 14) Chrome/126 Mobile Safari/537.36 RoamiApp/0.3.0')).toBe('0.3.0')
    expect(appShellVersion('Mozilla/5.0 Chrome/126 RoamiApp/1')).toBe('1')
    expect(appShellVersion('Mozilla/5.0 Chrome/126 Mobile Safari/537.36')).toBeNull()
  })
  it('版本比较按数字不按字符串', () => {
    expect(newer('0.3.0', '0.2.1')).toBe(true)
    expect(newer('0.10.0', '0.9.9')).toBe(true)
    expect(newer('0.3.0', '0.3.0')).toBe(false)
    expect(newer('0.2.1', '0.3.0')).toBe(false)
  })
})
