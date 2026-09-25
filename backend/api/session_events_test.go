package api

import "testing"

const waitScreen = "Do you want to proceed?\n❯ 1. Yes\n  2. No\nEnter to select · Esc to cancel"

func TestSessionEventsDebounceAndReset(t *testing.T) {
	ev := newSessionEvents()
	sess := map[string]string{"s1": "调研"}
	cap := func(string) string { return waitScreen }
	if got := ev.observe(sess, cap); len(got) != 0 {
		t.Fatalf("第一轮不该发（残帧）: %+v", got)
	}
	got := ev.observe(sess, cap)
	if len(got) != 1 || got[0].Type != "session.waiting" || got[0].Session != "s1" || got[0].Label != "调研" {
		t.Fatalf("第二轮该发一条: %+v", got)
	}
	if len(got[0].Actions) != 2 {
		t.Fatalf("1. Yes 这种该带 允许/拒绝: %+v", got[0])
	}
	if got[0].Body != "Do you want to proceed? · 1. Yes / 2. No" {
		t.Fatalf("正文该是问题加选项: %q", got[0].Body)
	}
	if got := ev.observe(sess, cap); len(got) != 0 {
		t.Fatalf("同一次等待不该重复发: %+v", got)
	}
	// 回到不等，再等：再发
	ev.observe(sess, func(string) string { return "$ " })
	ev.observe(sess, cap)
	if got := ev.observe(sess, cap); len(got) != 1 {
		t.Fatalf("复位后再等该再发: %+v", got)
	}
	// 会话没了：状态清掉
	ev.observe(map[string]string{}, cap)
	if len(ev.state) != 0 {
		t.Fatalf("会话没了状态该清: %+v", ev.state)
	}
}

func TestSessionEventsNoActionsForFreeChoice(t *testing.T) {
	ev := newSessionEvents()
	sess := map[string]string{"s": "x"}
	cap := func(string) string { return "选一个\n❯ 1. 按周\n  2. 按月\n  3. 自定义\nEnter to select" }
	ev.observe(sess, cap)
	got := ev.observe(sess, cap)
	if len(got) != 1 || len(got[0].Actions) != 0 {
		t.Fatalf("三选一不该带 允许/拒绝: %+v", got)
	}
}
