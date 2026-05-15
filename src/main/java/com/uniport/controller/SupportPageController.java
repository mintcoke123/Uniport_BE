package com.uniport.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SupportPageController {

    private static final MediaType TEXT_HTML_UTF8 = MediaType.parseMediaType("text/html;charset=UTF-8");

    private static final String SUPPORT_HTML = """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Uniport 지원</title>
              <style>
                :root {
                  color-scheme: light;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  color: #17202a;
                  background: #f6f8fb;
                }
                body {
                  margin: 0;
                  padding: 40px 20px;
                }
                main {
                  max-width: 720px;
                  margin: 0 auto;
                  background: #ffffff;
                  border: 1px solid #d8dee8;
                  border-radius: 8px;
                  padding: 32px;
                }
                h1 {
                  margin: 0 0 16px;
                  font-size: 28px;
                }
                p {
                  line-height: 1.6;
                }
                dl {
                  display: grid;
                  grid-template-columns: 96px 1fr;
                  gap: 12px 16px;
                  margin: 24px 0;
                }
                dt {
                  font-weight: 700;
                }
                dd {
                  margin: 0;
                  word-break: break-word;
                }
                a {
                  color: #0057b8;
                }
                @media (max-width: 520px) {
                  main {
                    padding: 24px;
                  }
                  dl {
                    grid-template-columns: 1fr;
                    gap: 6px;
                  }
                }
              </style>
            </head>
            <body>
              <main>
                <h1>Uniport 지원</h1>
                <p>Uniport 앱 사용 중 문제가 발생했거나 계정, 결제, 개인정보 관련 문의가 필요한 경우 아래 연락처로 문의해 주세요.</p>
                <dl>
                  <dt>이메일</dt>
                  <dd><a href="mailto:kwakkun2002@gmail.com">kwakkun2002@gmail.com</a></dd>
                  <dt>전화</dt>
                  <dd><a href="tel:+821066345516">010-6634-5516</a></dd>
                </dl>
                <p>문의 시 사용 중인 기기, OS 버전, 앱 버전, 발생한 문제 상황을 함께 알려주시면 더 정확하게 확인할 수 있습니다.</p>
              </main>
            </body>
            </html>
            """;

    @GetMapping(value = {"/support", "/support.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSupportPage() {
        return ResponseEntity.ok()
                .contentType(TEXT_HTML_UTF8)
                .body(SUPPORT_HTML);
    }
}
