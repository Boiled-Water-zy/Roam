package server

import (
	"crypto/x509"
	"encoding/pem"
	"os"
)

// selfSignedCert 判断下发的证书是不是 roami 自己签的 CA（用户换了正式证书就不用引导装证书了）
func selfSignedCert(path string) bool {
	if path == "" {
		return false
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		return false
	}
	block, _ := pem.Decode(raw)
	if block == nil {
		return false
	}
	cert, err := x509.ParseCertificate(block.Bytes)
	if err != nil {
		return false
	}
	return cert.IsCA && cert.Subject.CommonName == "ttmux-web local CA"
}
