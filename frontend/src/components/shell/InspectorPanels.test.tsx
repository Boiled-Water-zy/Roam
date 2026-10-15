// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { I18nProvider } from '../../i18n'
import { api } from '../../api'
import { InspectorPanels, worktreeAt } from './InspectorPanels'

vi.mock('../../api', () => ({ api: vi.fn() }))
vi.mock('../files/FileBrowser', () => ({ default: ({ dir }: { dir: string }) => <div data-testid="files-dir">{dir}</div> }))
vi.mock('../git/GitPanel', () => ({ default: ({ dir }: { dir: string }) => <div data-testid="git-dir">{dir}</div> }))
vi.mock('../git/WorktreePanel', () => ({ default: ({ initialDir }: { initialDir: string }) => <div data-testid="worktree-dir">{initialDir}</div> }))
vi.mock('./AdaptivePanel', () => ({ default: ({ children }: { children: React.ReactNode }) => <div>{children}</div> }))

beforeEach(() => {
  localStorage.setItem('ttmux-locale', 'zh-CN')
  vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({ matches: false, addListener: vi.fn(), removeListener: vi.fn() }))
  vi.mocked(api).mockResolvedValue({ data: [
    { path: '/repo', branch: 'main', isMain: true, prunable: false },
    { path: '/repo/.worktrees/task-a', branch: 'task/a', isMain: false, prunable: false },
    { path: '/repo/.worktrees/task-b', branch: 'task/b', isMain: false, prunable: false },
  ] } as never)
})
afterEach(() => { cleanup(); vi.clearAllMocks(); vi.unstubAllGlobals() })

it('matches the deepest worktree containing a task directory', () => {
  const choices = [
    { path: '/repo', branch: 'main', isMain: true, prunable: false },
    { path: '/repo/.worktrees/task-a', branch: 'task/a', isMain: false, prunable: false },
  ]
  expect(worktreeAt('/repo/.worktrees/task-a/src', choices)?.branch).toBe('task/a')
  expect(worktreeAt('/repo-other', choices)).toBeUndefined()
})

it('changes file, Git and worktree panels to the selected branch directory', async () => {
  render(<I18nProvider><InspectorPanels open panel="files" onPanel={vi.fn()} dir="/repo/.worktrees/task-a" scope="Repo · Task A" onClose={vi.fn()} /></I18nProvider>)
  await waitFor(() => expect(screen.getByTestId('files-dir').textContent).toBe('/repo/.worktrees/task-a'))
  fireEvent.mouseDown(screen.getByRole('combobox', { name: '查看分支工作树' }))
  fireEvent.click(await screen.findByText('task/b'))
  expect(screen.getByTestId('files-dir').textContent).toBe('/repo/.worktrees/task-b')
  expect(screen.getByTestId('git-dir').textContent).toBe('/repo/.worktrees/task-b')
  expect(screen.getByTestId('worktree-dir').textContent).toBe('/repo/.worktrees/task-b')
})
