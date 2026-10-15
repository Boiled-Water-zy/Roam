// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { registerFileFind } from '../../files/file-find'
import { GlobalSearch } from './GlobalSearch'

vi.mock('./CommandPalette', () => ({ CommandPalette: () => <div role="dialog">Global search</div> }))

afterEach(cleanup)

it('Ctrl+F opens the active file find instead of the global palette', async () => {
  const openFind = vi.fn()
  const unregister = registerFileFind(openFind, 1)
  try {
    render(<GlobalSearch items={[]} actions={{ openFile: vi.fn(), openRoute: vi.fn(), openSession: vi.fn() }} />)
    const event = new KeyboardEvent('keydown', { key: 'f', code: 'KeyF', ctrlKey: true, bubbles: true, cancelable: true })
    window.dispatchEvent(event)
    await waitFor(() => expect(openFind).toHaveBeenCalledOnce())
    expect(event.defaultPrevented).toBe(true)
    expect(screen.queryByRole('dialog')).toBeNull()
  } finally { unregister() }
})
