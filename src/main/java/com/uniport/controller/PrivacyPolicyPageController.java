package com.uniport.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PrivacyPolicyPageController {

    private static final MediaType TEXT_HTML_UTF8 = MediaType.parseMediaType("text/html;charset=UTF-8");

    private static final String PRIVACY_POLICY_HTML = """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Uniport 개인정보처리방침</title>
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
                  max-width: 820px;
                  margin: 0 auto;
                  background: #ffffff;
                  border: 1px solid #d8dee8;
                  border-radius: 8px;
                  padding: 32px;
                }
                h1 {
                  margin: 0 0 12px;
                  font-size: 28px;
                }
                h2 {
                  margin: 32px 0 12px;
                  font-size: 20px;
                }
                p, li {
                  line-height: 1.65;
                }
                ul, ol {
                  padding-left: 22px;
                }
                a {
                  color: #0057b8;
                }
                .effective-date {
                  margin: 0 0 24px;
                  color: #5b6675;
                }
                @media (max-width: 520px) {
                  main {
                    padding: 24px;
                  }
                }
              </style>
            </head>
            <body>
              <main>
                <h1>Uniport 개인정보처리방침</h1>
                <p class="effective-date">시행일: 2026년 5월 20일</p>
                <p>Uniport는 모의투자, 투자 학습, 그룹 투자 토론, 친구 초대, 알림 기능을 제공하는 모바일 앱입니다. Uniport는 서비스 제공에 필요한 범위에서 개인정보와 앱 이용 데이터를 처리하며, 사용자 개인정보를 데이터 브로커에게 판매하지 않습니다.</p>

                <h2>1. 수집하는 정보</h2>
                <p>Uniport는 서비스 이용 과정에서 다음 정보를 수집하거나 저장할 수 있습니다.</p>
                <ul>
                  <li>계정 정보: 사용자 ID, Firebase UID, 닉네임, 이메일, 프로필 이미지 URL, 로그인 제공자 정보</li>
                  <li>소셜 로그인 정보: Google, Kakao, Apple 로그인 인증 결과 및 인증 토큰</li>
                  <li>앱 프로필 정보: 자기소개, 선택 캐릭터, 푸시 알림 설정</li>
                  <li>온보딩 및 학습 정보: 투자 성향 설문 답변, 투자 성향 결과, 관심 섹터, 학습 진도, 퀴즈 답변, 포인트와 경험치</li>
                  <li>모의투자 정보: 보유 종목, 주문 내역, 총자산, 투자 원금, 손익, 수익률</li>
                  <li>그룹 및 커뮤니티 정보: 매칭방 참여 정보, 채팅 메시지, 투표와 찬반 기록, 게시글, 댓글, 좋아요, 신고, 친구 관계, 초대 코드</li>
                  <li>포인트샵 정보: 포인트 잔액, 교환 내역, 사용 포인트, 교환 상태</li>
                  <li>알림 정보: 푸시 알림 토큰, 기기 플랫폼, 알림 권한 상태, 토큰 활성 여부</li>
                  <li>로컬 저장 정보: 로그인 세션, 인증 토큰, 온보딩 완료 여부</li>
                </ul>
                <p>Uniport는 현재 위치, 카메라, 마이크, 연락처, 사진/동영상 접근 권한을 사용하지 않습니다.</p>

                <h2>2. 정보를 사용하는 목적</h2>
                <p>Uniport는 수집한 정보를 다음 목적으로 사용합니다.</p>
                <ul>
                  <li>회원 가입, 로그인, 자동 로그인, 계정 보안</li>
                  <li>모의투자, 그룹 투자, 채팅, 투표, 커뮤니티 기능 제공</li>
                  <li>투자 성향 분석, 학습 진도 저장, 사용자 맞춤 화면 제공</li>
                  <li>친구 초대, 알림 발송, 포인트샵 교환 처리</li>
                  <li>사용자 요청 처리, 계정 삭제, 고객 지원</li>
                  <li>서비스 안정성 유지, 부정 이용 방지, 서버 보안</li>
                </ul>

                <h2>3. 제3자 서비스</h2>
                <p>Uniport는 앱 기능 제공을 위해 다음 외부 서비스를 사용할 수 있습니다.</p>
                <ul>
                  <li>Google Firebase: 인증, 서버 함수, 푸시 알림, 서버 관리 기능</li>
                  <li>Google Sign-In: Google 계정 로그인</li>
                  <li>Apple Sign In: Apple 계정 로그인</li>
                  <li>Kakao SDK: Kakao 로그인, 사용자 정보 확인, KakaoTalk 공유</li>
                  <li>Uniport 백엔드 서버: 계정, 모의투자, 채팅, 커뮤니티, 학습, 포인트샵 데이터 처리</li>
                  <li>TradingView Lightweight Charts 및 jsDelivr CDN: 앱 내 주식 차트 표시</li>
                  <li>OpenAI API 또는 FinBERT 기반 분석 서비스: 투자/그룹 피드백 기능이 활성화된 경우 분석 결과 생성</li>
                  <li>Google AdMob: 앱 내 광고 표시, 광고 제공 및 성과 측정</li>
                </ul>
                <p>Uniport는 Google AdMob을 통해 앱 내 광고를 표시할 수 있습니다. 광고 제공 과정에서 광고 식별자, 기기 정보, 앱 활동 정보 등이 Google에 의해 처리될 수 있습니다. Uniport는 사용자 개인정보를 데이터 브로커에게 판매하지 않습니다.</p>

                <h2>4. 푸시 알림</h2>
                <p>Uniport는 사용자가 알림을 허용한 경우 푸시 알림을 보낼 수 있습니다. 알림에는 채팅, 투표, 친구 초대, 그룹 활동 등 서비스 이용과 관련된 내용이 포함될 수 있습니다.</p>
                <p>사용자는 기기 설정 또는 앱 내 설정에서 알림을 끌 수 있습니다. 알림을 끄면 Uniport는 푸시 토큰을 비활성화하거나 삭제 요청을 처리합니다.</p>

                <h2>5. 로컬 저장 및 인증 토큰</h2>
                <p>Uniport는 로그인 유지와 앱 상태 저장을 위해 기기 내 저장소에 인증 세션, 인증 토큰, 온보딩 완료 여부 등을 저장할 수 있습니다. 이 정보는 계정 인증, 자동 로그인, 앱 기능 제공에 사용됩니다.</p>

                <h2>6. 보관 및 삭제</h2>
                <p>Uniport는 서비스 제공에 필요한 기간 동안 정보를 보관합니다. 사용자가 계정 삭제를 요청하면 Uniport는 계정 정보와 주요 서비스 데이터를 삭제합니다.</p>
                <p>계정 삭제 시 삭제 대상에는 계정 정보, 인증 계정, 보유 종목, 주문, 친구 관계, 포인트, 푸시 토큰, 학습 상태 등이 포함될 수 있습니다. 다만 서비스 무결성, 분쟁 대응, 보안, 법적 의무 이행을 위해 일부 기록은 필요한 기간 동안 보관되거나 사용자 식별이 어렵도록 처리될 수 있습니다.</p>
                <p>사용자는 앱 내 계정 삭제 기능, 스토어 개발자 연락처, 또는 앱 내 문의 채널을 통해 데이터 삭제를 요청할 수 있습니다.</p>

                <h2 id="account-deletion">계정 삭제 안내</h2>
                <p>사용자는 Uniport 앱에서 직접 계정을 삭제할 수 있습니다.</p>
                <p>계정 삭제 방법:</p>
                <ol>
                  <li>Uniport 앱을 실행합니다.</li>
                  <li>계정에 로그인합니다.</li>
                  <li>마이페이지로 이동합니다.</li>
                  <li>설정을 엽니다.</li>
                  <li>회원 탈퇴 또는 계정 삭제를 선택합니다.</li>
                  <li>안내를 확인한 후 계정 삭제를 완료합니다.</li>
                </ol>
                <p>계정이 삭제되면 계정 정보, 인증 계정, 프로필 정보, 온보딩 설문 정보, 학습 상태, 보유 종목, 주문 내역, 친구 관계, 그룹 참여 정보, 푸시 토큰 등 계정과 연결된 주요 데이터가 삭제되거나 익명화됩니다.</p>
                <p>다만 서비스 무결성, 보안, 부정 이용 방지, 분쟁 대응 또는 법적 의무 이행을 위해 필요한 일부 기록은 제한된 기간 동안 보관될 수 있습니다.</p>
                <p>앱에 접근할 수 없어 계정 삭제가 어려운 경우, 사용자는 앱 스토어에 표시된 개발자 연락처 또는 아래 이메일을 통해 계정 삭제를 요청할 수 있습니다.</p>

                <h2>7. 데이터 보안</h2>
                <p>Uniport는 사용자 데이터를 보호하기 위해 HTTPS 등 암호화된 통신을 사용합니다. 또한 인증 토큰을 통해 서버 API 접근을 보호하고, 필요한 범위에서만 사용자 데이터를 처리합니다.</p>

                <h2>8. 광고 및 추적</h2>
                <p>Uniport는 Google AdMob을 통해 광고를 표시할 수 있습니다. 광고 제공 및 성과 측정을 위해 Google이 광고 식별자, 기기 정보, 앱 활동 정보를 처리할 수 있습니다. Uniport는 사용자 개인정보를 데이터 브로커에게 판매하지 않습니다.</p>

                <h2>9. 아동의 개인정보</h2>
                <p>Uniport는 아동을 대상으로 설계된 서비스가 아닙니다. 관련 법령상 보호가 필요한 사용자의 개인정보가 수집된 사실을 알게 되는 경우, 필요한 조치를 취합니다.</p>

                <h2>10. 개인정보처리방침 변경</h2>
                <p>Uniport는 서비스 변경, 법령 변경, 스토어 정책 변경에 따라 이 개인정보처리방침을 수정할 수 있습니다. 중요한 변경이 있는 경우 앱 또는 공개 페이지를 통해 안내합니다.</p>

                <h2>11. 문의</h2>
                <p>개인정보 처리, 계정 삭제, 데이터 삭제 요청에 대한 문의는 앱 내 문의 채널 또는 앱 스토어에 표시된 개발자 연락처를 통해 접수할 수 있습니다.</p>
                <p>이메일: <a href="mailto:kwakkun2002@gmail.com">kwakkun2002@gmail.com</a><br>전화: <a href="tel:+821066345516">010-6634-5516</a></p>
              </main>
            </body>
            </html>
            """;

    @GetMapping(value = {"/privacy", "/privacy.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getPrivacyPolicyPage() {
        return ResponseEntity.ok()
                .contentType(TEXT_HTML_UTF8)
                .body(PRIVACY_POLICY_HTML);
    }
}
