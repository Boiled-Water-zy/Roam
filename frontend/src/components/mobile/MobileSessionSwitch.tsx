// 手机会话页顶上那颗胶囊点开的「切换会话」。
// 原来列的是「打开过的标签」——桌面概念，手机上没人知道那个数字和那串 × 是什么。
// 现在只回答手机上真正要问的两件事：谁在等我；我最近在看哪几个。其余去「全部会话」。
import { useEffect, useState } from 'react'
import { api } from '../../api'
import { useI18n } from '../../i18n'
import { AgentLogo, TerminalIcon, TabsIcon } from '../../icons'
import { MobileSheet, SheetRow, SheetSection } from '../shell/MobileSheet'
import type { OverviewItem } from './MobileSessions'

const RECENT = 6

export function MobileSessionSwitch({ open, active, onPick, onAll, onClose }: {
  open: boolean
  active: string | null
  onPick: (name: string) => void
  onAll: () => void
  onClose: () => void
}) {
  const { t } = useI18n()
  const [items, setItems] = useState<OverviewItem[]>([])
  useEffect(() => {
    if (!open) return
    let stop = false
    const load = () => api('GET', '/sessions/overview').then((r) => { if (!stop) setItems(r.data.items || []) }).catch(() => {})
    load()
    const i = setInterval(load, 5000)
    return () => { stop = true; clearInterval(i) }
  }, [open])

  // 先关单子、等它那条历史记录退完，再动会话。两件事同一拍做的话：切会话改了地址栏参数，
  // 紧接着单子那一步 back() 落到参数不同的上一条记录上，路由当成「换页了」，把全屏会话也一起收掉。
  const afterSheetGone = (fn: () => void) => {
    let done = false
    const run = () => { if (done) return; done = true; window.removeEventListener('popstate', run); fn() }
    window.addEventListener('popstate', run)
    setTimeout(run, 300) // 没压过历史记录（桌面窄窗）就不会有 popstate，兜底
    onClose()
  }

  const waiting = items.filter((s) => s.waiting && s.name !== active)
  const recent = items.filter((s) => !s.dormant && !s.waiting && s.name !== active).slice(0, RECENT)
  const row = (s: OverviewItem) => (
    <SheetRow key={s.name} minHeight={56}
      icon={s.agent ? <AgentLogo kind={s.agent} size={16} /> : <TerminalIcon size={16} />}
      title={<span className="ttl">{s.project && <span className="pj">{s.project} ·</span>}<span className="nm">{s.label}</span></span>}
      desc={s.waiting ? (s.tail || t('mobile.st.waiting')) : s.running ? t('mobile.st.running') : t('mobile.st.idle')}
      onClick={() => afterSheetGone(() => onPick(s.name))} />
  )
  return (
    <MobileSheet open={open} title={t('mobile.switchSession')} onClose={onClose}>
      {waiting.length > 0 && <><SheetSection>{t('inbox.waiting')}</SheetSection>{waiting.map(row)}</>}
      {recent.length > 0 && <><SheetSection>{t('mobile.switchRecent')}</SheetSection>{recent.map(row)}</>}
      <SheetRow icon={<TabsIcon size={16} />} title={t('project.allSessions')} onClick={() => afterSheetGone(onAll)} />
    </MobileSheet>
  )
}
