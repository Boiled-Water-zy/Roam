// Android App 的更新提示。App 是个壳，里面的网页跟着服务器走、不用更新；
// 只有壳本身变了（上传、通知这类原生能力）才需要装新包——这里比一下版本号，有新的就在首页顶上提一句。
import { useEffect, useState } from 'react'
import { App as AntApp } from 'antd'
import { api } from '../../api'
import { nodeApi } from '../cluster/node-url'
import { useI18n } from '../../i18n'

/** 本页跑在 Roami App 里时返回壳的版本号，否则 null。0.2.x 及更早的壳 UA 里写的是 RoamiApp/1 */
export function appShellVersion(ua: string = navigator.userAgent): string | null {
  const m = ua.match(/RoamiApp\/([\w.]+)/)
  return m ? m[1] : null
}

export function newer(a: string, b: string): boolean {
  const pa = a.split('.').map((n) => parseInt(n, 10) || 0), pb = b.split('.').map((n) => parseInt(n, 10) || 0)
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    if ((pa[i] || 0) !== (pb[i] || 0)) return (pa[i] || 0) > (pb[i] || 0)
  }
  return false
}

export function AppUpdateBanner() {
  const { t } = useI18n()
  const { message } = AntApp.useApp()
  const [latest, setLatest] = useState('')
  const mine = appShellVersion()
  useEffect(() => {
    if (!mine) return
    api('GET', '/install-info').then((r) => setLatest(r.data?.apkVersion || '')).catch(() => {})
  }, [mine])
  // 老壳报的是「1」，和 0.x 比会被当成更新的；老壳一律提示
  const old = mine === '1'
  if (!mine || !latest || !(old || newer(latest, mine))) return null
  if (old) {
    const url = `${location.origin}/#/install`
    const copy = async () => {
      let copied = false
      try {
        if (navigator.clipboard?.writeText) { await navigator.clipboard.writeText(url); copied = true }
      } catch { /* 旧 WebView 的剪贴板接口可能存在但不能用 */ }
      if (!copied) {
        const field = document.createElement('textarea')
        field.value = url
        field.style.position = 'fixed'
        field.style.opacity = '0'
        document.body.appendChild(field)
        field.select()
        try { copied = document.execCommand('copy') } catch { /* 长按地址仍可手动复制 */ }
        finally { field.remove() }
      }
      if (copied) message.success(t('mobile.app.linkCopied'))
      else message.error(t('mobile.app.copyFailed'))
    }
    return (
      <button type="button" className="tt-appupdate" onClick={() => void copy()}>
        <b>{t('mobile.app.update', { v: latest })}</b>
        <span>{t('mobile.app.legacyHelp')}</span>
        <code>{url}</code>
        <span className="action">{t('mobile.app.copyLink')}</span>
      </button>
    )
  }
  return (
    <a className="tt-appupdate" href={nodeApi('/apk')}>
      <b>{t('mobile.app.update', { v: latest })}</b>
      <span>{t('mobile.app.updateHelp')}</span>
    </a>
  )
}
