// 每个任务「上次看到哪个标签」：会话名 + 当时开着的文件。回到这个任务时先回到它，
// 而不是一律落到会话的对话界面——在任务里看着 query.py，切走再从树里点回来，还该是 query.py。
const KEY = 'roam.taskLastTab'
const MAX = 100

export type LastTab = { session?: string; file?: string }

function readAll(): Record<string, LastTab> {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) || '{}')
    return v && typeof v === 'object' && !Array.isArray(v) ? v : {}
  } catch { return {} }
}

export function lastTabOf(task: string): LastTab | undefined {
  const v = readAll()[task]
  return v && typeof v === 'object' ? v : undefined
}

export function rememberLastTab(task: string, last: LastTab) {
  if (!task || (!last.session && !last.file)) return
  const all = readAll()
  delete all[task] // 重新插到末尾 = 最近用过
  all[task] = last
  const keys = Object.keys(all)
  for (const k of keys.slice(0, Math.max(0, keys.length - MAX))) delete all[k]
  try { localStorage.setItem(KEY, JSON.stringify(all)) } catch { /* 记不住而已 */ }
}
