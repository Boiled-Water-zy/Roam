import { type ReactNode } from 'react'
import { useI18n } from '../../i18n'
import { useBackDismiss } from './useBackDismiss'
import { CloseIcon } from '../../icons'

export function MobileSheet({ open, title, onClose, children }: {
  open: boolean
  title: ReactNode
  onClose: () => void
  children: ReactNode
}) {
  const { t } = useI18n()
  useBackDismiss(open, onClose)

  if (!open) return null
  return (
    <>
      <button type="button" className="tt-sheet-scrim" aria-label={t('common.close')} onClick={onClose} />
      <div role="dialog" aria-modal="true" aria-label={typeof title === 'string' ? title : undefined} className="tt-sheet">
        <div className="tt-sheet-handle"><i /></div>
        <div className="tt-sheet-head">
          <b>{title}</b>
          <button type="button" onClick={onClose} aria-label={t('common.close')}><CloseIcon size={18} /></button>
        </div>
        <div className="tt-sheet-body">{children}</div>
      </div>
    </>
  )
}

export function SheetRow({ icon, title, desc, danger, active, extra, minHeight, onClick }: {
  icon?: ReactNode
  title: ReactNode
  desc?: ReactNode
  danger?: boolean
  active?: boolean
  extra?: ReactNode
  minHeight?: number
  onClick: () => void
}) {
  return (
    <button type="button" onClick={onClick} className={`tt-sheet-row${danger ? ' danger' : ''}${active ? ' active' : ''}`} style={minHeight ? { minHeight } : undefined}>
      {icon && <span className="tt-sheet-row-icon">{icon}</span>}
      <span className="tt-sheet-row-copy">
        <span className="tt-sheet-row-title">{title}</span>
        {desc && <span className="tt-sheet-row-desc">{desc}</span>}
      </span>
      {extra && <span className="tt-sheet-row-extra">{extra}</span>}
    </button>
  )
}

export function SheetSection({ children }: { children: ReactNode }) {
  return <div className="tt-sheet-section">{children}</div>
}
