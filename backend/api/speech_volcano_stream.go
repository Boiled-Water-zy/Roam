package api

// 火山「流式语音识别 2.0」（sauc）：WebSocket + 自定义二进制帧。按住说话拿到的是整段音频，
// 走 bigmodel_nostream 端点：整段切片发完（最后一片负序号），服务端一次回最终结果。
//
// 帧格式（v3 bigmodel 协议）：4 字节头 [版本<<4|头长][类型<<4|标志][序列化<<4|压缩][保留]
// + 有序号标志时 int32 序号 + uint32 载荷长度 + 载荷（gzip）。错误帧：uint32 错误码 + uint32 长度 + 消息。

import (
	"bytes"
	"compress/gzip"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/gorilla/websocket"
)

const (
	saucFullClientRequest = 0x01
	saucAudioOnlyRequest  = 0x02
	saucFullServerResp    = 0x09
	saucServerAck         = 0x0b
	saucServerError       = 0x0f

	saucFlagPosSeq = 0x01 // 带序号
	saucFlagNegSeq = 0x03 // 带序号且是最后一片（序号取负）

	saucJSON = 0x1
	saucGzip = 0x1
	// 200ms 一片（16k/16bit/单声道 = 6400 字节），照官方样例
	saucChunk = 6400
)

// saucFrame 组一帧：payload 已按 comp 压缩。
func saucFrame(msgType, flags, ser, comp byte, seq int32, payload []byte) []byte {
	var b bytes.Buffer
	b.Write([]byte{0x10 | 0x01, msgType<<4 | flags, ser<<4 | comp, 0})
	if flags&0x01 != 0 {
		_ = binary.Write(&b, binary.BigEndian, seq)
	}
	_ = binary.Write(&b, binary.BigEndian, uint32(len(payload)))
	b.Write(payload)
	return b.Bytes()
}

func saucGzipBytes(raw []byte) []byte {
	var b bytes.Buffer
	w := gzip.NewWriter(&b)
	_, _ = w.Write(raw)
	_ = w.Close()
	return b.Bytes()
}

type saucMsg struct {
	Type    byte
	Seq     int32
	HasSeq  bool
	Code    uint32
	Payload []byte // 已解压
}

// saucParse 拆一帧服务端消息。
func saucParse(b []byte) (saucMsg, error) {
	var m saucMsg
	if len(b) < 4 {
		return m, fmt.Errorf("frame too short")
	}
	hdr := int(b[0]&0x0f) * 4
	if hdr < 4 || len(b) < hdr {
		return m, fmt.Errorf("bad header size %d", hdr)
	}
	m.Type = b[1] >> 4
	flags := b[1] & 0x0f
	comp := b[2] & 0x0f
	p := b[hdr:]
	if flags&0x01 != 0 {
		if len(p) < 4 {
			return m, fmt.Errorf("truncated sequence")
		}
		m.Seq = int32(binary.BigEndian.Uint32(p[:4]))
		m.HasSeq = true
		p = p[4:]
	}
	switch m.Type {
	case saucServerError:
		if len(p) < 8 {
			return m, fmt.Errorf("truncated error frame")
		}
		m.Code = binary.BigEndian.Uint32(p[:4])
		n := int(binary.BigEndian.Uint32(p[4:8]))
		p = p[8:]
		if n > len(p) {
			n = len(p)
		}
		m.Payload = p[:n]
	case saucFullServerResp, saucServerAck:
		if len(p) < 4 {
			return m, nil
		}
		n := int(binary.BigEndian.Uint32(p[:4]))
		p = p[4:]
		if n > len(p) {
			n = len(p)
		}
		m.Payload = p[:n]
	}
	if comp == saucGzip && len(m.Payload) > 0 {
		r, err := gzip.NewReader(bytes.NewReader(m.Payload))
		if err == nil {
			if out, err2 := io.ReadAll(r); err2 == nil {
				m.Payload = out
			}
		}
	}
	return m, nil
}

// transcribeVolcanoStream 用 sauc 的 nostream 端点识别整段音频。
func transcribeVolcanoStream(cfg VolcanoSpeech, resourceID string, audio []byte) (string, error) {
	endpoint := cfg.Endpoint
	if !strings.HasPrefix(endpoint, "ws") {
		endpoint = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_nostream"
	}
	h := http.Header{}
	if cfg.APIKey != "" {
		h.Set("X-Api-Key", cfg.APIKey)
	} else {
		h.Set("X-Api-App-Key", cfg.AppID)
		h.Set("X-Api-Access-Key", cfg.AccessToken)
	}
	h.Set("X-Api-Resource-Id", resourceID)
	h.Set("X-Api-Connect-Id", randomID())
	d := websocket.Dialer{HandshakeTimeout: 15 * time.Second}
	conn, resp, err := d.Dial(endpoint, h)
	if err != nil {
		if resp != nil {
			body, _ := io.ReadAll(resp.Body)
			return "", fmt.Errorf("volcano sauc connect %d: %s %s", resp.StatusCode, resp.Header.Get("X-Api-Message"), truncate(string(body), 200))
		}
		return "", fmt.Errorf("volcano sauc connect: %w", err)
	}
	defer conn.Close()

	req := map[string]any{
		"user":  map[string]any{"uid": "roami"},
		"audio": map[string]any{"format": "wav", "codec": "raw", "rate": 16000, "bits": 16, "channel": 1},
		"request": map[string]any{
			"model_name": "bigmodel", "enable_itn": true, "enable_punc": true, "enable_ddc": true,
			"show_utterances": false, "enable_nonstream": false,
		},
	}
	reqJSON, _ := json.Marshal(req)
	if err := conn.WriteMessage(websocket.BinaryMessage, saucFrame(saucFullClientRequest, saucFlagPosSeq, saucJSON, saucGzip, 1, saucGzipBytes(reqJSON))); err != nil {
		return "", fmt.Errorf("volcano sauc send request: %w", err)
	}
	seq := int32(1)
	for off := 0; off < len(audio); off += saucChunk {
		end := off + saucChunk
		if end > len(audio) {
			end = len(audio)
		}
		seq++
		flags, s := byte(saucFlagPosSeq), seq
		if end == len(audio) {
			flags, s = saucFlagNegSeq, -seq
		}
		if err := conn.WriteMessage(websocket.BinaryMessage, saucFrame(saucAudioOnlyRequest, flags, 0, saucGzip, s, saucGzipBytes(audio[off:end]))); err != nil {
			return "", fmt.Errorf("volcano sauc send audio: %w", err)
		}
	}
	// 收结果：nostream 模式发完最后一片后服务端回终态（负序号）；中间可能有确认/中间结果，以最后一条为准
	_ = conn.SetReadDeadline(time.Now().Add(60 * time.Second))
	text := ""
	for {
		_, data, err := conn.ReadMessage()
		if err != nil {
			if text != "" {
				return text, nil
			}
			return "", fmt.Errorf("volcano sauc read: %w", err)
		}
		m, perr := saucParse(data)
		if perr != nil {
			return "", fmt.Errorf("volcano sauc: %w", perr)
		}
		switch m.Type {
		case saucServerError:
			return "", fmt.Errorf("volcano sauc %d: %s", m.Code, truncate(string(m.Payload), 300))
		case saucFullServerResp:
			var r struct {
				Result struct {
					Text string `json:"text"`
				} `json:"result"`
			}
			if json.Unmarshal(m.Payload, &r) == nil && r.Result.Text != "" {
				text = r.Result.Text
			}
			if m.HasSeq && m.Seq < 0 {
				return text, nil
			}
		}
	}
}
