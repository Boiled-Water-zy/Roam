package api

// 会话事件：把「会话在等你」从抓屏里认出来变成一条通知（24 稿 §6）。
// 5s 一轮抓每个活会话的屏，sessionWaiting 连续两轮为真才发（TUI 重绘残帧会闪一下），
// 回到不等就复位——下次再等再发一条。event → 收件箱落库 + Web Push 广播。

import (
	"encoding/json"
	"regexp"
	"strings"
	"time"
)

type sessionEventState struct {
	streak   int
	notified bool
}

type sessionEvents struct {
	state map[string]sessionEventState
}

func newSessionEvents() *sessionEvents { return &sessionEvents{state: map[string]sessionEventState{}} }

// yesNo：屏幕上能确定是 y/n 或「1. Yes」这类二选一时，通知上才给 允许 / 拒绝 按钮，
// 不然只给「打开」——按错一格会话就没了
var yesNoPat = regexp.MustCompile(`(?i)\((?:y/n|yes/no|y/N|Y/n)\)|\[y/n\]`)
var yesFirst = regexp.MustCompile(`(?i)^(yes|allow|approve|ok|continue|proceed|是|允许|确认|继续)\b`)

// observe 喂一轮观察：sessions = 活会话 name → label；capture 由调用方按需抓。
// 返回这一轮该发的事件。
func (e *sessionEvents) observe(sessions map[string]string, capture func(name string) string) []PushPayload {
	var out []PushPayload
	for name := range e.state {
		if _, ok := sessions[name]; !ok {
			delete(e.state, name)
		}
	}
	for name, label := range sessions {
		cap := capture(name)
		st := e.state[name]
		if !sessionWaiting(cap) {
			e.state[name] = sessionEventState{}
			continue
		}
		st.streak++
		if st.streak >= 2 && !st.notified {
			st.notified = true
			p := PushPayload{Type: "session.waiting", Session: name, Label: label, Title: label, Body: waitingSummary(cap, 140)}
			// 第一个选项是 Yes / Allow 这类、或屏上有 (y/n)：通知才带 允许 / 拒绝
			if _, opts, ok := waitingPrompt(cap); (ok && len(opts) > 0 && yesFirst.MatchString(opts[0])) || yesNoPat.MatchString(waitStripCtl(cap)) {
				p.Actions = []string{"allow", "deny"}
			}
			out = append(out, p)
		}
		e.state[name] = st
	}
	return out
}

// SessionEventLoop 后台探测循环
func (a *API) SessionEventLoop() {
	ev := newSessionEvents()
	t := time.NewTicker(5 * time.Second)
	defer t.Stop()
	for range t.C {
		if a.Push.Count() == 0 && !a.Meta.OK() {
			continue // 没人订阅也没处落，白抓屏
		}
		out, err := a.TT.Run("ls", "--json")
		if err != nil {
			continue
		}
		var list []sessListItem
		if json.Unmarshal([]byte(out), &list) != nil {
			continue
		}
		sessions := map[string]string{}
		for _, s := range list {
			if s.State == "dormant" || strings.HasPrefix(s.Name, "_ttmux-") {
				continue
			}
			label := s.Label
			if label == "" {
				label = s.Name
			}
			sessions[s.Name] = label
		}
		for _, p := range ev.observe(sessions, func(n string) string { return sessionCapture(n, 60) }) {
			p.ID = a.Inbox.Publish(p.Type, p.Session, p.Label, p.Body)
			p.Badge = a.Inbox.Unread()
			a.Push.Broadcast(p)
		}
	}
}
