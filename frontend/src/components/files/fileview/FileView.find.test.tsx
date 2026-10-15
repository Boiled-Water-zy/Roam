// @vitest-environment jsdom
import { App as AntApp } from 'antd'
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { api } from '../../../api'
import { I18nProvider } from '../../../i18n'
import { openCurrentFileFind } from '../file-find'
import { FileView } from './FileView'

vi.mock('../../../api', () => ({ api: vi.fn() }))
vi.mock('./CodeView', () => ({ CodeView: ({ findNonce }: { findNonce?: number }) => <div data-testid="code-view" data-find={findNonce || 0} /> }))
vi.mock('./MarkdownView', () => ({ MarkdownView: () => <div data-testid="markdown-preview" /> }))

beforeEach(() => {
  localStorage.setItem('ttmux-locale', 'zh-CN')
  vi.mocked(api).mockResolvedValue({ data: { content: 'search me', binary: false, truncated: false, mtime: 1 } } as never)
})
afterEach(() => { cleanup(); vi.clearAllMocks() })

function show(path: string, tabbed = true, forcePreview = false) {
  return render(<I18nProvider><AntApp>
    <FileView path={path} accent="var(--accent)" inline tabbed={tabbed} forcePreview={forcePreview} onClose={vi.fn()} onOpenPath={vi.fn()} />
  </AntApp></I18nProvider>)
}

it('routes Ctrl+F to the open Python editor', async () => {
  show('/repo/app.py')
  const editor = await screen.findByTestId('code-view')
  expect(editor.getAttribute('data-find')).toBe('0')
  act(() => { expect(openCurrentFileFind()).toBe(true) })
  expect(editor.getAttribute('data-find')).toBe('1')
})

it('opens file-local source search from a rendered Markdown preview', async () => {
  show('/repo/notes.md', true, true)
  await screen.findByTestId('markdown-preview')
  act(() => { expect(openCurrentFileFind()).toBe(true) })
  expect(screen.getByTestId('code-view').getAttribute('data-find')).toBe('1')
  fireEvent.click(screen.getByRole('button', { name: '预览' }))
  expect(screen.getByTestId('markdown-preview')).toBeTruthy()
})
