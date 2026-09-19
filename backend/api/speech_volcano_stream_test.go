package api

import (
	"encoding/json"
	"testing"
)

// 组帧 / 拆帧对得上：有序号、gzip JSON 载荷、错误帧
func TestSaucFrameRoundTrip(t *testing.T) {
	payload, _ := json.Marshal(map[string]any{"result": map[string]any{"text": "你好"}})
	f := saucFrame(saucFullServerResp, saucFlagNegSeq, saucJSON, saucGzip, -3, saucGzipBytes(payload))
	m, err := saucParse(f)
	if err != nil {
		t.Fatal(err)
	}
	if m.Type != saucFullServerResp || !m.HasSeq || m.Seq != -3 {
		t.Fatalf("头解析错: %+v", m)
	}
	var r struct{ Result struct{ Text string } }
	if json.Unmarshal(m.Payload, &r) != nil || r.Result.Text != "你好" {
		t.Fatalf("载荷没解出来: %q", m.Payload)
	}
	// 错误帧：code + len + msg，不带序号
	msg := []byte(`{"error":"bad"}`)
	e := []byte{0x11, saucServerError << 4, 0x10, 0, 0, 0, 0x2a, 0xf8, 0, 0, 0, byte(len(msg))}
	e = append(e, msg...)
	em, err := saucParse(e)
	if err != nil || em.Type != saucServerError || em.Code != 0x2af8 || string(em.Payload) != string(msg) {
		t.Fatalf("错误帧解析错: %+v %v", em, err)
	}
}
