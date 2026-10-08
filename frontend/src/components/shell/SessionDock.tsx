// 会话坞（13 设计 §4.2）——手机上最缺的那块。
//
// 今天手机上的会话只有两态：全屏覆盖，或完全看不见。退回项目页那一刻，你就不知道
// 还有几个会话在跑、哪个在等你确认——而这恰恰是 Roami 存在的理由。
//
// 于是在底栏之上加一条 50px 的常驻坞：左边是上一个看过的会话（点或上滑回去），
// 右边是「几个等你」（点了去会话页，那里把等你的钉在顶上）。
// 原来右侧还有「打开过的标签数」和切换 sheet——24 稿会话页已经把所有会话按项目列全了，
// 「打开过的标签」在手机上就是个多余概念，用户看着数字变来变去也不知道是什么。
import { useRef } from 'react'
import { useI18n } from '../../i18n'
import { sessionLabel } from '../sessions/session-label'
import { useSessionProject } from '../sessions/session-project'
import { MobileSheet, SheetRow } from './MobileSheet'
import { ChevronUp, CloseIcon } from '../../icons'

/** `项目 · 会话`：空间不足时先截项目名，会话名不省略——会话名才是你要找的那个 */
export function DockTitle({ name }: { name: string }) {
  const proj = useSessionProject(name)
  return (
    <span className="ttl">
      {proj && <span className="pj">{proj.name} ·</span>}
      <span className="nm">{sessionLabel(name) || name}</span>
    </span>
  )
}

export function SessionDock({ last, needsInput, running, waitingTotal, onOpen, onWaiting }: {
  /** 上一个看过的会话（回去就是它）；没有就只剩「几个等你」 */
  last: string | null
  needsInput: Record<string, boolean>
  /** 会话是否有 Agent 在跑，只用于状态点颜色 */
  running: (name: string) => boolean
  /** 全部会话里在等你的个数，不只是打开过的 */
  waitingTotal: number
  onOpen: () => void
  onWaiting: () => void
}) {
  const { t } = useI18n()
  const touch = useRef<{ y: number; t: number } | null>(null)
  if (!last && !waitingTotal) return null

  const dotColor = (n: string) => (needsInput[n] ? 'var(--warn)' : running(n) ? 'var(--ok)' : 'var(--text-dimmer)')

  return (
    <div className="tt-sessdock" role="button" tabIndex={0}
      aria-label={t('mobile.sessionDock')}
      onClick={last ? onOpen : onWaiting}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); (last ? onOpen : onWaiting)() } }}
      // 上滑展开：拇指从坞往上一推就是"把会话拉出来"，比瞄准点按快
      onTouchStart={(e) => { touch.current = { y: e.touches[0].clientY, t: Date.now() } }}
      onTouchEnd={(e) => {
        const s = touch.current
        touch.current = null
        if (!s || !last) return
        const dy = e.changedTouches[0].clientY - s.y
        if (dy < -30 && Date.now() - s.t < 600) { e.preventDefault(); onOpen() }
      }}>
      {last && <i className="d" style={{ background: dotColor(last) }} />}
      {last && <DockTitle name={last} />}
      {waitingTotal > 0 && (
        <button type="button" className="wait" onClick={(e) => { e.stopPropagation(); onWaiting() }}>{t('mobile.waitingN', { count: waitingTotal })}</button>
      )}
      {last && <span className="up"><ChevronUp size={14} /></span>}
    </div>
  )
}

/**
 * 会话切换 sheet（13 §4.2 / §5.1）。会话坞和手机会话页顶栏共用同一个：
 * 两处问的是同一个问题「切到哪个会话」，长两套就会慢慢长歪。
 *
 * 一屏 8 行 × 56，比横滑标签条能看到的 2–3 个多一个数量级——这正是标签条在手机上
 * 被换掉的原因（App.tsx 里那句 scrollIntoView 就是标签条滑出视口的补丁）。
 */
export function SessionSwitchSheet({ open, sessions, active, needsInput, running, onPick, onCloseSession, onClose }: {
  open: boolean
  sessions: string[]
  active: string | null
  needsInput: Record<string, boolean>
  running: (name: string) => boolean
  onPick: (name: string) => void
  onCloseSession: (name: string) => void
  onClose: () => void
}) {
  const { t } = useI18n()
  const dotColor = (n: string) => (needsInput[n] ? 'var(--warn)' : running(n) ? 'var(--ok)' : 'var(--text-dimmer)')
  return (
    <MobileSheet open={open} title={t('mobile.switchSession')} onClose={onClose}>
      {sessions.map((n) => (
        <SheetRow key={n}
          icon={<i style={{ width: 8, height: 8, borderRadius: '50%', background: dotColor(n), display: 'block' }} />}
          title={<DockTitle name={n} />}
          desc={needsInput[n] ? t('session.waiting') : undefined}
          active={n === active}
          minHeight={56}
          onClick={() => { onClose(); onPick(n) }}
          extra={(
            <button type="button" className="tt-sessdock-x" aria-label={t('common.close')}
              onClick={(e) => { e.stopPropagation(); onCloseSession(n) }}><CloseIcon /></button>
          )}
        />
      ))}
      {/* 这张 sheet 只列「开着的终端」；搜索、筛选、Worktree 管理、新建竞赛都在会话页。
          你正想着会话的时候就在这儿，入口放这里比绕回概览近得多。 */}
      <SheetRow
        icon={<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="4" y1="7" x2="20" y2="7" /><line x1="4" y1="12" x2="20" y2="12" /><line x1="4" y1="17" x2="14" y2="17" /></svg>}
        title={t('project.allSessions')}
        onClick={() => { onClose(); location.hash = '#/sessions' }}
      />
    </MobileSheet>
  )
}
