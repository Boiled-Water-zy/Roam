// 手机「我」页（24 稿 §5）：机器 · 通知 · 工具 · 账户。原来塞在「更多」sheet 里的东西都搬到这儿，
// 不再需要一个「更多」。
import type { ReactNode } from 'react'
import { Modal } from 'antd'
import { useI18n } from '../../i18n'
import { SheetRow, SheetSection } from '../shell/MobileSheet'
import { NodeMark, nodeDotColor } from '../cluster/NodeMark'
import { ICONS } from '../nav-icons'
import { DeviceIcon, LogoutIcon, PlusIcon, SearchIcon } from '../../icons'
import { PushSettings } from '../settings/push-settings'

type Node = { id: string; name: string; online: boolean; sessionCount: number }

export default function MobileMe({ nodes, curNodeId, onSwitchNode, onNav, onSearch, onNewProject, themeIcon, themeLabel, onToggleTheme, fsSupported, fsIcon, fsLabel, onToggleFs, onLogout }: {
  nodes: Node[]
  curNodeId: string | null
  onSwitchNode: (id: string) => void
  onNav: (key: string) => void
  onSearch: () => void
  onNewProject: () => void
  themeIcon: ReactNode
  themeLabel: string
  onToggleTheme: () => void
  fsSupported: boolean
  fsIcon: ReactNode
  fsLabel: string
  onToggleFs: () => void
  onLogout: () => void
}) {
  const { t } = useI18n()
  return (
    <div className="tt-mme">
      <header className="tt-pagehead tt-mobile-pagehead">
        <div className="ttl"><div className="kicker">{t('mobile.me.kicker')}</div><h2>{t('nav.me')}</h2><p>{t('mobile.me.lead')}</p></div>
      </header>
      <div className="tt-mme-group">
        <SheetRow icon={<SearchIcon size={16} />} title={t('workspace.search')} desc={t('workspace.searchPlaceholder')} onClick={onSearch} />
        <SheetRow icon={<PlusIcon size={16} />} title={t('project.newProject')} onClick={onNewProject} />
      </div>

      {nodes.length > 0 && (<>
        <SheetSection>{t('node.switch')}</SheetSection>
        <div className="tt-mme-group">
        {nodes.map((n) => (
          <SheetRow key={n.id}
            icon={<NodeMark name={n.name} size="sm" current={n.id === curNodeId} offline={!n.online} />}
            title={n.name}
            desc={n.online ? t('node.sessionsN', { count: n.sessionCount }) : t('node.offline')}
            active={n.id === curNodeId}
            extra={<i style={{ display: 'block', width: 7, height: 7, borderRadius: '50%', background: nodeDotColor(n as any) }} />}
            onClick={() => { if (n.online && n.id !== curNodeId) onSwitchNode(n.id) }} />
        ))}
        </div>
      </>)}

      <SheetSection>{t('mobile.sec.notify')}</SheetSection>
      <div className="tt-mme-group">
        <SheetRow icon={<DeviceIcon size={16} />} title={t('install.meRow')} desc={t('install.pageLead')} onClick={() => onNav('install')} />
        <div className="tt-mme-push">
          <div><b>{t('set.push')}</b><span>{t('set.pushHelp')}</span></div>
          <PushSettings />
        </div>
      </div>

      <SheetSection>{t('nav.groupTools')}</SheetSection>
      <div className="tt-mme-group">
      {['files', 'browser', 'phone', 'plugins'].map((key) => (
        <SheetRow key={key} icon={ICONS[key]} title={t('nav.' + key)} onClick={() => onNav(key)} />
      ))}
      </div>

      <SheetSection>{t('mobile.groupAccount')}</SheetSection>
      <div className="tt-mme-group">
      <SheetRow icon={ICONS.settings} title={t('nav.env')} onClick={() => onNav('settings')} />
      <SheetRow icon={themeIcon} title={themeLabel} onClick={onToggleTheme} />
      {fsSupported && <SheetRow icon={fsIcon} title={fsLabel} onClick={onToggleFs} />}
      <SheetRow icon={ICONS.github} title={t('nav.about')} onClick={() => onNav('about')} />
      <SheetRow icon={<LogoutIcon />} title={t('common.logout')} desc={t('common.logoutConfirm')} danger
        onClick={() => Modal.confirm({ title: t('common.logoutConfirm'), okText: t('common.logout'), cancelText: t('common.cancel'), okButtonProps: { danger: true }, onOk: onLogout })} />
      </div>
    </div>
  )
}
