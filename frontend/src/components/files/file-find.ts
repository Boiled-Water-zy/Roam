type FindTarget = { open: () => void; priority: number | (() => number) }

const targets = new Map<symbol, FindTarget>()

export function registerFileFind(open: () => void, priority: FindTarget['priority'] = 0): () => void {
  const id = Symbol('file-find')
  targets.set(id, { open, priority })
  return () => { targets.delete(id) }
}

export function openCurrentFileFind(): boolean {
  const rank = (target: FindTarget) => typeof target.priority === 'function' ? target.priority() : target.priority
  const target = [...targets.values()].sort((a, b) => rank(b) - rank(a))[0]
  if (!target) return false
  target.open()
  return true
}
