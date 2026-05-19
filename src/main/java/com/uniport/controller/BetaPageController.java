package com.uniport.controller;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
public class BetaPageController {

    private static final MediaType TEXT_HTML_UTF8 = MediaType.parseMediaType("text/html;charset=UTF-8");
    private static final String DEFAULT_CONTACT_EMAIL = "kwakkun2002@gmail.com";

    private static final String BETA_HTML = """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Uniport Beta Test</title>
              <style>
                :root {
                  color-scheme: light;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  color: #17202a;
                  background: #f4f7fb;
                }
                * {
                  box-sizing: border-box;
                }
                body {
                  margin: 0;
                  padding: 32px 16px;
                  background:
                    linear-gradient(135deg, rgba(0, 87, 184, 0.10), rgba(0, 162, 143, 0.08)),
                    #f4f7fb;
                }
                main {
                  max-width: 880px;
                  margin: 0 auto;
                }
                header {
                  padding: 28px 0 20px;
                }
                .eyebrow {
                  margin: 0 0 10px;
                  color: #0057b8;
                  font-size: 14px;
                  font-weight: 800;
                  letter-spacing: 0;
                  text-transform: uppercase;
                }
                h1 {
                  margin: 0;
                  color: #0f1f33;
                  font-size: clamp(34px, 7vw, 56px);
                  line-height: 1.04;
                  letter-spacing: 0;
                }
                .lead {
                  max-width: 680px;
                  margin: 18px 0 0;
                  color: #405066;
                  font-size: 18px;
                  line-height: 1.65;
                }
                .notice {
                  display: flex;
                  gap: 12px;
                  align-items: flex-start;
                  margin: 24px 0;
                  padding: 16px;
                  border: 1px solid #d8e2ef;
                  border-radius: 8px;
                  background: #ffffff;
                  color: #304056;
                  line-height: 1.55;
                }
                .notice strong {
                  color: #0f1f33;
                }
                .steps {
                  display: grid;
                  grid-template-columns: repeat(2, minmax(0, 1fr));
                  gap: 16px;
                  align-items: stretch;
                  margin: 24px 0;
                }
                section {
                  display: flex;
                  flex-direction: column;
                  min-height: 100%;
                  padding: 24px;
                  border: 1px solid #d8e2ef;
                  border-radius: 8px;
                  background: #ffffff;
                  box-shadow: 0 12px 28px rgba(22, 39, 64, 0.08);
                }
                section h2 {
                  margin: 0 0 10px;
                  color: #0f1f33;
                  font-size: 24px;
                  letter-spacing: 0;
                }
                section p {
                  margin: 0 0 16px;
                  color: #405066;
                  line-height: 1.62;
                }
                ol {
                  margin: 0 0 20px;
                  padding-left: 22px;
                  color: #304056;
                }
                li {
                  margin: 8px 0;
                  line-height: 1.5;
                }
                .button-row {
                  margin-top: auto;
                }
                a.button,
                button.button {
                  display: inline-flex;
                  justify-content: center;
                  align-items: center;
                  width: 100%;
                  min-height: 52px;
                  padding: 14px 18px;
                  border: 0;
                  border-radius: 8px;
                  background: #0057b8;
                  color: #ffffff;
                  font: inherit;
                  font-weight: 800;
                  text-align: center;
                  text-decoration: none;
                  cursor: pointer;
                }
                .button.secondary {
                  background: #0d7d6c;
                }
                .form-grid {
                  display: grid;
                  gap: 12px;
                  margin: 4px 0 16px;
                }
                label {
                  display: grid;
                  gap: 6px;
                  color: #253246;
                  font-size: 14px;
                  font-weight: 700;
                }
                input,
                select {
                  width: 100%;
                  min-height: 46px;
                  padding: 10px 12px;
                  border: 1px solid #c8d2df;
                  border-radius: 8px;
                  background: #ffffff;
                  color: #17202a;
                  font: inherit;
                }
                .consent {
                  display: flex;
                  gap: 10px;
                  align-items: flex-start;
                  margin: 0 0 16px;
                  color: #405066;
                  font-size: 13px;
                  line-height: 1.5;
                }
                .consent input {
                  width: 18px;
                  min-height: 18px;
                  margin-top: 2px;
                }
                .manual {
                  margin: 12px 0 0;
                  color: #5b6675;
                  font-size: 13px;
                  line-height: 1.5;
                }
                .manual a {
                  color: #0057b8;
                  font-weight: 700;
                }
                .quick-links {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 10px;
                  margin: 8px 0 0;
                }
                .quick-links a {
                  min-width: 136px;
                  padding: 10px 12px;
                  border: 1px solid #c8d2df;
                  border-radius: 8px;
                  background: #ffffff;
                  color: #0057b8;
                  font-weight: 800;
                  text-align: center;
                  text-decoration: none;
                }
                footer {
                  padding: 12px 0 28px;
                  color: #5b6675;
                  font-size: 13px;
                  line-height: 1.6;
                }
                footer a {
                  color: #0057b8;
                }
                @media (max-width: 720px) {
                  body {
                    padding: 20px 12px;
                  }
                  header {
                    padding-top: 18px;
                  }
                  .steps {
                    grid-template-columns: 1fr;
                  }
                  section {
                    padding: 20px;
                  }
                }
              </style>
            </head>
            <body>
              <main>
                <header>
                  <p class="eyebrow">Uniport Beta</p>
                  <h1>Uniport Beta Test</h1>
                  <p class="lead">아래 버튼을 눌러 베타 테스트에 참여하세요. Android는 Firebase App Distribution으로 설치 절차를 바로 진행할 수 있고, iPhone은 Apple TestFlight 내부 테스트 초대가 필요합니다.</p>
                </header>

                <div class="notice">
                  <div>✓</div>
                  <div><strong>시연장 안내:</strong> 먼저 시연폰으로 앱을 체험한 뒤, 본인 기기에는 아래에서 기기에 맞는 방식으로 참여해 주세요.</div>
                </div>

                <div class="quick-links" aria-label="기기 선택">
                  <a href="#android">Android</a>
                  <a href="#ios">iPhone</a>
                </div>

                <div class="steps" id="steps">
                  <section id="android" data-platform="android">
                    <h2>Android 사용자</h2>
                    <p>Firebase App Distribution을 통해 베타 앱 설치 절차를 진행합니다.</p>
                    <ol>
                      <li>아래 버튼을 누르세요.</li>
                      <li>이메일을 등록하세요.</li>
                      <li>Firebase 초대 메일을 확인하세요.</li>
                      <li>Google 계정으로 로그인한 뒤 앱을 설치하세요.</li>
                    </ol>
                    <div class="button-row">
                      <a class="button" data-android-link="/beta/android" href="/beta/android">Android 베타 설치하기</a>
                    </div>
                    <p class="manual">설치가 막히면 현장 담당자에게 Firebase 초대 메일 수신 여부와 Google 계정 로그인 상태를 확인해 달라고 요청하세요.</p>
                  </section>

                  <section id="ios" data-platform="ios">
                    <h2>iPhone 사용자</h2>
                    <p>현재 iOS 버전은 TestFlight 내부 테스트로 운영 중입니다. Apple ID 이메일을 받아 순차적으로 초대합니다.</p>
                    <ol>
                      <li>아래 폼에 Apple ID 이메일을 입력하세요.</li>
                      <li>초대 메일을 받으면 App Store Connect 초대를 수락하세요.</li>
                      <li>TestFlight 앱을 설치하세요.</li>
                      <li>TestFlight에서 Uniport를 설치하세요.</li>
                    </ol>
                    <form id="ios-form">
                      <div class="form-grid">
                        <label>
                          이름/닉네임
                          <input id="name" name="name" autocomplete="name" placeholder="예: 김유니" required>
                        </label>
                        <label>
                          Apple ID 이메일
                          <input id="apple-email" name="appleEmail" type="email" autocomplete="email" placeholder="appleid@example.com" required>
                        </label>
                        <label>
                          연락처 이메일
                          <input id="contact-email" name="contactEmail" type="email" autocomplete="email" placeholder="연락 받을 이메일">
                        </label>
                        <label>
                          기기
                          <select id="device" name="device">
                            <option>iPhone</option>
                            <option>iPad</option>
                          </select>
                        </label>
                      </div>
                      <label class="consent">
                        <input id="consent" type="checkbox" required>
                        <span>입력한 Apple ID 이메일은 iOS TestFlight 내부 테스트 초대를 위해서만 사용됩니다.</span>
                      </label>
                      <button class="button secondary" type="submit">iPhone 베타 신청하기</button>
                    </form>
                    <p class="manual">메일 앱이 열리지 않으면 <a href="mailto:{{CONTACT_EMAIL}}">{{CONTACT_EMAIL}}</a>로 Apple ID 이메일을 보내주세요.</p>
                  </section>
                </div>

                <footer>
                  문의: <a href="mailto:{{CONTACT_EMAIL}}">{{CONTACT_EMAIL}}</a><br>
                  기기 감지가 틀렸다면 위의 Android 또는 iPhone 섹션을 직접 선택하세요.
                </footer>
              </main>

              <script>
                (function () {
                  const ua = navigator.userAgent.toLowerCase();
                  const isAndroid = ua.includes("android");
                  const isIos = /iphone|ipad|ipod/.test(ua);
                  const steps = document.getElementById("steps");
                  const android = document.getElementById("android");
                  const ios = document.getElementById("ios");

                  if (isAndroid && steps.firstElementChild !== android) {
                    steps.insertBefore(android, steps.firstElementChild);
                  } else if (isIos && steps.firstElementChild !== ios) {
                    steps.insertBefore(ios, steps.firstElementChild);
                  }

                  document.getElementById("ios-form").addEventListener("submit", function (event) {
                    event.preventDefault();
                    const name = document.getElementById("name").value.trim();
                    const appleEmail = document.getElementById("apple-email").value.trim();
                    const contactEmail = document.getElementById("contact-email").value.trim();
                    const device = document.getElementById("device").value;
                    const consent = document.getElementById("consent").checked;

                    if (!name || !appleEmail || !consent) {
                      event.currentTarget.reportValidity();
                      return;
                    }

                    const subject = "Uniport iOS 베타 신청";
                    const body = [
                      "이름/닉네임: " + name,
                      "Apple ID 이메일: " + appleEmail,
                      "연락처 이메일: " + (contactEmail || appleEmail),
                      "기기: " + device,
                      "동의: iOS TestFlight 내부 테스트 초대를 위한 Apple ID 이메일 사용에 동의"
                    ].join("\\n");

                    window.location.href = "mailto:{{CONTACT_EMAIL}}?subject=" + encodeURIComponent(subject) + "&body=" + encodeURIComponent(body);
                  });
                })();
              </script>
            </body>
            </html>
            """;

    private static final String ANDROID_INVITE_NOT_CONFIGURED_HTML = """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Uniport Android Beta</title>
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
                  padding: 32px;
                  border: 1px solid #d8dee8;
                  border-radius: 8px;
                  background: #ffffff;
                }
                h1 {
                  margin: 0 0 12px;
                  font-size: 28px;
                }
                p {
                  line-height: 1.6;
                }
                code {
                  padding: 2px 6px;
                  border-radius: 6px;
                  background: #eef2f7;
                }
                a {
                  color: #0057b8;
                }
              </style>
            </head>
            <body>
              <main>
                <h1>Android 베타 링크 설정 필요</h1>
                <p>Firebase App Distribution invite link가 아직 서버에 설정되지 않았습니다.</p>
                <p>배포 환경에 <code>APP_BETA_ANDROID_INVITE_URL</code> 값을 설정하면 이 주소는 Firebase 초대 링크로 바로 이동합니다.</p>
                <p>문의: <a href="mailto:{{CONTACT_EMAIL}}">{{CONTACT_EMAIL}}</a></p>
                <p><a href="/beta">베타 안내 페이지로 돌아가기</a></p>
              </main>
            </body>
            </html>
            """;

    private final String androidInviteUrl;
    private final String contactEmail;

    public BetaPageController(
            @Value("${app.beta.android-invite-url:}") String androidInviteUrl,
            @Value("${app.beta.contact-email:" + DEFAULT_CONTACT_EMAIL + "}") String contactEmail
    ) {
        this.androidInviteUrl = trimToEmpty(androidInviteUrl);
        this.contactEmail = trimToDefault(contactEmail, DEFAULT_CONTACT_EMAIL);
    }

    @GetMapping(value = {"/beta", "/beta.html", "/beta/ios"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getBetaPage() {
        return ResponseEntity.ok()
                .contentType(TEXT_HTML_UTF8)
                .body(withContactEmail(BETA_HTML));
    }

    @GetMapping(value = "/beta/android", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> redirectToAndroidInvite() {
        if (androidInviteUrl.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(TEXT_HTML_UTF8)
                    .body(withContactEmail(ANDROID_INVITE_NOT_CONFIGURED_HTML));
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(androidInviteUrl))
                .build();
    }

    private String withContactEmail(String html) {
        return html.replace("{{CONTACT_EMAIL}}", HtmlUtils.htmlEscape(contactEmail));
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToDefault(String value, String defaultValue) {
        String trimmed = trimToEmpty(value);
        return trimmed.isBlank() ? defaultValue : trimmed;
    }
}
