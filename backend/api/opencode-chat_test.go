package api

import (
	"database/sql"
	"os"
	"path/filepath"
	"testing"
)

func TestParseOpenCodeMessage(t *testing.T) {
	raw := `{"time":{"created":1791882098642,"completed":1791882100842},"model":{"id":"test-model"},"content":[{"type":"reasoning","text":"checking"},{"type":"tool","id":"call_1","name":"shell","state":{"status":"completed","input":{"command":"pwd"},"content":[{"type":"text","text":"/tmp"}]}},{"type":"text","text":"done"}]}`
	m, model := parseOpenCodeMessage("msg_1", "assistant", raw)
	if m.ID != "msg_1" || m.Role != "assistant" || model != "test-model" || len(m.Blocks) != 4 {
		t.Fatalf("unexpected message: %+v, model=%q", m, model)
	}
	if m.Blocks[1].Kind != "tool_use" || m.Blocks[1].Input != `{"command":"pwd"}` || m.Blocks[2].Kind != "tool_result" || m.Blocks[2].ToolUseID != "call_1" || m.Blocks[2].Text != "/tmp" {
		t.Fatalf("tool pair was lost: %+v", m.Blocks)
	}
}

func TestOpenCodeSessionDoesNotPickOldSession(t *testing.T) {
	db, err := sql.Open("sqlite", filepath.Join(t.TempDir(), "opencode.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	if _, err := db.Exec(`CREATE TABLE session_v2 (id TEXT, directory TEXT, time_created INTEGER, time_updated INTEGER, time_viewed INTEGER);
		INSERT INTO session_v2 VALUES ('ses_old','/project',1,2,NULL);`); err != nil {
		t.Fatal(err)
	}
	if got := openCodeSession(db, "/project", os.Getpid()); got != "" {
		t.Fatalf("picked stale session %q", got)
	}
	if _, err := db.Exec(`INSERT INTO session_v2 VALUES ('ses_new','/project',9999999999999,9999999999999,NULL)`); err != nil {
		t.Fatal(err)
	}
	if got := openCodeSession(db, "/project", os.Getpid()); got != "ses_new" {
		t.Fatalf("selected %q", got)
	}
}
