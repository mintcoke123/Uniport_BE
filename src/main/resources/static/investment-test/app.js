(function () {
  "use strict";

  var app = document.getElementById("app");
  var reservationEndpoint = "/api/investment-test/reservations";

  var state = {
    step: "welcome",
    name: "",
    contact: "",
    consent: false,
    answers: {},
    interests: [],
    resultKey: null,
    reservation: {
      status: "idle",
      message: ""
    }
  };

  var questions = [
    {
      id: "risk",
      title: "1주일 사이 100만원이\n<strong>95만원(-5%) 으로 떨어졌어</strong>",
      subtitle: "나랑 제일 가까운 반응은?",
      icon: ["✋", "⌕", "⌁"],
      options: [
        { label: "바로 팔고 다시는 안 산다", sub: "스트레스 받아서 못견디겠어", score: { turtle: 3, panda: 1 } },
        { label: "이유 찾아보고 더 지켜본다", sub: "일시적인 하락일 수도 있잖아?", score: { researcher: 2, panda: 2, farmer: 1, owl: 1 } },
        { label: "원래 그럴 수 있다 그냥 둔다", sub: "장기적으로 보면 오를 거야", score: { farmer: 2, turtle: 1, surfer: 1 } }
      ]
    },
    {
      id: "check",
      title: "가격 변동을\n얼마나 자주 확인하는 편이야?",
      subtitle: "내가 편한 확인 주기는?",
      icon: ["◷", "◴", "↯"],
      options: [
        { label: "자주 보면 불안해서 싫다", sub: "가끔 확인하는게 마음 편해요", score: { turtle: 2, farmer: 2 } },
        { label: "하루 한 번 정도 OK", sub: "마감 시황 정도는 챙겨봐요", score: { panda: 3, researcher: 1, owl: 1 } },
        { label: "수시로 보는 것도 재밌다", sub: "시장의 흐름을 놓치고 싶지 않아요", score: { cheetah: 2, surfer: 2, dolphin: 1 } }
      ]
    },
    {
      id: "term",
      title: "이 앱에서 투자기간를\n어느 정도 기간 가져가고 싶어?",
      subtitle: "목표 기간을 골라줘",
      icon: ["1Y", "3Y", "10Y"],
      options: [
        { label: "1년 안에 결과 보고 싶다", sub: "단기 목표 달성형", score: { cheetah: 2, surfer: 2 } },
        { label: "3~5년 정도면 괜찮다", sub: "중기 성장 추구형", score: { panda: 2, dolphin: 2 } },
        { label: "10년 이상 길게도 OK", sub: "장기 가치 투자형", score: { turtle: 2, farmer: 3 } }
      ]
    },
    {
      id: "style",
      title: "나는 이런 식으로\n투자 결정을 내리고 싶어",
      subtitle: "나의 투자 스타일은?",
      icon: ["#", "✦", "⌁"],
      options: [
        { label: "숫자/재무제표/지표 보고", sub: "기업의 내재 가치를 분석해서", score: { owl: 4, researcher: 2, turtle: 1, panda: 1 } },
        { label: "뉴스/스토리/트렌드 보고", sub: "미래 성장 가능성을 예측해서", score: { dolphin: 3, cheetah: 1 } },
        { label: "차트/가격 흐름 보고", sub: "시장의 흐름과 타이밍을 잡아서", score: { surfer: 3, cheetah: 1 } }
      ]
    },
    {
      id: "experience",
      title: "투자 해본 경험은 \n어느 정도야?",
      subtitle: "지금의 나와 가까운 쪽은?",
      icon: ["0", "₩", "↑"],
      options: [
        { label: "완전 처음", sub: "계좌도 거의 안 써봄", score: { turtle: 2, farmer: 1 } },
        { label: "가끔 해봄", sub: "소액으로 몇 번 해봤다", score: { panda: 2, dolphin: 1 } },
        { label: "익숙함", sub: "1년 이상 꾸준히 해봤다", score: { owl: 2, researcher: 1, surfer: 2, cheetah: 1 } }
      ]
    }
  ];

  var keywords = ["AI 반도체", "로봇", "방산", "자율주행", "양자컴퓨터", "2차전지", "전력기기", "바이오", "원전", "우주/로켓"];

  var profiles = {
    turtle: {
      title: "조심스러운 거북이형",
      sticker: "안정형 거북이 스티커",
      summary: "원금은 지키면서 천천히 배우고 싶은\n장기형 투자자시네요!",
      image: "/investment-test/assets/turtle.png",
      mark: "거",
      gradient: ["#48722A", "#152D04"],
      card: "#E1FACE",
      border: "#D4E5D8",
      titleColor: "#CBFDA9",
      soft: "#ACD093",
      ink: "#425647",
      traits: ["손실에 대한 스트레스가 큰 편|큰 변동보다는 예측 가능한 흐름을 선호해요", "투자에서 가장 중요한 기준은 잃지 않는 것!|이득보다 손실을 더 크게 느껴요", "적립식, 분산, 장기보유 같은 구조에 강점|복잡한 매매 전략보다는 이런게 좋아", "꾸준하고 안정적인 복리 흐름을 선호해요|수익률이 폭발적이지 않아도 괜찮아"],
      principles: ["한 종목에 몰빵 금지 (최대 20%)|총자산의 20% 이상은 넣지 마세요", "이해하지 못한 상품은 투자 금지|수익률이 좋아보여도 경계하세요", "시장 급락 시 감정적인 매매 금지|내 투자 원칙과 비중부터 다시 점검해보세요", "수익보다는 손실 관리가 먼저!|기준을 유지하는 것이 중요해요"],
      strategies: ["인덱스 ETF, 대형 우량주, 배당주 중심의 코어 자산을 우선 구축한다", "적립식 투자 비중을 높여 진입 타이밍 스트레스를 줄인다", "분기 또는 반기 단위로만 리밸런싱하며 일간 변동 대응은 최소화한다", "섹터 투자에 관심이 있어도 전체 자산의 일부만 위성 포지션으로 가져간다"]
    },
    cheetah: {
      title: "호기심 많은 치타형",
      sticker: "스피드 치타 스티커",
      summary: "수익ㆍ트렌드에 끌리는\n공격형 입문자시네요!",
      image: "/investment-test/assets/cheetah.png",
      mark: "치",
      gradient: ["#FF8A31", "#7A2700"],
      card: "#FFF0D8",
      border: "#FFD29B",
      titleColor: "#FFE1B8",
      soft: "#FFD7A6",
      ink: "#65421F",
      traits: ["뜨거운 흐름을 감지하는 속도가 빨라요|트렌드와 기회를 빠르게 포착하는 공격 성향의 투자자", "기회를 놓치기 싫어하는 편|의사결정이나 실행 속도가 압도적으로 빨라요", "추격매수와 충동매매에 취약해요|기준을 가지고 투자해야 합니다", "규칙이 생기면 실력이 빠르게 성장하는 타입|잠재력이 뛰어난 투자 성향 중 하나에요!"],
      principles: ["손절가와 익절 기준을 먼저 정하기|기준을 가지고 진입하세요", "테마주와 고변동 종목은 일부만 사용!|몰빵하는 습관 버리기", "뉴스가 뜨면, 가격 반영 여부부터 확인|바로 따라가는 태도는 지양하세요", "손실 한도를 넘으면 즉시 속도 줄이기|이 정도의 제동은 반드시 필요해요"],
      strategies: ["테마 스윙 투자나 이벤트 드리븐 전략을 사용한다", "진입과 청산 기준을 먼저 정한다", "포지션 크기를 작게 나눠 여러 번 진입한다", "매매일지를 통해 규칙 준수 여부를 복기한다"]
    },
    panda: {
      title: "균형잡힌 판다형",
      sticker: "밸런스 판다 스티커",
      summary: "성장성과 안정성 사이에서\n균형을 찾는 타입의 투자자시네요!",
      image: "/investment-test/assets/panda.png",
      mark: "판",
      gradient: ["#64748B", "#1F2937"],
      card: "#EEF2F7",
      border: "#D7DEE8",
      titleColor: "#F8FAFC",
      soft: "#CBD5E1",
      ink: "#334155",
      traits: ["손실 관리와 성장 기회를 동시에 노리는 성향|두 마리 토끼를 잡아야 하는 편", "숫자와 스토리를 함께 보며 판단해요|균형 있는 판단 기준의 소유자", "꾸준히 개선되는 포트폴리오 운영에 적합|큰 승부보다는 차근차근 관리하는 쪽이 유리!", "장기적으로 안정적인 실행력을 내기 쉬운 타입|꾸준함이라는 가장 강력한 무기를 가진 분이네요"],
      principles: ["코어 자산 중심의 포트폴리오 구성|꾸준한 투자의 비결이 되어줍니다", "매수 전, 찬/반 근거 적어보기|스스로 납득할 수 있는 투자 이유를 명확히 할 것", "관심 섹터는 자산의 일부일 뿐|트렌드 투자는 비중 조절이 핵심이에요", "주기적으로 포트폴리오의 비중 확인|자산이 한 곳에 쏠리지 않도록 관리하세요"],
      strategies: ["코어 70/위성 30 구조로 포트폴리오를 설계한다", "코어는 인덱스 ETF, 대형 우량주, 배당주로 구성한다", "위성은 관심 섹터나 성장주로 채운다", "월 1회 또는 분기 1회 포트폴리오 점검 기준을 정한다"]
    },
    dolphin: {
      title: "감각형 돌고래형",
      sticker: "인사이트 돌고래 스티커",
      summary: "스토리나 뉴스 흐름을 따라가는 성장형 투자자시네요!",
      image: "/investment-test/assets/dolphin.png",
      mark: "돌",
      gradient: ["#22B8CF", "#075985"],
      card: "#DDFBFF",
      border: "#B8EEF7",
      titleColor: "#CFFAFE",
      soft: "#A5F3FC",
      ink: "#155E75",
      traits: ["변화의 흐름에 자연스럽게 눈길이 가는 타입|앞으로 커질 시장과 변화를 만드는 기업을 빠르게 캐치해요", "혁신성과 스토리에 민감해요|데이터나 숫자 이면의 서사에 관심이 많으시군요", "검증하는 습관을 들일 필요가 있어요|좋은 이야기와 좋은 투자 기회는 다를 수 있어요", "비교적 긴 호흡으로 보유하는 성향|긴 안목으로 투자를 이어가는 장기형 투자자"],
      principles: ["스토리만 보지 말 것!|숫자로 실제 사업 성과를 한 번 더 검증하세요", "같은 섹터라도 종목을 분산할 것|성격이 다른 종목들로 골고루 채워보세요", "스토리와 실제 사업 성과는 구분할 것|유행에 휩쓸리지 않도록 조심하세요", "불장일수록 비중 관리는 필수|과열 구간에서 나도 모르게 무리할 수 있어요"],
      strategies: ["성장주 또는 혁신 섹터를 보되, 매출 성장률과 밸류에이션을 함께 확인한다", "좋은 이야기가 실제 실적과 지표로 연결되는지 점검한다", "대표 종목과 보조 종목으로 나눠 비중을 배분한다", "실적 발표, 가이던스, 시장 점유율 변화를 체크 포인트로 둔다"]
    },
    owl: {
      title: "전략짜는 올빼미형",
      sticker: "전략 올빼미 스티커",
      summary: "공부와 분석을 즐길 줄 아는\n자기주도형 투자자시네요!",
      image: "/investment-test/assets/owl.png",
      mark: "올",
      gradient: ["#2179BE", "#0F549D"],
      card: "#DFF1FF",
      border: "#BADEFA",
      surface: "#EFF6FE",
      titleColor: "#DFF1FF",
      soft: "#7DAACA",
      body: "#699EE3",
      ink: "#326BB4",
      traits: ["감보다 근거를 우선하는 투자자에요|근거가 있어 심리적으로 흔들릴 가능성이 적어요!", "숫자로 설명되는 것에만 투자해요|근거 없이는 발도 들이지 않는 편", "내가 이해한 투자에는 높은 확신이 있어요|매수 전에 오래 고민하는 스타일"],
      principles: ["설명할 수 있는 사업에만 투자|수익률이 좋아보여도 경계하세요", "핵심 가설과 반증 조건을 글로 남기기|매수 전에 반드시 해야할 습관이에요", "질 좋은 사업인지 함께 확인할 것|단순히 싼 가격만 보는 건 의미 없어요", "가설 검증을 우선할 것|감정적 대응은 자제하세요"],
      strategies: ["종목 선정 전 1 페이지 정도 리서치 노트를 작성한다", "벨류에이션이 매력적인 구간에서 분할 매수한다", "실적 발표, 가이던스, 산업 규제 변화를 핵심 체크 포인트로 관리한다", "반증 조건을 미리 정하고, 틀렸을 때 나오는 기준을 만든다"]
    },
    researcher: {
      title: "호기심 많은 연구자형",
      sticker: "분석 연구자 스티커",
      summary: "섹터나 기업 공부 자체를\n즐길 줄 아는 연구형 투자자시네요!",
      image: "/investment-test/assets/researcher.png",
      mark: "연",
      gradient: ["#6D5DF6", "#26135E"],
      card: "#EFEAFF",
      border: "#D8D0FF",
      titleColor: "#EDE9FE",
      soft: "#DDD6FE",
      ink: "#4C3A82",
      traits: ["비즈니스의 큰 그림을 읽는 편|산업 구조와 기업 차별점을 이해하는 과정 자체를 즐기시네요", "‘왜 그럴까?’ 이유를 깊게 파고 들어요|데이터나 숫자 이면의 서사에 관심이 많으시군요", "리서치는 다다익선|투자자님의 확신과 보유 지속력을 높여줄거에요", "핵심 변수를 구분하는 능력이 중요해요|정보가 많아질 수록 꼭 필요한 역량입니다"],
      principles: ["진입 전, 꼼꼼한 개념 정리부터!|핵심 키워드, 대표 기업, 체크 지표 먼저 정리", "내 가설부터 정리해볼 것|남의 의견을 보기 전에 적어보세요", "분산 원칙은 끝까지 유지하세요|아는 것이 다가 아니다", "반증 조건을 항상 함께 관리해요|내 예측이 빗나갔음을 대비할 기준이 필요합니다"],
      strategies: ["관심 섹터를 정하면 핵심 키워드, 대표 기업, 체크 지표를 먼저 정리한다", "기업 리서치 노트에 투자 가설과 반증 조건을 구조화한다", "포트폴리오를 늘릴 것, 유지할 것, 줄일 것, 버릴 것으로 재분류한다", "산업 변화와 기업 경쟁력 변화를 핵심 판단 기준으로 둔다"]
    },
    farmer: {
      title: "성실한 농부형",
      sticker: "꾸준함 농부 스티커",
      summary: "배당ㆍ우량주ㆍETF를 꾸준히 모으는\n적립식 장기형 투자자시네요!",
      image: "/investment-test/assets/farmer.png",
      mark: "농",
      gradient: ["#7A9F35", "#2F3D10"],
      card: "#F0F7D7",
      border: "#DDE8A8",
      titleColor: "#ECFCCB",
      soft: "#D9F99D",
      ink: "#4D5D1D",
      traits: ["타이밍보다는 꾸준함을 믿는 편|타이밍의 유혹을 이겨내는 우직한 마라토너", "적립식, 장기 보유, 재투자 구조와 잘 맞아요|복리의 마법을 믿는 정석 투자자", "일정과 규칙을 지키는 실행력이 강점|감정적 매매 따윈 없어", "시간과 습관으로 성과를 쌓아요|매일의 투자 습관이 가장 강력한 무기"],
      principles: ["내가 정한 적립일은 지키기|시장 상황과는 무관하게 지켜야 할 것!", "장기 보유 자산도 분기별 점검 필요|최소 분기 1회는 점검하세요", "투자로 번 돈은 다시 투자의 씨앗으로|배당금 및 현금 흐름은 소비 대신 재투자로 활용", "복리는 지속성에서 나온다|속도에 휘둘리지 마세요"],
      strategies: ["월간 자동매수와 정기 리밸런싱을 기본 전략으로 둔다", "ETF, 우량주, 배당주 중심으로 구성한다", "장기 목표를 3년, 5년, 10년 단위로 수치화한다", "배당금이나 추가 현금을 재투자하는 구조를 우선 고려한다"]
    },
    surfer: {
      title: "파도타는 서퍼형",
      sticker: "웨이브 서퍼 스티커",
      summary: "차트나 가격 흐름을 중심으로\n트레이딩하는 투자자시네요!",
      image: "/investment-test/assets/surfer.png",
      mark: "서",
      gradient: ["#2563EB", "#0F172A"],
      card: "#E5F0FF",
      border: "#BDD7FF",
      titleColor: "#DBEAFE",
      soft: "#BFDBFE",
      ink: "#1D4E89",
      traits: ["짧은 흐름과 타이밍에서 기회를 찾는 성향|시장이라는 작은 파도에서 빠르게 이익을 챙기는 데 능숙해요", "빠른 대응력이 장점|예상치 못한 변화에도 끄떡 없어요", "심리적 흔들림에 크게 영향을 받는 편|충동적인 매매를 하지 않도록 주의할 것", "손실 제한 능력이 성과를 좌우해요|공격형 투자자에게는 꼭 필요해요"],
      principles: ["진입 전에 나만의 손절가 정하기|손절가를 정하지 않은 매매는 하지 않는다", "일일 손실 한도 초과 시, 매매 중단|무리하게 만회하지 말고, 과감하게 중단하세요", "연속 손실이 나면 패턴부터 복기한다|무작정 뛰어들기보다, 내 투자 습관을 돌아봐요", "투자의 제 1원칙은 생존!|내 자산을 안전하게 지키는 것을 최우선하세요"],
      strategies: ["진입 기준, 손절 기준, 익절 기준을 명확히 둔다", "‘하루 손실 한도’와 ‘연속 손실 시 매매 중단 규칙’을 만든다", "매매 전 시나리오를 짧게라도 적는다", "장중 소음에 휘둘리지 않도록 사용하는 지표와 시간 프레임을 고정한다"]
    }
  };

  var resultOrder = ["turtle", "panda", "cheetah", "owl", "dolphin", "surfer", "farmer", "researcher"];
  var classifierProfiles = {
    turtle: { id: 1, risk: 1.0, term: 3.0, style: 1.0, involvement: 1.0 },
    panda: { id: 2, risk: 2.0, term: 2.6, style: 1.7, involvement: 2.0 },
    cheetah: { id: 3, risk: 3.0, term: 1.6, style: 2.3, involvement: 2.0 },
    owl: { id: 4, risk: 2.4, term: 2.2, style: 1.0, involvement: 3.0 },
    dolphin: { id: 5, risk: 2.4, term: 2.2, style: 2.0, involvement: 2.6 },
    surfer: { id: 6, risk: 3.0, term: 1.4, style: 3.0, involvement: 3.0 },
    farmer: { id: 7, risk: 1.7, term: 3.0, style: 1.0, involvement: 2.0 },
    researcher: { id: 8, risk: 2.2, term: 2.8, style: 1.6, involvement: 3.0 }
  };
  var questionSteps = questions.map(function (_, index) { return "q" + index; });
  var stepOrder = ["welcome", "profile", "start"].concat(questionSteps, ["interests", "intro", "result", "analysis", "principles", "strategy", "complete"]);

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  function currentIndex() {
    return stepOrder.indexOf(state.step);
  }

  function canGoBack() {
    return currentIndex() > 0 && state.step !== "complete";
  }

  function setStep(step) {
    state.step = step;
    render();
  }

  function goBack() {
    var index = currentIndex();
    if (index > 0) {
      state.step = stepOrder[index - 1];
      render();
    }
  }

  function progressDots(active, total) {
    var dots = "";
    for (var i = 0; i < total; i += 1) {
      dots += '<span class="dot' + (i === active ? " active" : "") + '"></span>';
    }
    return '<div class="progress" aria-label="' + total + "단계 중 " + (active + 1) + '단계">' + dots + "</div>";
  }

  function topbar(active, total, color) {
    return '<div class="topbar" style="color:' + (color || "#1F2024") + '">' +
      '<button class="icon-button" type="button" data-action="back" aria-label="이전"' + (canGoBack() ? "" : " disabled") + ">‹</button>" +
      (typeof active === "number" ? progressDots(active, total) : "<span></span>") +
      '<span></span>' +
      "</div>";
  }

  function bottomButton(label, disabled, action, secondary) {
    return '<div class="bottom-bar"><button class="cta' + (secondary ? " secondary" : "") + '" type="button" data-action="' + action + '"' + (disabled ? " disabled" : "") + ">" + label + "</button></div>";
  }

  function renderWelcome() {
    app.innerHTML = '<section class="screen">' +
      '<div class="content">' +
      '<div class="hero-badge">U</div>' +
      '<p class="eyebrow">Uniport Festival Test</p>' +
      '<h1 class="title">유니포트에 오신 걸\n환영해요!</h1>' +
      '<p class="subtitle">1-2분이면 나의 첫 투자 성향과\n스티커 타입을 확인할 수 있어요.</p>' +
      "</div>" +
      bottomButton("다음", false, "next") +
      "</section>";
  }

  function renderProfileForm() {
    app.innerHTML = '<section class="screen">' +
      topbar(null, null) +
      '<div class="content top">' +
      '<h1 class="title">당신의 이름을\n알려주세요!</h1>' +
      '<p class="subtitle">테스트 결과 저장과 출시 알림을 위해\n연락처와 동의가 필요해요.</p>' +
      '<div class="form-stack">' +
      '<div class="field"><label for="name">이름</label><input id="name" class="text-field" maxlength="10" value="' + escapeHtml(state.name) + '" placeholder="최대 10자 이내"><p class="helper">최대 10자 이내</p></div>' +
      '<div class="field"><label for="contact">휴대폰 번호 또는 이메일</label><input id="contact" class="text-field" value="' + escapeHtml(state.contact) + '" placeholder="010-1234-5678 또는 uniport@email.com"></div>' +
      '<label class="consent-card"><input id="consent" type="checkbox"' + (state.consent ? " checked" : "") + '><span>Uniport 공식 출시 및 이벤트 안내를 위해 이름과 연락처, 테스트 결과를 저장하는 데 동의합니다.</span></label>' +
      '<p class="error-text" id="form-error"></p>' +
      "</div></div>" +
      bottomButton("다음", false, "profileNext") +
      "</section>";
  }

  function renderStart() {
    app.innerHTML = '<section class="screen">' +
      topbar(0, 7) +
      '<div class="content">' +
      '<div class="intro-card"><div class="note-mark">✓</div><h1 class="title">진단 테스트를\n시작할게요</h1><p class="subtitle">정답은 없어요. 가장 편한 선택지를 골라주세요.</p></div>' +
      "</div>" +
      bottomButton("다음", false, "next") +
      "</section>";
  }

  function renderQuestion(questionIndex) {
    var question = questions[questionIndex];
    var selected = state.answers[question.id];
    var cards = question.options.map(function (option, index) {
      var selectedClass = selected === index ? " selected" : "";
      return '<button class="option-card' + selectedClass + '" type="button" data-action="answer" data-question="' + question.id + '" data-index="' + index + '">' +
        '<span class="option-icon">' + question.icon[index] + "</span>" +
        '<span><span class="option-main">' + option.label + '</span><span class="option-sub">' + option.sub + "</span></span>" +
        "</button>";
    }).join("");

    app.innerHTML = '<section class="screen">' +
      topbar(questionIndex + 1, 7) +
      '<div class="content">' +
      '<h1 class="question-title">' + question.title + '</h1>' +
      '<p class="subtitle">' + question.subtitle + '</p>' +
      '<div class="option-list">' + cards + "</div>" +
      "</div>" +
      bottomButton("다음", selected === undefined, "next") +
      "</section>";
  }

  function renderInterests() {
    var cards = keywords.map(function (keyword) {
      var selected = state.interests.indexOf(keyword) >= 0;
      return '<button class="interest-card' + (selected ? " selected" : "") + '" type="button" data-action="interest" data-keyword="' + escapeHtml(keyword) + '">' + keyword + "</button>";
    }).join("");

    app.innerHTML = '<section class="screen">' +
      topbar(6, 7) +
      '<div class="content top">' +
      '<h1 class="question-title">현재 가장 관심 있는\n투자분야가 있다면?</h1>' +
      '<p class="subtitle">관심 있는 키워드를 모두 선택해주세요. (최대 2개)</p>' +
      '<div class="interest-grid">' + cards + "</div>" +
      '<p class="error-text" id="interest-error"></p>' +
      "</div>" +
      bottomButton("진단 결과 보기", state.interests.length === 0, "showIntro") +
      "</section>";
  }

  function renderIntro() {
    var name = escapeHtml(state.name || "유니포트");
    app.innerHTML = '<section class="screen">' +
      topbar(null, null) +
      '<div class="content">' +
      '<div class="intro-card"><div class="note-mark">N</div><p class="eyebrow">진단 결과 보기</p>' +
      '<h1 class="title">' + name + '<span class="title-tail">님의</span>\n첫 투자 노트가 생성되었어요</h1>' +
      '<p class="subtitle">결과와 함께 사전등록 저장을 진행할게요.</p></div>' +
      "</div>" +
      bottomButton("결과 확인", false, "result") +
      "</section>";
  }

  function renderResultPage(kind) {
    var profile = profiles[state.resultKey] || profiles.turtle;
    var vars = profileVars(profile);
    var notice = reservationNotice();
    var content = "";
    var action = "next";
    var button = "다음";
    var active = null;

    if (kind === "result") {
      active = 0;
      content = '<div class="result-header"><p class="result-owner">' + escapeHtml(state.name || "유니포트") + '님의 투자성향은</p><h1 class="result-title">' + profile.title + '</h1></div>' +
        '<div class="profile-card"><div class="profile-brand">UNIPORT</div><h2 class="profile-name">' + profile.title + '</h2><p class="profile-summary">' + profile.summary + '</p><div class="level-pill">Lv.1</div><div class="mascot">' + mascotMarkup(profile) + '</div><div class="sticker-label">' + profile.sticker + '</div></div>';
    } else if (kind === "analysis") {
      active = 1;
      content = resultSection("성향 분석", "내 투자 캐릭터는?", profile.traits);
    } else if (kind === "principles") {
      active = 2;
      content = resultSection("투자 원칙", "처음부터 지키면 좋은 기준", profile.principles);
    } else if (kind === "strategy") {
      active = 3;
      content = resultSection("추천 전략", "나에게 맞는 첫 포트폴리오", profile.strategies);
    } else {
      active = 4;
      button = "30일 투자 공부 하러가기";
      action = "restart";
      content = '<div class="result-section"><p class="section-kicker">Diagnosis</p><h1 class="section-title">Complete</h1><div class="info-card"><ul class="bullet-list"><li>' + profile.sticker + '</li><li>행사 스태프에게 이 화면을 보여주세요.</li><li>관심 키워드: ' + state.interests.map(escapeHtml).join(", ") + "</li></ul></div></div>";
    }

    app.innerHTML = '<section class="screen result" style="' + vars + '">' +
      topbar(active, 5, "rgba(255,255,255,0.92)") +
      '<div class="content top">' + content + "</div>" +
      '<div class="bottom-bar">' + notice + '<button class="cta" type="button" data-action="' + action + '">' + button + "</button></div>" +
      "</section>";
  }

  function resultSection(kicker, title, items) {
    return '<div class="result-section"><p class="section-kicker">' + kicker + '</p><h1 class="section-title">' + title + '</h1><div class="info-card"><ul class="bullet-list">' +
      items.map(function (item) { return "<li>" + formatListItem(item) + "</li>"; }).join("") +
      "</ul></div></div>";
  }

  function formatListItem(item) {
    var parts = item.split("|");
    if (parts.length === 1) {
      return escapeHtml(item);
    }
    return '<span><strong>' + escapeHtml(parts[0]) + '</strong><small>' + escapeHtml(parts.slice(1).join("|")) + '</small></span>';
  }

  function mascotMarkup(profile) {
    if (profile.image) {
      return '<img class="mascot-image" src="' + profile.image + '" alt="' + escapeHtml(profile.title) + ' 캐릭터">';
    }
    return escapeHtml(profile.mark);
  }

  function profileVars(profile) {
    return "--profile-top:" + profile.gradient[0] +
      ";--profile-bottom:" + profile.gradient[1] +
      ";--profile-card:" + profile.card +
      ";--profile-surface:" + (profile.surface || profile.card) +
      ";--profile-border:" + profile.border +
      ";--profile-title:" + profile.titleColor +
      ";--profile-soft:" + profile.soft +
      ";--profile-body:" + (profile.body || profile.ink) +
      ";--profile-ink:#fff;--profile-card-ink:" + profile.ink + ";";
  }

  function reservationNotice() {
    if (state.reservation.status === "saving") {
      return '<p class="notice">사전등록 저장 중입니다.</p>';
    }
    if (state.reservation.status === "success") {
      return '<p class="notice success">사전등록 저장 완료: ' + escapeHtml(state.reservation.message) + "</p>";
    }
    if (state.reservation.status === "failure") {
      return '<p class="notice failure">사전등록 저장 실패: ' + escapeHtml(state.reservation.message) + "</p>";
    }
    return '<p class="notice">결과 화면은 표시되지만, 저장 완료 여부는 서버 응답 후 확인돼요.</p>';
  }

  function validateProfileForm() {
    var name = document.getElementById("name").value.trim();
    var contact = document.getElementById("contact").value.trim();
    var consent = document.getElementById("consent").checked;
    var error = document.getElementById("form-error");

    if (!name) {
      error.textContent = "이름을 입력해주세요.";
      return false;
    }
    if (name.length > 10) {
      error.textContent = "이름은 최대 10자 이내로 입력해주세요.";
      return false;
    }
    if (!isValidContact(contact)) {
      error.textContent = "휴대폰 번호 또는 이메일을 정확히 입력해주세요.";
      return false;
    }
    if (!consent) {
      error.textContent = "사전등록 저장 동의가 필요해요.";
      return false;
    }

    state.name = name;
    state.contact = contact;
    state.consent = consent;
    return true;
  }

  function isValidContact(contact) {
    var compactPhone = contact.replace(/[^\d]/g, "");
    var email = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return /^01[016789]\d{7,8}$/.test(compactPhone) || email.test(contact);
  }

  function toggleInterest(keyword) {
    var index = state.interests.indexOf(keyword);
    var error = document.getElementById("interest-error");
    if (index >= 0) {
      state.interests.splice(index, 1);
    } else if (state.interests.length < 2) {
      state.interests.push(keyword);
    } else if (error) {
      error.textContent = "관심 키워드는 최대 2개까지 선택할 수 있어요.";
      return;
    }
    render();
  }

  function generateResult() {
    var axes = {
      risk: optionAxisValue("risk"),
      term: optionAxisValue("term"),
      style: optionAxisValue("style"),
      involvement: optionAxisValue("check")
    };

    state.resultKey = resultOrder.reduce(function (best, key) {
      var candidate = classifierProfiles[key];
      var current = classifierProfiles[best];
      if (isBetterClassifierCandidate(axes, candidate, current)) {
        return key;
      }
      return best;
    }, resultOrder[0]);
  }

  function optionAxisValue(questionId) {
    var selected = state.answers[questionId];
    return typeof selected === "number" ? selected + 1 : 2;
  }

  function classifierDistance(axes, profile) {
    return 4 * Math.abs(axes.risk - profile.risk) +
      3 * Math.abs(axes.term - profile.term) +
      2 * Math.abs(axes.style - profile.style) +
      Math.abs(axes.involvement - profile.involvement);
  }

  function isBetterClassifierCandidate(axes, candidate, current) {
    var delta = classifierDistance(axes, candidate) - classifierDistance(axes, current);
    if (Math.abs(delta) > 0.000000001) {
      return delta < 0;
    }

    var riskCompare = Math.abs(axes.risk - candidate.risk) - Math.abs(axes.risk - current.risk);
    if (Math.abs(riskCompare) > 0.000000001) {
      return riskCompare < 0;
    }

    var termCompare = Math.abs(axes.term - candidate.term) - Math.abs(axes.term - current.term);
    if (Math.abs(termCompare) > 0.000000001) {
      return termCompare < 0;
    }

    var styleCompare = Math.abs(axes.style - candidate.style) - Math.abs(axes.style - current.style);
    if (Math.abs(styleCompare) > 0.000000001) {
      return styleCompare < 0;
    }

    var involvementCompare = Math.abs(axes.involvement - candidate.involvement) -
      Math.abs(axes.involvement - current.involvement);
    if (Math.abs(involvementCompare) > 0.000000001) {
      return involvementCompare < 0;
    }

    return candidate.id < current.id;
  }

  function submitReservationOnce() {
    if (state.reservation.status !== "idle") {
      return;
    }
    state.reservation.status = "saving";
    state.reservation.message = "";

    var profile = profiles[state.resultKey];
    var payload = {
      name: state.name,
      contact: state.contact,
      consent: state.consent,
      resultKey: state.resultKey,
      resultTitle: profile.title,
      interestKeywords: state.interests,
      answers: buildAnswerPayload()
    };

    fetch(reservationEndpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    })
      .then(function (response) {
        return response.text().then(function (text) {
          var data = {};
          try {
            data = text ? JSON.parse(text) : {};
          } catch (error) {
            data = { message: text };
          }
          if (!response.ok) {
            throw new Error(data.message || "서버가 저장 요청을 거절했어요. 현장 스태프에게 알려주세요.");
          }
          return data;
        });
      })
      .then(function (data) {
        state.reservation.status = "success";
        state.reservation.message = data.message || "출시 알림 신청이 저장되었어요.";
        render();
      })
      .catch(function (error) {
        state.reservation.status = "failure";
        state.reservation.message = error.message || "네트워크 오류로 저장하지 못했어요.";
        render();
      });
  }

  function buildAnswerPayload() {
    var payload = {};
    questions.forEach(function (question) {
      var selected = state.answers[question.id];
      if (selected !== undefined) {
        payload[question.id] = question.options[selected].label;
      }
    });
    return payload;
  }

  function nextStep() {
    var index = currentIndex();
    if (index < stepOrder.length - 1) {
      state.step = stepOrder[index + 1];
    }
    render();
  }

  function restart() {
    state = {
      step: "welcome",
      name: "",
      contact: "",
      consent: false,
      answers: {},
      interests: [],
      resultKey: null,
      reservation: {
        status: "idle",
        message: ""
      }
    };
    render();
  }

  function render() {
    if (state.step === "welcome") {
      renderWelcome();
      return;
    }
    if (state.step === "profile") {
      renderProfileForm();
      return;
    }
    if (state.step === "start") {
      renderStart();
      return;
    }
    if (state.step.indexOf("q") === 0) {
      renderQuestion(Number(state.step.slice(1)));
      return;
    }
    if (state.step === "interests") {
      renderInterests();
      return;
    }
    if (state.step === "intro") {
      renderIntro();
      return;
    }
    renderResultPage(state.step);
  }

  app.addEventListener("click", function (event) {
    var target = event.target.closest("[data-action]");
    if (!target || target.disabled) {
      return;
    }

    var action = target.getAttribute("data-action");
    if (action === "back") {
      goBack();
      return;
    }
    if (action === "next") {
      nextStep();
      return;
    }
    if (action === "profileNext") {
      if (validateProfileForm()) {
        setStep("start");
      }
      return;
    }
    if (action === "answer") {
      state.answers[target.getAttribute("data-question")] = Number(target.getAttribute("data-index"));
      render();
      return;
    }
    if (action === "interest") {
      toggleInterest(target.getAttribute("data-keyword"));
      return;
    }
    if (action === "showIntro") {
      generateResult();
      submitReservationOnce();
      setStep("intro");
      return;
    }
    if (action === "result") {
      setStep("result");
      return;
    }
    if (action === "restart") {
      restart();
    }
  });

  app.addEventListener("input", function (event) {
    if (event.target.id === "name") {
      state.name = event.target.value.slice(0, 10);
    }
    if (event.target.id === "contact") {
      state.contact = event.target.value;
    }
    if (event.target.id === "consent") {
      state.consent = event.target.checked;
    }
  });

  render();
}());
