/* ============================================================
 * UniPort 차트/캔들 SVG 일러스트 라이브러리 v2 (사실적 버전)
 * - viewBox 360x220 (가격패널 16-168, 볼륨패널 178-205, 축 영역 별도)
 * - 한국식 캔들: 양봉=빨강(#EF4444), 음봉=파랑(#3B82F6)
 * - 가격축·시간축 라벨 + 그리드 + 거래량 패널 + 18~28개 캔들 + 자연 흐름
 * - 패턴 강조: 어노테이션, 화살표, 점선 박스
 * - 신규: candle_anatomy / order_book / market_cap_treemap / pullback / pullback_volume / stop_loss / ma_60_120
 * ============================================================ */
(function(global){

const COL = {
  bull: '#EF4444', bullSoft: '#FCA5A5',
  bear: '#3B82F6', bearSoft: '#93C5FD',
  ma1:  '#F59E0B',
  ma2:  '#10B981',
  ma3:  '#6366F1',
  supp: '#10B981',
  resi: '#EF4444',
  ink:  '#0F172A',
  axis: '#475569',
  muted:'#94A3B8',
  bg:   '#F8FAFC',
  panel:'#FFFFFF',
  grid: '#E2E8F0',
  arrow:'#3B82F6',
  vol:  '#CBD5E1',
};

const W = 400, H = 250;
const PX0 = 10, PX1 = 348;
const PY0 = 22, PY1 = 195;
const VY0 = 205, VY1 = 235;
const AXX = PX1 + 6;

// ── 헬퍼 ──────────────────────────────────────────
function svgWrap(body){
  return `<svg viewBox="0 0 ${W} ${H}" xmlns="http://www.w3.org/2000/svg" style="display:block;width:100%;height:auto;border-radius:10px;background:${COL.panel};border:1px solid ${COL.grid};font-family:-apple-system,sans-serif">${body}</svg>`;
}
function txt(x,y,t,o){ o=o||{};
  return `<text x="${x}" y="${y}" font-size="${o.size||9}" font-weight="${o.weight||600}" fill="${o.color||COL.muted}" text-anchor="${o.anchor||'start'}">${t}</text>`;
}
function pathLine(pts, color, dash, sw){ sw = sw||1.4;
  if(!pts.length) return '';
  const d = pts.map((p,i)=>`${i?'L':'M'}${p[0]} ${p[1]}`).join(' ');
  return `<path d="${d}" stroke="${color}" fill="none" stroke-width="${sw}" ${dash?`stroke-dasharray="${dash}"`:''} stroke-linecap="round" stroke-linejoin="round"/>`;
}
function arrow(x1,y1,x2,y2,color){
  color = color || COL.arrow;
  const id = 'a'+Math.floor(Math.random()*999999);
  return `<defs><marker id="${id}" viewBox="0 0 10 10" refX="9" refY="5" markerUnits="strokeWidth" markerWidth="5" markerHeight="5" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="${color}"/></marker></defs>`+
    `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${color}" stroke-width="1.6" marker-end="url(#${id})"/>`;
}

function chartFrame(prices, dates){
  const minP = Math.min(...prices), maxP = Math.max(...prices);
  const range = maxP - minP || 1;
  let s = '';
  const ticks = 4;
  for(let i=0;i<=ticks;i++){
    const y = PY0 + (PY1-PY0)*i/ticks;
    s += `<line x1="${PX0}" y1="${y}" x2="${PX1}" y2="${y}" stroke="${COL.grid}" stroke-width="0.6"/>`;
    const p = maxP - range*i/ticks;
    s += txt(AXX, y+3, formatP(p), {color:COL.axis, size:10, weight:700});
  }
  for(let i=0;i<=5;i++){
    const x = PX0 + (PX1-PX0)*i/5;
    s += `<line x1="${x}" y1="${PY0}" x2="${x}" y2="${PY1}" stroke="${COL.grid}" stroke-width="0.4" stroke-dasharray="2 3"/>`;
  }
  s += `<line x1="${PX0}" y1="${VY0-2}" x2="${PX1}" y2="${VY0-2}" stroke="${COL.grid}" stroke-width="0.6"/>`;
  s += txt(PX0+2, VY0+8, 'Vol', {color:COL.axis, size:9.5, weight:800});
  if(dates && dates.length){
    const step = (PX1-PX0)/(dates.length-1);
    dates.forEach((d,i)=>{
      // 양 끝 라벨은 안쪽으로 살짝 들여서 모서리 잘림 방지
      let x = PX0 + step*i;
      if(i === 0) x += 6;
      if(i === dates.length-1) x -= 6;
      s += txt(x, H-7, d, {color:COL.axis, size:9.5, anchor:'middle', weight:700});
    });
  }
  return s;
}
function formatP(p){
  if(p>=10000) return Math.round(p/100)/10+'k';
  return Math.round(p);
}

function priceMapper(prices){
  const minP = Math.min(...prices), maxP = Math.max(...prices);
  const range = maxP - minP || 1;
  return (p)=> PY0 + (1 - (p-minP)/range) * (PY1-PY0);
}

function drawCandles(ohlc, mapY, opts){
  opts = opts || {};
  const n = ohlc.length;
  const xStart = opts.xStart!=null ? opts.xStart : PX0+6;
  const xEnd   = opts.xEnd!=null ? opts.xEnd : PX1-6;
  const w = (xEnd-xStart)/(n-1 || 1);
  const cw = Math.max(2.5, Math.min(8, w*0.65));
  let s = '';
  ohlc.forEach((c,i)=>{
    const x = xStart + i*w;
    const [o,cl,h,l] = c;
    const bull = cl>=o;
    const color = bull ? COL.bull : COL.bear;
    const yO = mapY(o), yC = mapY(cl), yH = mapY(h), yL = mapY(l);
    const yTop = Math.min(yO,yC), bH = Math.max(0.8, Math.abs(yC-yO));
    s += `<line x1="${x.toFixed(1)}" y1="${yH.toFixed(1)}" x2="${x.toFixed(1)}" y2="${yL.toFixed(1)}" stroke="${color}" stroke-width="0.9"/>`;
    s += `<rect x="${(x-cw/2).toFixed(1)}" y="${yTop.toFixed(1)}" width="${cw.toFixed(1)}" height="${bH.toFixed(1)}" fill="${color}" rx="0.5"/>`;
  });
  return { svg: s, xStart, xEnd, w, cw };
}

function drawVolume(volumes, ohlc, opts){
  opts = opts || {};
  const xStart = opts.xStart!=null ? opts.xStart : PX0+6;
  const xEnd   = opts.xEnd!=null ? opts.xEnd : PX1-6;
  const n = volumes.length;
  const w = (xEnd-xStart)/(n-1 || 1);
  const cw = Math.max(2, Math.min(6, w*0.6));
  const maxV = Math.max(...volumes);
  let s = '';
  volumes.forEach((v,i)=>{
    const x = xStart + i*w;
    const h = (v/maxV) * (VY1-VY0);
    const bull = ohlc && ohlc[i] && ohlc[i][1]>=ohlc[i][0];
    const color = bull===false ? COL.bearSoft : (bull?COL.bullSoft:COL.vol);
    s += `<rect x="${(x-cw/2).toFixed(1)}" y="${(VY1-h).toFixed(1)}" width="${cw.toFixed(1)}" height="${h.toFixed(1)}" fill="${color}" rx="0.5"/>`;
  });
  return s;
}

function drawMA(prices, period, color, mapY, opts){
  opts = opts || {};
  const xStart = opts.xStart!=null ? opts.xStart : PX0+6;
  const xEnd   = opts.xEnd!=null ? opts.xEnd : PX1-6;
  const n = prices.length;
  const w = (xEnd-xStart)/(n-1 || 1);
  const ma = [];
  for(let i=0;i<n;i++){
    if(i<period-1){ ma.push(null); continue; }
    let sum=0; for(let j=i-period+1;j<=i;j++) sum += prices[j];
    ma.push(sum/period);
  }
  const pts = ma.map((v,i)=>v==null?null:[xStart+i*w, mapY(v)]).filter(Boolean);
  return pathLine(pts, color, null, opts.sw||1.4);
}

function rng(seed){ let s = seed; return ()=>{ s = (s*9301 + 49297) % 233280; return s/233280; }; }

function genPrices(n, start, trend, volatility, seed){
  const r = rng(seed||7);
  const ohlc = [], closes = [];
  let prev = start;
  for(let i=0;i<n;i++){
    const o = prev;
    const drift = trend(i, n);
    const noise = (r()-0.5) * volatility;
    const c = o + drift + noise;
    const range = (volatility*0.8) * (0.4+r()*0.6);
    const h = Math.max(o,c) + r()*range*0.6;
    const l = Math.min(o,c) - r()*range*0.6;
    ohlc.push([o,c,h,l]); closes.push(c); prev = c;
  }
  return { ohlc, closes };
}

function genVolume(n, base, seed){
  const r = rng(seed||9);
  const v = [];
  for(let i=0;i<n;i++) v.push(base*(0.4+r()*1.2));
  return v;
}

// ── 캔들 패턴 헬퍼 ────────────────────────────────
function makeCandlePatternChart(opts){
  const {dirBefore, dirAfter, patternIdx, patternOhlc, patternVol, label, sublabel, dates, seed} = opts;
  const N = 20;
  const baseStart = 50000;
  const trend = (i,n)=>{
    if(i < patternIdx) return dirBefore * 280 * (1 - i/(N-1));
    return dirAfter * 320 * ((i-patternIdx)/(n-patternIdx));
  };
  const { ohlc } = genPrices(N, baseStart, trend, 600, seed||7);
  if(patternOhlc) ohlc[patternIdx] = patternOhlc(ohlc[patternIdx-1]?.[1] || baseStart);
  const closes = ohlc.map(c=>c[1]);
  const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
  const mapY = priceMapper(all);
  const vols = genVolume(N, 100, (seed||7)+1);
  if(patternVol) vols[patternIdx] = patternVol*100;
  let s = chartFrame(all, dates||['','','','','','']);
  s += drawCandles(ohlc, mapY).svg;
  s += drawVolume(vols, ohlc);
  const xStart = PX0+6, w = (PX1-12-xStart)/(N-1);
  const px = xStart + patternIdx*w;
  const cy = mapY(ohlc[patternIdx][1]);
  s += `<circle cx="${px}" cy="${cy}" r="11" fill="none" stroke="${dirAfter>=0?COL.bull:COL.bear}" stroke-width="1.4" stroke-dasharray="2 2"/>`;
  // 패턴 라벨: 캔들 위 또는 아래 배지로 (제목과 안 겹치게)
  const lblColor = dirAfter>=0?COL.bull:COL.bear;
  const labelWidth = label.length*9 + 16;
  const lblBoxX = Math.max(PX0+2, Math.min(px - labelWidth/2, PX1 - labelWidth));
  // cy가 제목(y=16, size 13) 가까이 있으면 캔들 아래로 배치
  const placeAbove = cy > 50;
  const lblBoxY = placeAbove ? cy-30 : cy+12;
  const lblTextY = placeAbove ? cy-17 : cy+25;
  const arrowY1 = placeAbove ? cy-12 : cy+12;
  s += `<rect x="${lblBoxX}" y="${lblBoxY}" width="${labelWidth}" height="18" fill="#fff" stroke="${lblColor}" stroke-width="1.2" rx="4" opacity="0.95"/>`;
  s += txt(lblBoxX + labelWidth/2, lblTextY, label, {color:lblColor, weight:800, size:11.5, anchor:'middle'});
  s += `<line x1="${lblBoxX + labelWidth/2}" y1="${arrowY1}" x2="${px}" y2="${cy + (placeAbove?-11:11)}" stroke="${lblColor}" stroke-width="1" stroke-dasharray="2 1.5"/>`;
  if(sublabel) s += txt(W/2, 16, sublabel, {color:COL.ink, weight:800, size:13, anchor:'middle'});
  return svgWrap(s);
}

// ── 프리셋 ────────────────────────────────────────
const PRESETS = {

  // 캔들 패턴
  candle_hammer: ()=> makeCandlePatternChart({
    dirBefore:-1, dirAfter:1, patternIdx:14,
    patternOhlc:(prev)=>{ const c=prev; const o=c-50; const cl=c+80;
      const h=Math.max(o,cl)+30; const l=Math.min(o,cl)-700; return [o,cl,h,l]; },
    label:'망치형', sublabel:'하락 끝 → 반등 신호',
    dates:['','-30D','','-15D','','오늘'], seed:11,
  }),

  candle_inverted_hammer: ()=> makeCandlePatternChart({
    dirBefore:-1, dirAfter:1, patternIdx:14,
    patternOhlc:(prev)=>{ const c=prev; const o=c-30; const cl=c+50;
      const h=Math.max(o,cl)+700; const l=Math.min(o,cl)-30; return [o,cl,h,l]; },
    label:'역망치형', sublabel:'하락 끝 → 반전 가능 신호', seed:13,
  }),

  candle_doji: ()=> makeCandlePatternChart({
    dirBefore:1, dirAfter:0, patternIdx:14,
    patternOhlc:(prev)=>{ const o=prev; const cl=prev+5;
      const h=o+500; const l=o-500; return [o,cl,h,l]; },
    label:'도지', sublabel:'추세 전환 가능성·시장 균형', seed:17,
  }),

  candle_long_bullish: ()=> makeCandlePatternChart({
    dirBefore:1, dirAfter:1, patternIdx:14,
    patternOhlc:(prev)=>{ const o=prev-100; const cl=prev+1100;
      const h=cl+80; const l=o-80; return [o,cl,h,l]; },
    patternVol: 2.4,
    label:'장대양봉', sublabel:'강한 매수세·추세 전환·돌파 신호', seed:21,
  }),

  candle_engulfing: ()=>{
    const N=20;
    const trend = (i,n)=>{ if(i<13) return -260; if(i===13) return -200; return 200*((i-13)/(n-13)); };
    const { ohlc } = genPrices(N, 50000, trend, 500, 25);
    const prev = ohlc[12][1];
    ohlc[13] = [prev, prev-700, prev+30, prev-800];
    const o14 = prev-750, c14 = prev+200;
    ohlc[14] = [o14, c14, c14+50, o14-50];
    let curr = c14;
    for(let i=15;i<N;i++){ const next = curr + 250 + (i-15)*30; ohlc[i] = [curr, next, next+60, curr-30]; curr = next; }
    const closes = ohlc.map(c=>c[1]);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    const vols = genVolume(N, 100, 26); vols[14] = 240;
    let s = chartFrame(all, ['','-30D','','-15D','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawVolume(vols, ohlc);
    const xStart = PX0+6, w = (PX1-12-xStart)/(N-1);
    const px = xStart + 14*w;
    s += `<rect x="${px-12}" y="${mapY(ohlc[14][2])-3}" width="24" height="${mapY(ohlc[14][3])-mapY(ohlc[14][2])+6}" fill="none" stroke="${COL.bull}" stroke-width="1.5" stroke-dasharray="2 2" rx="2"/>`;
    // 라벨 배지 — 패턴 위쪽
    const _engCy = mapY(ohlc[14][2]);
    const _engW = 70, _engX = Math.max(PX0+2, Math.min(px - _engW/2, PX1 - _engW));
    s += `<rect x="${_engX}" y="${_engCy-22}" width="${_engW}" height="18" fill="#fff" stroke="${COL.bull}" stroke-width="1.2" rx="4" opacity="0.95"/>`;
    s += txt(_engX+_engW/2, _engCy-9, '상승 장악형', {color:COL.bull, weight:800, size:11.5, anchor:'middle'});
    s += txt(W/2, 16, '직전 음봉을 양봉이 완전히 감쌈 → 매수 우위', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  candle_harami: ()=>{
    const N=20;
    const trend = (i,n)=>{ if(i<13) return 280; return -50*(i-13); };
    const { ohlc } = genPrices(N, 50000, trend, 450, 31);
    const prev = ohlc[12][1];
    ohlc[13] = [prev, prev+900, prev+960, prev-30];
    const big = ohlc[13];
    ohlc[14] = [big[0]+200, big[0]+50, big[0]+260, big[0]+0];
    const closes = ohlc.map(c=>c[1]);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    const vols = genVolume(N, 100, 32);
    let s = chartFrame(all, ['','-30D','','-15D','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawVolume(vols, ohlc);
    const xStart = PX0+6, w = (PX1-12-xStart)/(N-1);
    const px = xStart + 14*w;
    s += `<rect x="${px-7}" y="${mapY(ohlc[14][2])-3}" width="14" height="${mapY(ohlc[14][3])-mapY(ohlc[14][2])+6}" fill="none" stroke="${COL.bear}" stroke-width="1.5" stroke-dasharray="2 2" rx="2"/>`;
    const _hrCy = mapY(ohlc[14][2]);
    const _hrW = 70, _hrX = Math.max(PX0+2, Math.min(px - _hrW/2, PX1 - _hrW));
    s += `<rect x="${_hrX}" y="${_hrCy-22}" width="${_hrW}" height="18" fill="#fff" stroke="${COL.bear}" stroke-width="1.2" rx="4" opacity="0.95"/>`;
    s += txt(_hrX+_hrW/2, _hrCy-9, '하락 잉태형', {color:COL.bear, weight:800, size:11.5, anchor:'middle'});
    s += txt(W/2, 16, '큰 양봉 안에 작은 음봉 → 추세 약화', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  candle_anatomy: ()=>{
    let s = '';
    s += `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 16, '캔들 한 개 = 하루 가격의 4가지 정보', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 캔들 좌표 (양봉 왼쪽, 음봉 오른쪽)
    const cx1=95, cx2=225, top=52, bot=170;
    // 양봉: 종가 위, 시가 아래
    const clBull = 75, opBull = 145;
    // 음봉: 시가 위, 종가 아래
    const opBear = 75, clBear = 145;
    const w = 22;
    // 양봉 (왼쪽)
    s += `<line x1="${cx1}" y1="${top}" x2="${cx1}" y2="${clBull}" stroke="${COL.bull}" stroke-width="2.5"/>`;
    s += `<line x1="${cx1}" y1="${opBull}" x2="${cx1}" y2="${bot}" stroke="${COL.bull}" stroke-width="2.5"/>`;
    s += `<rect x="${cx1-w/2}" y="${clBull}" width="${w}" height="${opBull-clBull}" fill="${COL.bull}" rx="2.5"/>`;
    // 음봉 (오른쪽) — FIXED: 시가가 위, 종가가 아래
    s += `<line x1="${cx2}" y1="${top}" x2="${cx2}" y2="${opBear}" stroke="${COL.bear}" stroke-width="2.5"/>`;
    s += `<line x1="${cx2}" y1="${clBear}" x2="${cx2}" y2="${bot}" stroke="${COL.bear}" stroke-width="2.5"/>`;
    s += `<rect x="${cx2-w/2}" y="${opBear}" width="${w}" height="${clBear-opBear}" fill="${COL.bear}" rx="2.5"/>`;
    // 타이틀 (캔들 위)
    s += txt(cx1, 36, '양봉 (상승)', {color:COL.bull, weight:800, size:14, anchor:'middle'});
    s += txt(cx1, 47, '시가 < 종가', {color:COL.bull, weight:700, size:11, anchor:'middle'});
    s += txt(cx2, 36, '음봉 (하락)', {color:COL.bear, weight:800, size:14, anchor:'middle'});
    s += txt(cx2, 47, '시가 > 종가', {color:COL.bear, weight:700, size:11, anchor:'middle'});
    // 라벨 가이드 라인 (양봉 → 우측, 음봉 → 좌측 안쪽)
    function guideLine(x1, y, x2, color){
      return `<line x1="${x1}" y1="${y}" x2="${x2}" y2="${y}" stroke="${color}" stroke-width="0.8" stroke-dasharray="2 1.5"/>`;
    }
    // 가운데 라벨 영역 (cx1+w/2 ~ cx2-w/2 사이)
    const lx = cx1 + w/2 + 4, rx = cx2 - w/2 - 4;
    // 고가
    s += guideLine(lx, top, rx, COL.axis);
    s += `<rect x="${(lx+rx)/2-22}" y="${top-7}" width="44" height="13" fill="#fff" stroke="${COL.axis}" stroke-width="0.6" rx="2"/>`;
    s += txt((lx+rx)/2, top+2, '고가', {color:COL.axis, weight:800, size:12.5, anchor:'middle'});
    // 종가 (양봉=상단), 시가 (음봉=상단) — 같은 y
    s += guideLine(lx, clBull, rx, COL.bull);
    s += `<rect x="${(lx+rx)/2-32}" y="${clBull-7}" width="64" height="13" fill="#fff" stroke="${COL.muted}" stroke-width="0.6" rx="2"/>`;
    s += txt((lx+rx)/2-26, clBull+2, '종가', {color:COL.bull, weight:800, size:10});
    s += txt((lx+rx)/2+5, clBull+2, '|', {color:COL.muted, size:12, weight:700});
    s += txt((lx+rx)/2+10, clBull+2, '시가', {color:COL.bear, weight:800, size:10});
    // 시가 (양봉=하단), 종가 (음봉=하단)
    s += guideLine(lx, opBull, rx, COL.bear);
    s += `<rect x="${(lx+rx)/2-32}" y="${opBull-7}" width="64" height="13" fill="#fff" stroke="${COL.muted}" stroke-width="0.6" rx="2"/>`;
    s += txt((lx+rx)/2-26, opBull+2, '시가', {color:COL.bull, weight:800, size:10});
    s += txt((lx+rx)/2+5, opBull+2, '|', {color:COL.muted, size:12, weight:700});
    s += txt((lx+rx)/2+10, opBull+2, '종가', {color:COL.bear, weight:800, size:10});
    // 저가
    s += guideLine(lx, bot, rx, COL.axis);
    s += `<rect x="${(lx+rx)/2-22}" y="${bot-7}" width="44" height="13" fill="#fff" stroke="${COL.axis}" stroke-width="0.6" rx="2"/>`;
    s += txt((lx+rx)/2, bot+2, '저가', {color:COL.axis, weight:800, size:12.5, anchor:'middle'});
    // 윗꼬리/아랫꼬리/몸통 라벨 (양봉 좌측, 음봉 우측)
    s += txt(cx1-w/2-4, (top+clBull)/2+3, '윗꼬리', {color:COL.muted, size:12, weight:700, anchor:'end'});
    s += txt(cx1-w/2-4, (clBull+opBull)/2+3, '몸통', {color:COL.ink, size:12, weight:800, anchor:'end'});
    s += txt(cx1-w/2-4, (opBull+bot)/2+3, '아랫꼬리', {color:COL.muted, size:12, weight:700, anchor:'end'});
    // 우측 음봉
    s += txt(cx2+w/2+4, (top+opBear)/2+3, '윗꼬리', {color:COL.muted, size:12, weight:700});
    s += txt(cx2+w/2+4, (opBear+clBear)/2+3, '몸통', {color:COL.ink, size:12, weight:800});
    s += txt(cx2+w/2+4, (clBear+bot)/2+3, '아랫꼬리', {color:COL.muted, size:12, weight:700});
    // 하단 안내
    s += `<rect x="20" y="184" width="${W-40}" height="22" fill="#F1F5F9" rx="3"/>`;
    s += txt(W/2, 197, '몸통 = 시가-종가 사이 / 꼬리 = 그날의 최고·최저가', {color:COL.ink, size:12.5, anchor:'middle', weight:800});
    return svgWrap(s);
  },

  // 이동평균
  ma_basic: ()=>{
    const N=24;
    const { ohlc, closes } = genPrices(N, 49000, (i,n)=>120, 500, 41);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-3W','','-2W','','-1W']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawMA(closes, 5, COL.ma1, mapY, {sw:1.6});
    s += drawMA(closes, 20, COL.ma2, mapY, {sw:1.6});
    s += drawVolume(genVolume(N,100,42), ohlc);
    s += `<rect x="${PX0+4}" y="${PY0+3}" width="80" height="14" fill="#fff" opacity="0.85" rx="3"/>`;
    s += txt(PX0+8, PY0+13, 'MA5', {color:COL.ma1, size:11, weight:800});
    s += txt(PX0+38, PY0+13, 'MA20', {color:COL.ma2, size:11, weight:800});
    s += txt(W/2, 16, '이동평균선 = 일정 기간 평균 가격을 잇는 선', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  ma_5_20: ()=>{
    const N=24;
    const { ohlc, closes } = genPrices(N, 48500, (i,n)=>110+(i>10?40:0), 450, 43);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-3W','','-2W','','-1W']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawMA(closes, 5, COL.ma1, mapY, {sw:1.7});
    s += drawMA(closes, 20, COL.ma2, mapY, {sw:1.7});
    s += drawVolume(genVolume(N,100,44), ohlc);
    s += `<rect x="${PX0+4}" y="${PY0+3}" width="158" height="16" fill="#fff" opacity="0.9" rx="3"/>`;
    s += txt(PX0+8, PY0+15, 'MA5 단기', {color:COL.ma1, size:11, weight:800});
    s += txt(PX0+72, PY0+15, '· MA20 중기', {color:COL.ma2, size:11, weight:800});
    s += txt(W/2, 16, '단기선이 중기선 위 → 단기 상승 추세', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  ma_60_120: ()=>{
    const N=30;
    const { ohlc, closes } = genPrices(N, 47000, (i,n)=>140+Math.sin(i*0.4)*40, 550, 47);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['-6M','','-4M','','-2M','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawMA(closes, 5, COL.ma1, mapY);
    s += drawMA(closes, 20, COL.ma3, mapY);
    s += drawMA(closes, 12, COL.ma2, mapY, {sw:2});
    s += drawVolume(genVolume(N,100,48), ohlc);
    s += `<rect x="${PX0+4}" y="${PY0+3}" width="170" height="14" fill="#fff" opacity="0.85" rx="3"/>`;
    s += txt(PX0+8, PY0+13, 'MA5', {color:COL.ma1, size:11, weight:800});
    s += txt(PX0+34, PY0+13, 'MA20', {color:COL.ma3, size:11, weight:800});
    s += txt(PX0+68, PY0+13, 'MA60 (수급선)', {color:COL.ma2, size:11, weight:800});
    s += txt(W/2, 16, '60·120일선 = 중장기 추세 기준선', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  ma_aligned: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 47000, (i,n)=>180, 400, 51);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawMA(closes, 5, COL.ma1, mapY, {sw:1.8});
    s += drawMA(closes, 10, COL.ma2, mapY, {sw:1.8});
    s += drawMA(closes, 20, COL.ma3, mapY, {sw:1.8});
    s += drawVolume(genVolume(N,100,52), ohlc);
    s += `<rect x="${PX0+4}" y="${PY0+3}" width="170" height="14" fill="#fff" opacity="0.85" rx="3"/>`;
    s += txt(PX0+8, PY0+13, 'MA5', {color:COL.ma1, size:11, weight:800});
    s += txt(PX0+36, PY0+13, '> MA10', {color:COL.ma2, size:11, weight:800});
    s += txt(PX0+78, PY0+13, '> MA20', {color:COL.ma3, size:11, weight:800});
    s += txt(W/2, 16, '정배열 = 단기·중기·장기선이 차례로 우상향', {color:COL.bull, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  ma_inverse: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 53000, (i,n)=>-200, 400, 55);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawMA(closes, 5, COL.ma1, mapY, {sw:1.8});
    s += drawMA(closes, 10, COL.ma2, mapY, {sw:1.8});
    s += drawMA(closes, 20, COL.ma3, mapY, {sw:1.8});
    s += drawVolume(genVolume(N,100,56), ohlc);
    s += `<rect x="${PX0+4}" y="${PY0+3}" width="170" height="14" fill="#fff" opacity="0.85" rx="3"/>`;
    s += txt(PX0+8, PY0+13, 'MA5', {color:COL.ma1, size:11, weight:800});
    s += txt(PX0+36, PY0+13, '< MA10', {color:COL.ma2, size:11, weight:800});
    s += txt(PX0+78, PY0+13, '< MA20', {color:COL.ma3, size:11, weight:800});
    s += txt(W/2, 16, '역배열 = 단기선이 가장 아래 (하락 추세)', {color:COL.bear, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  ma_golden_cross: ()=>{
    const N=28;
    const { ohlc, closes } = genPrices(N, 50000, (i,n)=>{
      if(i<10) return -180; if(i<16) return -10; return 220;
    }, 380, 61);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['-2M','','-1M','','-2W','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawMA(closes, 5, COL.ma1, mapY, {sw:1.8});
    s += drawMA(closes, 20, COL.ma2, mapY, {sw:1.8});
    s += drawVolume(genVolume(N,100,62), ohlc);
    const xStart = PX0+6, w = (PX1-12-xStart)/(N-1);
    const cx = xStart + 19*w, cy = mapY(closes[19]);
    const labelY = Math.max(cy-15, 38);
    s += `<circle cx="${cx}" cy="${cy}" r="9" fill="none" stroke="${COL.bull}" stroke-width="1.6"/>`;
    s += arrow(cx+15, labelY, cx+3, cy-3, COL.bull);
    s += `<rect x="${cx+15}" y="${labelY-13}" width="60" height="28" fill="#fff" stroke="${COL.bull}" stroke-width="0.8" rx="3" opacity="0.95"/>`;
    s += txt(cx+18, labelY-2, '골든크로스', {color:COL.bull, size:12.5, weight:800});
    s += txt(cx+18, labelY+10, '매수 신호', {color:COL.bull, size:12, weight:700});
    s += `<rect x="${PX0+4}" y="${PY0+3}" width="80" height="14" fill="#fff" opacity="0.85" rx="3"/>`;
    s += txt(PX0+8, PY0+13, 'MA5', {color:COL.ma1, size:11, weight:800});
    s += txt(PX0+38, PY0+13, 'MA20', {color:COL.ma2, size:11, weight:800});
    return svgWrap(s);
  },

  ma_dead_cross: ()=>{
    const N=28;
    const { ohlc, closes } = genPrices(N, 50000, (i,n)=>{
      if(i<10) return 220; if(i<16) return 10; return -200;
    }, 380, 65);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['-2M','','-1M','','-2W','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawMA(closes, 5, COL.ma1, mapY, {sw:1.8});
    s += drawMA(closes, 20, COL.ma2, mapY, {sw:1.8});
    s += drawVolume(genVolume(N,100,66), ohlc);
    const xStart = PX0+6, w = (PX1-12-xStart)/(N-1);
    const cx = xStart + 19*w, cy = mapY(closes[19]);
    const labelY = Math.min(cy+15, PY1-32);
    s += `<circle cx="${cx}" cy="${cy}" r="9" fill="none" stroke="${COL.bear}" stroke-width="1.6"/>`;
    s += arrow(cx+15, labelY, cx+3, cy+3, COL.bear);
    s += `<rect x="${cx+15}" y="${labelY-7}" width="60" height="28" fill="#fff" stroke="${COL.bear}" stroke-width="0.8" rx="3" opacity="0.95"/>`;
    s += txt(cx+18, labelY+4, '데드크로스', {color:COL.bear, size:12.5, weight:800});
    s += txt(cx+18, labelY+15, '매도 신호', {color:COL.bear, size:12, weight:700});
    s += `<rect x="${PX0+4}" y="${PY0+3}" width="80" height="14" fill="#fff" opacity="0.85" rx="3"/>`;
    s += txt(PX0+8, PY0+13, 'MA5', {color:COL.ma1, size:11, weight:800});
    s += txt(PX0+38, PY0+13, 'MA20', {color:COL.ma2, size:11, weight:800});
    return svgWrap(s);
  },

  ma_converge: ()=>{
    const N=24;
    const { ohlc, closes } = genPrices(N, 50000, (i,n)=>40-Math.abs(i-12)*8, 350, 71);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-3W','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawMA(closes, 5, COL.ma1, mapY);
    s += drawMA(closes, 10, COL.ma2, mapY);
    s += drawMA(closes, 20, COL.ma3, mapY);
    s += drawVolume(genVolume(N,100,72), ohlc);
    const xStart = PX0+6, w = (PX1-12-xStart)/(N-1);
    s += `<rect x="${xStart+18*w-15}" y="${PY0+15}" width="50" height="${PY1-PY0-25}" fill="#F59E0B" opacity="0.08" stroke="#F59E0B" stroke-width="1" stroke-dasharray="3 2" rx="3"/>`;
    s += txt(xStart+18*w+10, PY0+10, '수렴 구간', {color:'#B45309', weight:800, size:11.5, anchor:'middle'});
    s += txt(W/2, 16, '이평선 수렴 = 변동성 폭발 직전 신호', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  ma_diverge: ()=>{
    const N=24;
    const { ohlc, closes } = genPrices(N, 49000, (i,n)=>i<10?60:200, 380, 75);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-3W','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawMA(closes, 5, COL.ma1, mapY, {sw:1.7});
    s += drawMA(closes, 10, COL.ma2, mapY, {sw:1.7});
    s += drawMA(closes, 20, COL.ma3, mapY, {sw:1.7});
    s += drawVolume(genVolume(N,100,76), ohlc);
    s += txt(W/2, 16, '이평선 발산 = 추세 강화', {color:COL.bull, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  ma_with_volume: ()=>{
    const N=24;
    const { ohlc, closes } = genPrices(N, 50000, (i,n)=>i>14?280:80, 450, 81);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    const vols = genVolume(N, 100, 82);
    for(let i=15;i<N;i++) vols[i] *= 2.2;
    let s = chartFrame(all, ['','-3W','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawMA(closes, 5, COL.ma1, mapY);
    s += drawMA(closes, 20, COL.ma2, mapY);
    s += drawVolume(vols, ohlc);
    const xStart = PX0+6, w = (PX1-12-xStart)/(N-1);
    s += `<rect x="${xStart+15*w-3}" y="${VY0-1}" width="${(N-15)*w}" height="${VY1-VY0+1}" fill="none" stroke="${COL.bull}" stroke-width="1.2" stroke-dasharray="3 2" rx="2"/>`;
    s += txt(xStart+18*w, VY0+8, '거래량 동반 상승', {color:COL.bull, weight:800, size:11, anchor:'middle'});
    s += txt(W/2, 16, '거래량 + 이평 우상향 = 강한 매수 흐름', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  ma_support: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 49000, (i,n)=>140+Math.sin(i*0.5)*180, 350, 85);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawMA(closes, 20, COL.ma2, mapY, {sw:2.2});
    s += drawVolume(genVolume(N,100,86), ohlc);
    [10, 16, 22].forEach(i=>{
      const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
      const cx = xStart+i*w, cy = mapY(closes[i]);
      s += `<circle cx="${cx}" cy="${cy}" r="6" fill="none" stroke="${COL.bull}" stroke-width="1.5"/>`;
    });
    s += txt(W/2, 16, '20일선이 지지선 역할 → 반등 지점', {color:COL.bull, weight:800, size:12.5, anchor:'middle'});
    s += `<rect x="${PX0+4}" y="${PY0+3}" width="50" height="14" fill="#fff" opacity="0.85" rx="3"/>`;
    s += txt(PX0+8, PY0+13, 'MA20', {color:COL.ma2, size:11, weight:800});
    return svgWrap(s);
  },

  // RSI
  rsi_basic: ()=>{
    const N=24;
    const { ohlc, closes } = genPrices(N, 50000, (i,n)=>50, 450, 91);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-3W','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const rsiVals = closes.map((c,i)=>50+Math.sin(i*0.5)*25);
    s += `<line x1="${PX0}" y1="${VY0+(VY1-VY0)*(100-70)/100}" x2="${PX1}" y2="${VY0+(VY1-VY0)*(100-70)/100}" stroke="${COL.resi}" stroke-width="0.8" stroke-dasharray="3 3"/>`;
    s += `<line x1="${PX0}" y1="${VY0+(VY1-VY0)*0.5}" x2="${PX1}" y2="${VY0+(VY1-VY0)*0.5}" stroke="${COL.muted}" stroke-width="0.6" stroke-dasharray="2 4"/>`;
    s += `<line x1="${PX0}" y1="${VY0+(VY1-VY0)*0.7}" x2="${PX1}" y2="${VY0+(VY1-VY0)*0.7}" stroke="${COL.supp}" stroke-width="0.8" stroke-dasharray="3 3"/>`;
    s += txt(AXX, VY0+(VY1-VY0)*(100-70)/100+3, '70', {color:COL.resi, size:9.5, weight:700});
    s += txt(AXX, VY0+(VY1-VY0)*0.7+3, '30', {color:COL.supp, size:9.5, weight:700});
    s += `<rect x="${PX0}" y="${VY0+1}" width="30" height="13" fill="#fff"/>`;
    s += txt(PX0+2, VY0+11, 'RSI', {color:'#7C3AED', size:11, weight:800});
    const pts = rsiVals.map((r,i)=>[xStart+i*w, VY0+(VY1-VY0)*(100-r)/100]);
    s += pathLine(pts, '#7C3AED', null, 1.4);
    s += txt(W/2, 16, 'RSI = 0~100, 70 이상 과매수, 30 이하 과매도', {color:COL.ink, weight:800, size:11.5, anchor:'middle'});
    return svgWrap(s);
  },

  rsi_overbought: ()=>{
    const N=24;
    const { ohlc, closes } = genPrices(N, 49000, (i,n)=>i<18?250:-100, 380, 95);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-3W','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const rsi = closes.map((_,i)=> i<10?50+i*2 : i<18? 70+(i-10)*2 : 85-(i-18)*7);
    s += `<line x1="${PX0}" y1="${VY0+(VY1-VY0)*0.3}" x2="${PX1}" y2="${VY0+(VY1-VY0)*0.3}" stroke="${COL.resi}" stroke-width="0.9" stroke-dasharray="3 3"/>`;
    s += `<line x1="${PX0}" y1="${VY0+(VY1-VY0)*0.7}" x2="${PX1}" y2="${VY0+(VY1-VY0)*0.7}" stroke="${COL.supp}" stroke-width="0.9" stroke-dasharray="3 3"/>`;
    s += `<rect x="${PX0}" y="${VY0+1}" width="30" height="13" fill="#fff"/>`;
    s += txt(PX0+2, VY0+11, 'RSI', {color:'#7C3AED', size:11, weight:800});
    s += txt(AXX, VY0+(VY1-VY0)*0.3+3, '70', {color:COL.resi, size:9.5, weight:700});
    const pts = rsi.map((r,i)=>[xStart+i*w, VY0+(VY1-VY0)*(100-r)/100]);
    s += pathLine(pts, '#7C3AED', null, 1.5);
    s += `<rect x="${PX0}" y="${VY0}" width="${PX1-PX0}" height="${(VY1-VY0)*0.3}" fill="${COL.resi}" opacity="0.08"/>`;
    const cx = xStart + 17*w, cy = mapY(closes[17]);
    // 라벨이 너무 위로 가지 않도록 클램프
    const labelY = Math.max(cy, 35);
    s += `<circle cx="${cx}" cy="${cy}" r="9" fill="none" stroke="${COL.resi}" stroke-width="1.4"/>`;
    s += arrow(cx+22, labelY-2, cx+5, cy-3, COL.resi);
    s += `<rect x="${cx+21}" y="${labelY-15}" width="58" height="26" fill="#fff" stroke="${COL.resi}" stroke-width="0.8" rx="3" opacity="0.95"/>`;
    s += txt(cx+24, labelY-3, '과매수', {color:COL.resi, size:12.5, weight:800});
    s += txt(cx+24, labelY+7, '조정 가능', {color:COL.resi, size:11.5, weight:700});
    s += txt(W/2, 16, 'RSI > 70 → 과매수, 매도 압력 증가', {color:COL.resi, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  rsi_oversold: ()=>{
    const N=24;
    const { ohlc, closes } = genPrices(N, 51000, (i,n)=>i<18?-280:140, 380, 99);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-3W','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const rsi = closes.map((_,i)=> i<10?50-i*2 : i<18? 30-(i-10)*2 : 18+(i-18)*7);
    s += `<line x1="${PX0}" y1="${VY0+(VY1-VY0)*0.3}" x2="${PX1}" y2="${VY0+(VY1-VY0)*0.3}" stroke="${COL.resi}" stroke-width="0.9" stroke-dasharray="3 3"/>`;
    s += `<line x1="${PX0}" y1="${VY0+(VY1-VY0)*0.7}" x2="${PX1}" y2="${VY0+(VY1-VY0)*0.7}" stroke="${COL.supp}" stroke-width="0.9" stroke-dasharray="3 3"/>`;
    s += `<rect x="${PX0}" y="${VY0+1}" width="30" height="13" fill="#fff"/>`;
    s += txt(PX0+2, VY0+11, 'RSI', {color:'#7C3AED', size:11, weight:800});
    s += txt(AXX, VY0+(VY1-VY0)*0.7+3, '30', {color:COL.supp, size:9.5, weight:700});
    s += `<rect x="${PX0}" y="${VY0+(VY1-VY0)*0.7}" width="${PX1-PX0}" height="${(VY1-VY0)*0.3}" fill="${COL.supp}" opacity="0.08"/>`;
    const pts = rsi.map((r,i)=>[xStart+i*w, VY0+(VY1-VY0)*(100-r)/100]);
    s += pathLine(pts, '#7C3AED', null, 1.5);
    const cx = xStart + 17*w, cy = mapY(closes[17]);
    const labelY = Math.min(cy, PY1-30);
    s += `<circle cx="${cx}" cy="${cy}" r="9" fill="none" stroke="${COL.supp}" stroke-width="1.4"/>`;
    s += arrow(cx+22, labelY+5, cx+5, cy+3, COL.supp);
    s += `<rect x="${cx+21}" y="${labelY-2}" width="58" height="26" fill="#fff" stroke="${COL.supp}" stroke-width="0.8" rx="3" opacity="0.95"/>`;
    s += txt(cx+24, labelY+9, '과매도', {color:COL.supp, size:12.5, weight:800});
    s += txt(cx+24, labelY+19, '반등 가능', {color:COL.supp, size:11.5, weight:700});
    s += txt(W/2, 16, 'RSI < 30 → 과매도, 매수 기회 신호', {color:COL.supp, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  rsi_divergence: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 50000, (i,n)=> i<10?180:i<16?-80:120, 350, 103);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const high1 = 9, high2 = 22;
    s += pathLine([[xStart+high1*w, mapY(closes[high1])-4],[xStart+high2*w, mapY(closes[high2])-4]], COL.bear, '4 2', 1.4);
    const rsi = closes.map((_,i)=>{
      if(i<=high1) return 50+i*2.5;
      if(i<=16) return 70-(i-high1)*4;
      return 60+Math.sin((i-16)*0.6)*8;
    });
    const pts = rsi.map((r,i)=>[xStart+i*w, VY0+(VY1-VY0)*(100-r)/100]);
    s += `<line x1="${PX0}" y1="${VY0+(VY1-VY0)*0.3}" x2="${PX1}" y2="${VY0+(VY1-VY0)*0.3}" stroke="${COL.resi}" stroke-width="0.7" stroke-dasharray="3 3"/>`;
    s += pathLine(pts, '#7C3AED', null, 1.5);
    s += pathLine([[xStart+high1*w, VY0+(VY1-VY0)*(100-rsi[high1])/100-2],[xStart+high2*w, VY0+(VY1-VY0)*(100-rsi[high2])/100-2]], COL.bear, '4 2', 1.4);
    s += `<rect x="${PX0}" y="${VY0+1}" width="30" height="13" fill="#fff"/>`;
    s += txt(PX0+2, VY0+11, 'RSI', {color:'#7C3AED', size:11, weight:800});
    s += txt(W/2, 16, '약세 다이버전스: 가격↑ vs RSI↓ → 추세 약화', {color:COL.bear, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  rsi_ranging_market: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 50000, (i,n)=>Math.sin(i*0.6)*250, 250, 107);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    const top = Math.max(...closes), bot = Math.min(...closes);
    s += `<line x1="${PX0}" y1="${mapY(top)}" x2="${PX1}" y2="${mapY(top)}" stroke="${COL.resi}" stroke-width="1.4" stroke-dasharray="4 3"/>`;
    s += `<line x1="${PX0}" y1="${mapY(bot)}" x2="${PX1}" y2="${mapY(bot)}" stroke="${COL.supp}" stroke-width="1.4" stroke-dasharray="4 3"/>`;
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const rsi = closes.map((_,i)=> 50+Math.sin(i*0.6+0.3)*22);
    const pts = rsi.map((r,i)=>[xStart+i*w, VY0+(VY1-VY0)*(100-r)/100]);
    s += pathLine(pts, '#7C3AED', null, 1.5);
    s += `<line x1="${PX0}" y1="${VY0+(VY1-VY0)*0.3}" x2="${PX1}" y2="${VY0+(VY1-VY0)*0.3}" stroke="${COL.resi}" stroke-width="0.7" stroke-dasharray="3 3"/>`;
    s += `<line x1="${PX0}" y1="${VY0+(VY1-VY0)*0.7}" x2="${PX1}" y2="${VY0+(VY1-VY0)*0.7}" stroke="${COL.supp}" stroke-width="0.7" stroke-dasharray="3 3"/>`;
    s += `<rect x="${PX0}" y="${VY0+1}" width="30" height="13" fill="#fff"/>`;
    s += txt(PX0+2, VY0+11, 'RSI', {color:'#7C3AED', size:11, weight:800});
    s += txt(W/2, 16, '박스권: RSI 30~70 사이 진동 → 매매 신호 활용', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  rsi_strong_trend: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 47000, (i,n)=>250, 320, 111);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawMA(closes, 5, COL.ma1, mapY);
    s += drawMA(closes, 20, COL.ma2, mapY);
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const rsi = closes.map((_,i)=> i<6? 50+i*4 : 75+Math.sin(i*0.4)*5);
    const pts = rsi.map((r,i)=>[xStart+i*w, VY0+(VY1-VY0)*(100-r)/100]);
    s += `<line x1="${PX0}" y1="${VY0+(VY1-VY0)*0.3}" x2="${PX1}" y2="${VY0+(VY1-VY0)*0.3}" stroke="${COL.resi}" stroke-width="0.9" stroke-dasharray="3 3"/>`;
    s += `<rect x="${PX0}" y="${VY0}" width="${PX1-PX0}" height="${(VY1-VY0)*0.3}" fill="${COL.resi}" opacity="0.08"/>`;
    s += pathLine(pts, '#7C3AED', null, 1.5);
    s += `<rect x="${PX0}" y="${VY0+1}" width="30" height="13" fill="#fff"/>`;
    s += txt(PX0+2, VY0+11, 'RSI', {color:'#7C3AED', size:11, weight:800});
    s += txt(W/2, 16, '강한 상승 추세: RSI 70+ 유지 (과매수 ≠ 매도)', {color:COL.bull, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  // MACD
  macd_basic: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 49000, (i,n)=>140+Math.sin(i*0.4)*100, 320, 121);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawMA(closes, 5, COL.ma1, mapY);
    s += drawMA(closes, 20, COL.ma2, mapY);
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const ema12 = closes.map((_,i)=>{ let sum=0,cnt=0; for(let j=Math.max(0,i-5);j<=i;j++){sum+=closes[j];cnt++;} return sum/cnt; });
    const ema26 = closes.map((_,i)=>{ let sum=0,cnt=0; for(let j=Math.max(0,i-12);j<=i;j++){sum+=closes[j];cnt++;} return sum/cnt; });
    const macd = ema12.map((v,i)=>v-ema26[i]);
    const macdMax = Math.max(...macd.map(Math.abs))*1.2;
    const zeroY = VY0+(VY1-VY0)/2;
    s += `<line x1="${PX0}" y1="${zeroY}" x2="${PX1}" y2="${zeroY}" stroke="${COL.muted}" stroke-width="0.6"/>`;
    macd.forEach((v,i)=>{
      const h = Math.abs(v)/macdMax * (VY1-VY0)/2;
      const color = v>=0?COL.bullSoft:COL.bearSoft;
      s += `<rect x="${(xStart+i*w-2.2).toFixed(1)}" y="${(v>=0?zeroY-h:zeroY).toFixed(1)}" width="4.4" height="${h.toFixed(1)}" fill="${color}"/>`;
    });
    const pts = macd.map((v,i)=>[xStart+i*w, zeroY - (v/macdMax)*(VY1-VY0)/2]);
    s += pathLine(pts, '#3B82F6', null, 1.5);
    const sig = macd.map((_,i)=>{ let sum=0,cnt=0; for(let j=Math.max(0,i-4);j<=i;j++){sum+=macd[j];cnt++;} return sum/cnt; });
    const sigPts = sig.map((v,i)=>[xStart+i*w, zeroY - (v/macdMax)*(VY1-VY0)/2]);
    s += pathLine(sigPts, COL.bull, null, 1.3);
    s += `<rect x="${PX0}" y="${VY0+1}" width="36" height="13" fill="#fff"/>`;
    s += txt(PX0+2, VY0+11, 'MACD', {color:'#3B82F6', size:11, weight:800});
    s += txt(W/2, 16, 'MACD = 단기·장기 EMA 차이 + 시그널선', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  macd_cross_buy: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 50000, (i,n)=> i<13?-180:200, 350, 125);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const macd = closes.map((_,i)=> i<13? -50+i*2 : -25 + (i-13)*8);
    const sig = closes.map((_,i)=> i<13? -40+i*1.5 : -22 + (i-13)*5);
    const macdMax = 80;
    const zeroY = VY0+(VY1-VY0)/2;
    s += `<line x1="${PX0}" y1="${zeroY}" x2="${PX1}" y2="${zeroY}" stroke="${COL.muted}" stroke-width="0.6"/>`;
    macd.forEach((v,i)=>{
      const sv = sig[i]; const diff = v-sv;
      const h = Math.abs(diff)/macdMax * (VY1-VY0)/2 * 4;
      const color = diff>=0?COL.bullSoft:COL.bearSoft;
      s += `<rect x="${(xStart+i*w-2.2).toFixed(1)}" y="${(diff>=0?zeroY-h:zeroY).toFixed(1)}" width="4.4" height="${h.toFixed(1)}" fill="${color}"/>`;
    });
    const macdPts = macd.map((v,i)=>[xStart+i*w, zeroY - v/macdMax*(VY1-VY0)/2]);
    const sigPts = sig.map((v,i)=>[xStart+i*w, zeroY - v/macdMax*(VY1-VY0)/2]);
    s += pathLine(macdPts, '#3B82F6', null, 1.5);
    s += pathLine(sigPts, COL.bull, null, 1.4);
    const cx = xStart + 14*w, cy = zeroY - macd[14]/macdMax*(VY1-VY0)/2;
    const labelY = Math.max(cy-12, VY0+10);
    s += `<circle cx="${cx}" cy="${cy}" r="6" fill="none" stroke="${COL.bull}" stroke-width="1.5"/>`;
    s += arrow(cx+18, labelY, cx+5, cy-3, COL.bull);
    s += txt(cx+22, labelY-3, 'MACD 골든크로스', {color:COL.bull, size:11, weight:800});
    s += txt(cx+22, labelY+7, '매수 시그널', {color:COL.bull, size:11.5, weight:700});
    s += `<rect x="${PX0}" y="${VY0+1}" width="36" height="13" fill="#fff"/>`;
    s += txt(PX0+2, VY0+11, 'MACD', {color:'#3B82F6', size:11, weight:800});
    return svgWrap(s);
  },

  macd_zero_break: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 49000, (i,n)=>i<14?80:240, 320, 131);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const macd = closes.map((_,i)=> i<14? -30+i*1.8 : (i-14)*6);
    const macdMax = 60;
    const zeroY = VY0+(VY1-VY0)/2;
    s += `<line x1="${PX0}" y1="${zeroY}" x2="${PX1}" y2="${zeroY}" stroke="${COL.ink}" stroke-width="1" stroke-dasharray="2 2"/>`;
    s += txt(AXX, zeroY+3, '0', {color:COL.ink, size:10, weight:800});
    macd.forEach((v,i)=>{
      const h = Math.abs(v)/macdMax * (VY1-VY0)/2;
      const color = v>=0?COL.bullSoft:COL.bearSoft;
      s += `<rect x="${(xStart+i*w-2.2).toFixed(1)}" y="${(v>=0?zeroY-h:zeroY).toFixed(1)}" width="4.4" height="${h.toFixed(1)}" fill="${color}"/>`;
    });
    const pts = macd.map((v,i)=>[xStart+i*w, zeroY - v/macdMax*(VY1-VY0)/2]);
    s += pathLine(pts, '#3B82F6', null, 1.5);
    // 0선 cross 시점 계산 (macd가 음 → 양으로 바뀌는 인덱스)
    let crossIdx = 14;
    for(let i=1;i<macd.length;i++){
      if(macd[i-1] < 0 && macd[i] >= 0){ crossIdx = i; break; }
    }
    const cx = xStart + crossIdx*w, cy = zeroY;
    const labelY = Math.min(cy+12, VY1-22);
    s += `<circle cx="${cx}" cy="${cy}" r="6" fill="none" stroke="${COL.bull}" stroke-width="1.6"/>`;
    s += arrow(cx+18, labelY, cx+5, cy+2, COL.bull);
    s += txt(cx+22, labelY+3, '0선 상향 돌파', {color:COL.bull, size:11, weight:800});
    s += txt(cx+22, labelY+13, '추세 전환 확정', {color:COL.bull, size:11.5, weight:700});
    s += `<rect x="${PX0}" y="${VY0+1}" width="36" height="13" fill="#fff"/>`;
    s += txt(PX0+2, VY0+11, 'MACD', {color:'#3B82F6', size:11, weight:800});
    return svgWrap(s);
  },

  macd_trend: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 47000, (i,n)=>200+Math.sin(i*0.3)*60, 280, 135);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawMA(closes, 5, COL.ma1, mapY);
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const macd = closes.map((_,i)=> Math.min(50, 5+i*2.2));
    const macdMax = 60;
    const zeroY = VY0+(VY1-VY0)/2;
    s += `<line x1="${PX0}" y1="${zeroY}" x2="${PX1}" y2="${zeroY}" stroke="${COL.muted}" stroke-width="0.5"/>`;
    macd.forEach((v,i)=>{
      const h = v/macdMax * (VY1-VY0)/2;
      s += `<rect x="${(xStart+i*w-2.2).toFixed(1)}" y="${(zeroY-h).toFixed(1)}" width="4.4" height="${h.toFixed(1)}" fill="${COL.bullSoft}"/>`;
    });
    const pts = macd.map((v,i)=>[xStart+i*w, zeroY - v/macdMax*(VY1-VY0)/2]);
    s += pathLine(pts, '#3B82F6', null, 1.5);
    s += `<rect x="${PX0}" y="${VY0+1}" width="36" height="13" fill="#fff"/>`;
    s += txt(PX0+2, VY0+11, 'MACD', {color:'#3B82F6', size:11, weight:800});
    s += txt(W/2, 16, 'MACD 우상향 + 0선 위 = 강한 상승 추세', {color:COL.bull, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  // 차트 패턴
  box_range: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 50000, (i,n)=>Math.sin(i*0.6)*420, 220, 141);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawVolume(genVolume(N,100,142), ohlc);
    const top = Math.max(...closes)+50, bot = Math.min(...closes)-50;
    s += `<line x1="${PX0+4}" y1="${mapY(top)}" x2="${PX1-4}" y2="${mapY(top)}" stroke="${COL.resi}" stroke-width="1.6" stroke-dasharray="4 3"/>`;
    s += `<line x1="${PX0+4}" y1="${mapY(bot)}" x2="${PX1-4}" y2="${mapY(bot)}" stroke="${COL.supp}" stroke-width="1.6" stroke-dasharray="4 3"/>`;
    s += `<rect x="${PX0+4}" y="${mapY(top)}" width="${PX1-PX0-8}" height="${mapY(bot)-mapY(top)}" fill="${COL.muted}" opacity="0.05"/>`;
    s += txt(PX0+8, mapY(top)-3, '저항선 (상단)', {color:COL.resi, size:11, weight:800});
    s += txt(PX0+8, mapY(bot)+10, '지지선 (하단)', {color:COL.supp, size:11, weight:800});
    s += txt(W/2, 16, '박스권 = 일정 범위에서 가격 횡보', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  box_breakout_top: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 49500, (i,n)=> i<19? Math.sin(i*0.6)*380 : 350+(i-19)*60, 200, 145);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    const vols = genVolume(N, 100, 146);
    for(let i=19;i<N;i++) vols[i] *= 2.5;
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawVolume(vols, ohlc);
    const top = 50100, bot = 49100;
    s += `<line x1="${PX0+4}" y1="${mapY(top)}" x2="${PX1-4}" y2="${mapY(top)}" stroke="${COL.resi}" stroke-width="1.5" stroke-dasharray="4 3"/>`;
    s += `<line x1="${PX0+4}" y1="${mapY(bot)}" x2="${PX1-4}" y2="${mapY(bot)}" stroke="${COL.supp}" stroke-width="1.5" stroke-dasharray="4 3"/>`;
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const cx = xStart + 20*w, cy = mapY(closes[20]);
    s += arrow(cx-15, cy+15, cx-2, cy+3, COL.bull);
    s += txt(cx, cy-7, '박스 상단 돌파', {color:COL.bull, size:12.5, weight:800, anchor:'middle'});
    s += txt(W/2, 16, '거래량 동반 돌파 → 강한 매수 신호', {color:COL.bull, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  box_buy_low: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 50000, (i,n)=>Math.sin(i*0.6)*420, 220, 149);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    const top = Math.max(...closes)+50, bot = Math.min(...closes)-50;
    s += `<line x1="${PX0+4}" y1="${mapY(top)}" x2="${PX1-4}" y2="${mapY(top)}" stroke="${COL.resi}" stroke-width="1.5" stroke-dasharray="4 3"/>`;
    s += `<line x1="${PX0+4}" y1="${mapY(bot)}" x2="${PX1-4}" y2="${mapY(bot)}" stroke="${COL.supp}" stroke-width="1.5" stroke-dasharray="4 3"/>`;
    s += drawVolume(genVolume(N,100,150), ohlc);
    [4, 13, 22].forEach(i=>{
      const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
      const cx = xStart+i*w, cy = mapY(closes[i]);
      s += `<circle cx="${cx}" cy="${cy}" r="6" fill="${COL.bull}" opacity="0.85"/>`;
      s += txt(cx, cy+3, '↑', {color:'#fff', size:11, weight:800, anchor:'middle'});
    });
    s += txt(W/2, 16, '하단(지지선) 매수 / 상단(저항선) 매도', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    s += txt(PX0+8, mapY(bot)+10, '하단 매수', {color:COL.bull, size:11, weight:800});
    s += txt(PX0+8, mapY(top)-3, '상단 매도', {color:COL.bear, size:11, weight:800});
    return svgWrap(s);
  },

  fake_breakout: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 50000, (i,n)=>{
      if(i<14) return Math.sin(i*0.6)*350;
      if(i<17) return 300;
      return -180;
    }, 240, 155);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    const vols = genVolume(N, 100, 156);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawVolume(vols, ohlc);
    const top = 50500, bot = 49400;
    s += `<line x1="${PX0+4}" y1="${mapY(top)}" x2="${PX1-4}" y2="${mapY(top)}" stroke="${COL.resi}" stroke-width="1.5" stroke-dasharray="4 3"/>`;
    s += `<line x1="${PX0+4}" y1="${mapY(bot)}" x2="${PX1-4}" y2="${mapY(bot)}" stroke="${COL.supp}" stroke-width="1.5" stroke-dasharray="4 3"/>`;
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    s += `<rect x="${xStart+13.5*w}" y="${mapY(top)-12}" width="${3.5*w}" height="14" fill="${COL.bear}" opacity="0.15" stroke="${COL.bear}" stroke-width="1" stroke-dasharray="2 2" rx="2"/>`;
    s += txt(xStart+15.5*w, mapY(top)-15, '페이크 돌파', {color:COL.bear, size:12.5, weight:800, anchor:'middle'});
    s += arrow(xStart+18*w, mapY(top)-5, xStart+19*w, mapY((top+bot)/2), COL.bear);
    s += txt(W/2, 16, '거래량 부족 → 박스 안으로 회귀 (페이크)', {color:COL.bear, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  box_target: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 49000, (i,n)=>i<16?Math.sin(i*0.6)*280:i<22?180:60, 220, 159);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    const top = 49350, bot = 48650;
    const target = top + (top-bot);
    s += `<line x1="${PX0+4}" y1="${mapY(top)}" x2="${PX1-4}" y2="${mapY(top)}" stroke="${COL.resi}" stroke-width="1.5" stroke-dasharray="4 3"/>`;
    s += `<line x1="${PX0+4}" y1="${mapY(bot)}" x2="${PX1-4}" y2="${mapY(bot)}" stroke="${COL.supp}" stroke-width="1.5" stroke-dasharray="4 3"/>`;
    s += `<line x1="${PX0+4}" y1="${mapY(target)}" x2="${PX1-4}" y2="${mapY(target)}" stroke="${COL.bull}" stroke-width="1.5" stroke-dasharray="4 3"/>`;
    s += `<line x1="${PX0+12}" y1="${mapY(top)}" x2="${PX0+12}" y2="${mapY(bot)}" stroke="${COL.arrow}" stroke-width="1.4"/>`;
    s += txt(PX0+18, (mapY(top)+mapY(bot))/2+3, 'H', {color:COL.arrow, size:12, weight:800});
    s += `<line x1="${PX0+12}" y1="${mapY(target)}" x2="${PX0+12}" y2="${mapY(top)}" stroke="${COL.bull}" stroke-width="1.4" stroke-dasharray="2 2"/>`;
    s += txt(PX0+18, mapY(top)-3, '+H', {color:COL.bull, size:12, weight:800});
    s += txt(PX1-6, mapY(target)-3, '목표가', {color:COL.bull, size:12.5, weight:800, anchor:'end'});
    s += txt(W/2, 16, '박스 돌파 시 목표가 = 박스 상단 + H', {color:COL.bull, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  // 추세선·지지/저항
  uptrend_line: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 48000, (i,n)=>180+Math.sin(i*0.7)*120, 220, 161);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawVolume(genVolume(N,100,162), ohlc);
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const lows = [3, 11, 19];
    s += pathLine(lows.map(i=>[xStart+i*w-3, mapY(ohlc[i][3])+3]), COL.supp, null, 1.8);
    const x1=xStart+lows[0]*w-3, y1=mapY(ohlc[lows[0]][3])+3;
    const x2=xStart+lows[2]*w-3, y2=mapY(ohlc[lows[2]][3])+3;
    const slope = (y2-y1)/(x2-x1);
    s += `<line x1="${x2}" y1="${y2}" x2="${PX1-4}" y2="${y2+slope*(PX1-4-x2)}" stroke="${COL.supp}" stroke-width="1.5" stroke-dasharray="3 3"/>`;
    lows.forEach(i=>{
      const cx=xStart+i*w, cy=mapY(ohlc[i][3]);
      s += `<circle cx="${cx}" cy="${cy+3}" r="3" fill="${COL.supp}"/>`;
    });
    s += txt(W/2, 16, '상승 추세선 = 저점들을 잇는 선', {color:COL.supp, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  downtrend_line: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 51000, (i,n)=>-180+Math.sin(i*0.7)*120, 220, 165);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawVolume(genVolume(N,100,166), ohlc);
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const highs = [3, 11, 19];
    s += pathLine(highs.map(i=>[xStart+i*w-3, mapY(ohlc[i][2])-3]), COL.resi, null, 1.8);
    const x1=xStart+highs[0]*w-3, y1=mapY(ohlc[highs[0]][2])-3;
    const x2=xStart+highs[2]*w-3, y2=mapY(ohlc[highs[2]][2])-3;
    const slope = (y2-y1)/(x2-x1);
    s += `<line x1="${x2}" y1="${y2}" x2="${PX1-4}" y2="${y2+slope*(PX1-4-x2)}" stroke="${COL.resi}" stroke-width="1.5" stroke-dasharray="3 3"/>`;
    highs.forEach(i=>{
      const cx=xStart+i*w, cy=mapY(ohlc[i][2]);
      s += `<circle cx="${cx}" cy="${cy-3}" r="3" fill="${COL.resi}"/>`;
    });
    s += txt(W/2, 16, '하락 추세선 = 고점들을 잇는 선', {color:COL.resi, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  trend_break: ()=>{
    const N=28;
    const { ohlc, closes } = genPrices(N, 48000, (i,n)=>{
      if(i<18) return 180+Math.sin(i*0.7)*100;
      return -250;
    }, 220, 169);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    const vols = genVolume(N, 100, 170);
    for(let i=18;i<N;i++) vols[i] *= 2;
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawVolume(vols, ohlc);
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const lows = [3, 10, 16];
    const x1=xStart+lows[0]*w-3, y1=mapY(ohlc[lows[0]][3])+3;
    const x2=xStart+lows[2]*w-3, y2=mapY(ohlc[lows[2]][3])+3;
    const slope = (y2-y1)/(x2-x1);
    // 추세선 끝점이 차트 위로 벗어나지 않게 클립
    let endX = PX1-4, endY = y2+slope*(endX-x2);
    if(endY < PY0+4){ endX = x2 + (PY0+4-y2)/slope; endY = PY0+4; }
    s += `<line x1="${x1}" y1="${y1}" x2="${endX}" y2="${endY}" stroke="${COL.supp}" stroke-width="1.6" stroke-dasharray="3 2"/>`;
    const cx = xStart + 20*w, cy = mapY(closes[20]);
    s += `<circle cx="${cx}" cy="${cy}" r="9" fill="none" stroke="${COL.bear}" stroke-width="1.6"/>`;
    s += arrow(cx+12, cy-15, cx+3, cy-3, COL.bear);
    s += txt(cx+15, cy-18, '추세 이탈', {color:COL.bear, size:12.5, weight:800});
    s += txt(cx+15, cy-7, '매도 신호', {color:COL.bear, size:12, weight:700});
    return svgWrap(s);
  },

  support_line: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 50000, (i,n)=>Math.sin(i*0.5)*300, 220, 173);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawVolume(genVolume(N,100,174), ohlc);
    const supportY = mapY(Math.min(...closes)-50);
    s += `<line x1="${PX0+4}" y1="${supportY}" x2="${PX1-4}" y2="${supportY}" stroke="${COL.supp}" stroke-width="2" stroke-dasharray="4 2"/>`;
    [5, 12, 19].forEach(i=>{
      const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
      const cx=xStart+i*w, cy=mapY(ohlc[i][3]);
      s += `<circle cx="${cx}" cy="${cy+2}" r="4" fill="${COL.supp}" opacity="0.9"/>`;
    });
    s += txt(PX0+8, supportY-3, '지지선', {color:COL.supp, weight:800, size:10});
    s += txt(W/2, 16, '지지선 = 가격이 반복적으로 반등하는 가격대', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  resistance_line: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 50000, (i,n)=>Math.sin(i*0.5)*300, 220, 177);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawVolume(genVolume(N,100,178), ohlc);
    const resY = mapY(Math.max(...closes)+50);
    s += `<line x1="${PX0+4}" y1="${resY}" x2="${PX1-4}" y2="${resY}" stroke="${COL.resi}" stroke-width="2" stroke-dasharray="4 2"/>`;
    [5, 12, 19].forEach(i=>{
      const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
      const cx=xStart+i*w, cy=mapY(ohlc[i][2]);
      s += `<circle cx="${cx}" cy="${cy-2}" r="4" fill="${COL.resi}" opacity="0.9"/>`;
    });
    s += txt(PX0+8, resY-4, '저항선', {color:COL.resi, weight:800, size:10});
    s += txt(W/2, 16, '저항선 = 가격이 반복적으로 부딪치는 가격대', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  breakout_high: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 49000, (i,n)=>i<19?Math.sin(i*0.5)*220:280+(i-19)*60, 220, 181);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    const vols = genVolume(N, 100, 182);
    for(let i=19;i<N;i++) vols[i] *= 2.4;
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawVolume(vols, ohlc);
    const resY = mapY(49250);
    s += `<line x1="${PX0+4}" y1="${resY}" x2="${PX1-4}" y2="${resY}" stroke="${COL.resi}" stroke-width="1.6" stroke-dasharray="4 2"/>`;
    s += txt(PX0+8, resY-3, '저항선', {color:COL.resi, weight:800, size:9});
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const cx = xStart + 20*w;
    s += arrow(cx-15, resY+15, cx-2, resY-3, COL.bull);
    s += txt(cx+5, resY-12, '저항선 돌파', {color:COL.bull, size:12.5, weight:800});
    s += txt(W/2, 16, '거래량 동반 저항선 돌파 → 매수 신호', {color:COL.bull, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  breakdown_low: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 51000, (i,n)=>i<19?Math.sin(i*0.5)*220:-280-(i-19)*60, 220, 185);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    const vols = genVolume(N, 100, 186);
    for(let i=19;i<N;i++) vols[i] *= 2.4;
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawVolume(vols, ohlc);
    const supY = mapY(50750);
    s += `<line x1="${PX0+4}" y1="${supY}" x2="${PX1-4}" y2="${supY}" stroke="${COL.supp}" stroke-width="1.6" stroke-dasharray="4 2"/>`;
    s += txt(PX0+8, supY+10, '지지선', {color:COL.supp, weight:800, size:9});
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const cx = xStart + 20*w;
    s += arrow(cx-15, supY-15, cx-2, supY+3, COL.bear);
    s += txt(cx+5, supY+22, '지지선 이탈', {color:COL.bear, size:12.5, weight:800});
    s += txt(W/2, 16, '지지선 이탈 → 추가 하락 위험', {color:COL.bear, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  // 신규: 호가창 / 시가총액 / 눌림목 / 손절선
  order_book: ()=>{
    let s = '';
    s += `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 14, '호가창 (매수/매도 잔량)', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    s += `<line x1="20" y1="22" x2="${W-20}" y2="22" stroke="${COL.grid}"/>`;
    s += txt(95, 32, '잔량(매도)', {color:COL.bear, size:11, weight:800, anchor:'end'});
    s += txt(150, 32, '호가', {color:COL.ink, size:11, weight:800, anchor:'middle'});
    s += txt(225, 32, '잔량(매수)', {color:COL.bull, size:11, weight:800});
    const sellRows = [{p:71800,q:42000},{p:71700,q:35000},{p:71600,q:28000},{p:71500,q:21000},{p:71400,q:15000}];
    const buyRows = [{p:71300,q:18000},{p:71200,q:25000},{p:71100,q:31000},{p:71000,q:48000},{p:70900,q:24000}];
    const maxQ = Math.max(...sellRows.concat(buyRows).map(r=>r.q));
    sellRows.forEach((r,i)=>{
      const y = 42 + i*14;
      const bw = (r.q/maxQ) * 60;
      s += `<rect x="${100-bw}" y="${y-9}" width="${bw}" height="11" fill="${COL.bear}" opacity="0.25" rx="1"/>`;
      s += txt(100, y, r.q.toLocaleString(), {color:COL.bear, size:12, weight:700, anchor:'end'});
      s += txt(150, y, r.p.toLocaleString(), {color:COL.ink, size:12, weight:700, anchor:'middle'});
    });
    const yCur = 42 + sellRows.length*14;
    s += `<rect x="20" y="${yCur-1}" width="${W-40}" height="13" fill="#FEF3C7" rx="2"/>`;
    s += txt(150, yCur+9, '71,350 (현재가)', {color:'#92400E', size:12.5, weight:800, anchor:'middle'});
    s += txt(W-25, yCur+9, '+2.1%', {color:COL.bull, size:11, weight:800, anchor:'end'});
    buyRows.forEach((r,i)=>{
      const y = yCur + 14 + i*14 + 9;
      const bw = (r.q/maxQ) * 60;
      s += `<rect x="200" y="${y-9}" width="${bw}" height="11" fill="${COL.bull}" opacity="0.25" rx="1"/>`;
      s += txt(150, y, r.p.toLocaleString(), {color:COL.ink, size:12, weight:700, anchor:'middle'});
      s += txt(200, y, r.q.toLocaleString(), {color:COL.bull, size:12, weight:700});
    });
    s += txt(35, 50, '매도', {color:COL.bear, size:12, weight:800});
    s += txt(35, 60, '호가', {color:COL.bear, size:12, weight:800});
    s += `<rect x="29" y="40" width="36" height="60" fill="none" stroke="${COL.bear}" stroke-width="1" stroke-dasharray="2 2" rx="3"/>`;
    s += txt(35, yCur+38, '매수', {color:COL.bull, size:12, weight:800});
    s += txt(35, yCur+48, '호가', {color:COL.bull, size:12, weight:800});
    s += `<rect x="29" y="${yCur+22}" width="36" height="60" fill="none" stroke="${COL.bull}" stroke-width="1" stroke-dasharray="2 2" rx="3"/>`;
    return svgWrap(s);
  },

  market_cap_treemap: ()=>{
    let s = '';
    s += `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 14, 'KOSPI 시가총액 비중 (예시)', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    const X0 = 20, Y0 = 26, X1 = W-20, Y1 = H-22;
    const TW = X1-X0, TH = Y1-Y0;
    s += `<rect x="${X0}" y="${Y0}" width="${TW*0.45}" height="${TH}" fill="#3B82F6" rx="4"/>`;
    s += txt(X0+TW*0.225, Y0+TH/2-8, '삼성전자', {color:'#fff', weight:800, size:13, anchor:'middle'});
    s += txt(X0+TW*0.225, Y0+TH/2+8, '23.5%', {color:'#fff', weight:800, size:18, anchor:'middle'});
    s += txt(X0+TW*0.225, Y0+TH/2+24, '약 480조', {color:'#DBEAFE', weight:700, size:11.5, anchor:'middle'});
    let cx = X0+TW*0.45+1;
    s += `<rect x="${cx}" y="${Y0}" width="${TW*0.27}" height="${TH*0.55}" fill="#10B981" rx="4"/>`;
    s += txt(cx+TW*0.135, Y0+TH*0.275-6, 'SK하이닉스', {color:'#fff', weight:800, size:13, anchor:'middle'});
    s += txt(cx+TW*0.135, Y0+TH*0.275+8, '8.2%', {color:'#fff', weight:800, size:14, anchor:'middle'});
    let cx2 = cx+TW*0.27+1;
    s += `<rect x="${cx2}" y="${Y0}" width="${TW*0.27}" height="${TH*0.55}" fill="#F59E0B" rx="4"/>`;
    s += txt(cx2+TW*0.135, Y0+TH*0.275-6, 'LG에너지솔루션', {color:'#fff', weight:800, size:11.5, anchor:'middle'});
    s += txt(cx2+TW*0.135, Y0+TH*0.275+8, '5.8%', {color:'#fff', weight:800, size:14, anchor:'middle'});
    let bY = Y0+TH*0.55+1;
    s += `<rect x="${cx}" y="${bY}" width="${TW*0.18}" height="${TH*0.45-1}" fill="#8B5CF6" rx="4"/>`;
    s += txt(cx+TW*0.09, bY+TH*0.225-3, '삼성바이오', {color:'#fff', weight:800, size:11, anchor:'middle'});
    s += txt(cx+TW*0.09, bY+TH*0.225+9, '3.4%', {color:'#fff', weight:800, size:13, anchor:'middle'});
    let cx3 = cx+TW*0.18+1;
    s += `<rect x="${cx3}" y="${bY}" width="${TW*0.18}" height="${TH*0.45-1}" fill="#EC4899" rx="4"/>`;
    s += txt(cx3+TW*0.09, bY+TH*0.225-3, '현대차', {color:'#fff', weight:800, size:11.5, anchor:'middle'});
    s += txt(cx3+TW*0.09, bY+TH*0.225+9, '2.8%', {color:'#fff', weight:800, size:13, anchor:'middle'});
    let cx4 = cx3+TW*0.18+1;
    s += `<rect x="${cx4}" y="${bY}" width="${X1-cx4}" height="${TH*0.45-1}" fill="#64748B" rx="4"/>`;
    s += txt((cx4+X1)/2, bY+TH*0.225-3, '기타', {color:'#fff', weight:800, size:12.5, anchor:'middle'});
    s += txt((cx4+X1)/2, bY+TH*0.225+9, '56.3%', {color:'#fff', weight:800, size:14, anchor:'middle'});
    return svgWrap(s);
  },

  pullback: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 48000, (i,n)=>{
      if(i<10) return 200; if(i<16) return -100; return 220;
    }, 240, 191);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawMA(closes, 5, COL.ma1, mapY);
    s += drawMA(closes, 10, COL.ma2, mapY, {sw:1.7});
    s += drawVolume(genVolume(N,100,192), ohlc);
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    s += `<rect x="${xStart+10*w-3}" y="${mapY(closes[10])-15}" width="${6*w}" height="${mapY(closes[15])-mapY(closes[10])+25}" fill="#F59E0B" opacity="0.1" stroke="#F59E0B" stroke-width="1" stroke-dasharray="3 2" rx="2"/>`;
    s += txt(xStart+13*w, mapY(closes[10])-20, '눌림목 (조정)', {color:'#B45309', size:12.5, weight:800, anchor:'middle'});
    const cx = xStart+15*w, cy = mapY(closes[15]);
    s += `<circle cx="${cx}" cy="${cy}" r="6" fill="${COL.bull}"/>`;
    s += txt(cx, cy+3, '✓', {color:'#fff', weight:800, size:12.5, anchor:'middle'});
    s += txt(cx, cy+18, '매수 후보', {color:COL.bull, size:11, weight:800, anchor:'middle'});
    s += txt(W/2, 16, '눌림목 = 상승 추세 중 일시적 조정', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  pullback_volume: ()=>{
    const N=26;
    const { ohlc, closes } = genPrices(N, 48000, (i,n)=>{
      if(i<10) return 200; if(i<16) return -100; return 220;
    }, 240, 195);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    const vols = genVolume(N, 100, 196);
    for(let i=10;i<16;i++) vols[i] *= 0.35;
    for(let i=16;i<N;i++) vols[i] *= 1.8;
    let s = chartFrame(all, ['','-1M','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawVolume(vols, ohlc);
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    s += `<rect x="${xStart+10*w-3}" y="${VY0-1}" width="${6*w}" height="${VY1-VY0+1}" fill="none" stroke="#F59E0B" stroke-width="1.2" stroke-dasharray="3 2" rx="2"/>`;
    s += txt(xStart+13*w, VY0+8, '거래량 급감', {color:'#B45309', weight:800, size:11, anchor:'middle'});
    s += `<rect x="${xStart+16*w-3}" y="${VY0-1}" width="${(N-16)*w}" height="${VY1-VY0+1}" fill="none" stroke="${COL.bull}" stroke-width="1.2" stroke-dasharray="3 2" rx="2"/>`;
    s += txt(xStart+19*w, VY0+8, '거래량 회복', {color:COL.bull, weight:800, size:11, anchor:'middle'});
    s += txt(W/2, 16, '눌림목 = 거래량 급감 → 회복 시 재상승', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  stop_loss: ()=>{
    const N=24;
    const { ohlc, closes } = genPrices(N, 50000, (i,n)=>i<6?200:i<14?-180:-300, 250, 199);
    const all = closes.concat(ohlc.flatMap(c=>[c[2],c[3]]));
    const mapY = priceMapper(all);
    let s = chartFrame(all, ['','-3W','','-2W','','오늘']);
    s += drawCandles(ohlc, mapY).svg;
    s += drawVolume(genVolume(N,100,200), ohlc);
    const buyP = closes[6];
    const stopP = buyP * 0.93;
    s += `<line x1="${PX0+4}" y1="${mapY(buyP)}" x2="${PX1-4}" y2="${mapY(buyP)}" stroke="${COL.bull}" stroke-width="1.4" stroke-dasharray="4 2"/>`;
    s += txt(PX1-6, mapY(buyP)-3, '매수 (P)', {color:COL.bull, size:11, weight:800, anchor:'end'});
    s += `<line x1="${PX0+4}" y1="${mapY(stopP)}" x2="${PX1-4}" y2="${mapY(stopP)}" stroke="${COL.bear}" stroke-width="1.6" stroke-dasharray="4 2"/>`;
    s += txt(PX1-6, mapY(stopP)+10, '손절선 (-7%)', {color:COL.bear, size:11, weight:800, anchor:'end'});
    const xStart = PX0+6, w=(PX1-12-xStart)/(N-1);
    const cxB = xStart + 6*w, cyB = mapY(closes[6]);
    s += `<circle cx="${cxB}" cy="${cyB}" r="5" fill="${COL.bull}"/>`;
    let stopIdx = closes.findIndex(p=>p<=stopP);
    if(stopIdx>0){
      const cxS = xStart + stopIdx*w, cyS = mapY(closes[stopIdx]);
      s += `<circle cx="${cxS}" cy="${cyS}" r="6" fill="${COL.bear}"/>`;
      // 라벨 위치: 가격축(PX1+) 안 침범하도록 좌측에 배치
      const lblX = Math.min(cxS+15, PX1-90);
      s += arrow(lblX-3, cyS-12, cxS+3, cyS-3, COL.bear);
      s += `<rect x="${lblX-2}" y="${cyS-23}" width="80" height="26" fill="#fff" stroke="${COL.bear}" stroke-width="1" rx="3" opacity="0.95"/>`;
      s += txt(lblX+2, cyS-12, '손절 발동', {color:COL.bear, size:11, weight:800});
      s += txt(lblX+2, cyS-1, '기계적 매도', {color:COL.bear, size:10.5, weight:700});
    }
    s += txt(W/2, 16, '손절선 = 매수가 -X% 자동 매도 기준', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    return svgWrap(s);
  },

  // ============ 신규 일러스트 (placeholder 카드용) ============
  inflation_decay: ()=>{
    // 시간이 지날수록 동전이 작아짐 (구매력 감소)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '인플레이션 = 시간이 지날수록 줄어드는 돈의 가치', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    const cy = 110;
    const radii = [38, 32, 26, 20, 14];
    const labels = ['오늘','+5년','+10년','+15년','+20년'];
    const values = ['100%','85%','70%','55%','40%'];
    const xStarts = [40, 110, 175, 230, 280];
    radii.forEach((r,i)=>{
      const cx = xStarts[i];
      // 동전 (그라디언트 없이)
      s += `<circle cx="${cx}" cy="${cy}" r="${r}" fill="#FBBF24" stroke="#D97706" stroke-width="${1.5*r/38}"/>`;
      s += `<circle cx="${cx}" cy="${cy}" r="${r-3}" fill="none" stroke="#92400E" stroke-width="${0.6*r/38}" stroke-dasharray="2 1"/>`;
      s += txt(cx, cy+5, '₩', {color:'#92400E', size:Math.round(r*0.8), weight:800, anchor:'middle'});
      // 라벨
      s += txt(cx, cy+r+15, labels[i], {color:COL.axis, size:12, weight:800, anchor:'middle'});
      s += txt(cx, cy+r+27, values[i], {color:i===0?COL.bull:COL.bear, size:13, weight:800, anchor:'middle'});
    });
    // 화살표 시간축
    s += `<line x1="20" y1="180" x2="${W-20}" y2="180" stroke="${COL.muted}" stroke-width="1" marker-end="url(#tArr)"/>`;
    s += `<defs><marker id="tArr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="${COL.muted}"/></marker></defs>`;
    s += txt(W/2, 200, '같은 100원이라도 시간이 지날수록 살 수 있는 양은 줄어요', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  trading_clock: ()=>{
    // 9시-15:30 거래 시간 시계
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '국내 정규 거래 시간 (평일 09:00 ~ 15:30)', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    const cx = 180, cy = 115, r = 70;
    // 시계 베이스
    s += `<circle cx="${cx}" cy="${cy}" r="${r}" fill="#F8FAFC" stroke="${COL.ink}" stroke-width="2"/>`;
    // 거래 시간 호 (9시 → 15시 30분, 시계 방향)
    const ang = a => ({ x: cx + r*Math.sin(a), y: cy - r*Math.cos(a) });
    const a9 = -Math.PI/2; // 12시
    // 9시 = -90도 + (9/12)*360 = ... 실제 시계: 9시 = 270도 = -90도(상단) + 270도 회전 = ...
    // 간단히: 12시 = 위(-pi/2 with our convention 0 = top, clockwise positive)
    // angle = (hour/12) * 2pi (0 at 12 o'clock)
    const arcStart = (9/12) * 2*Math.PI;     // 9시
    const arcEnd = (15.5/12) * 2*Math.PI;    // 15:30
    function pt(ang){ return { x: cx + r*Math.sin(ang), y: cy - r*Math.cos(ang) }; }
    const p1 = pt(arcStart), p2 = pt(arcEnd);
    const largeArc = (arcEnd-arcStart) > Math.PI ? 1 : 0;
    s += `<path d="M${cx} ${cy} L${p1.x} ${p1.y} A${r} ${r} 0 ${largeArc} 1 ${p2.x} ${p2.y} Z" fill="${COL.bull}" opacity="0.18"/>`;
    // 시간 마커 12개
    for(let h=0; h<12; h++){
      const a = (h/12)*2*Math.PI;
      const o1 = pt(a), o0 = { x: cx + (r-6)*Math.sin(a), y: cy - (r-6)*Math.cos(a) };
      s += `<line x1="${o0.x.toFixed(1)}" y1="${o0.y.toFixed(1)}" x2="${o1.x.toFixed(1)}" y2="${o1.y.toFixed(1)}" stroke="${COL.ink}" stroke-width="${[0,3,6,9].includes(h)?1.6:0.9}"/>`;
      if([0,3,6,9].includes(h)){
        const lbl = h===0?'12':String(h);
        const lp = { x: cx + (r-15)*Math.sin(a), y: cy - (r-15)*Math.cos(a) };
        s += txt(lp.x, lp.y+4, lbl, {color:COL.ink, size:13, weight:800, anchor:'middle'});
      }
    }
    // 9시 라벨
    s += `<circle cx="${p1.x}" cy="${p1.y}" r="4" fill="${COL.bull}"/>`;
    s += txt(p1.x-10, p1.y+5, '9:00', {color:COL.bull, size:12, weight:800, anchor:'end'});
    s += txt(p1.x-10, p1.y+17, '장 시작', {color:COL.bull, size:12, weight:700, anchor:'end'});
    // 15:30 라벨 (시계 옆, 정보 박스와 떨어진 곳)
    s += `<circle cx="${p2.x}" cy="${p2.y}" r="4" fill="${COL.bear}"/>`;
    s += txt(p2.x-3, p2.y+18, '15:30', {color:COL.bear, size:12, weight:800, anchor:'middle'});
    s += txt(p2.x-3, p2.y+30, '장 마감', {color:COL.bear, size:11, weight:700, anchor:'middle'});
    // 우측 정보 박스 (오른쪽으로 이동, 박스 + 카드 끝)
    s += `<rect x="290" y="55" width="100" height="135" fill="#F1F5F9" stroke="${COL.line||COL.grid}" stroke-width="1" rx="6"/>`;
    s += txt(340, 73, '거래 시간', {color:COL.ink, size:12, weight:800, anchor:'middle'});
    s += `<line x1="298" y1="80" x2="382" y2="80" stroke="${COL.grid}" stroke-width="0.8"/>`;
    s += txt(300, 96, '🟢 정규장', {color:COL.bull, size:11, weight:800});
    s += txt(300, 109, '09:00 ~ 15:30', {color:COL.axis, size:10, weight:700});
    s += txt(300, 132, '🟡 동시호가', {color:'#B45309', size:11, weight:800});
    s += txt(300, 145, '08:30 ~ 09:00', {color:COL.axis, size:10, weight:700});
    s += txt(300, 168, '🔵 시간외', {color:COL.bear, size:11, weight:800});
    s += txt(300, 181, '16:00 ~ 18:00', {color:COL.axis, size:10, weight:700});
    return svgWrap(s);
  },

  investment_compass: ()=>{
    // 투자 철학 = 나침반
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '투자 철학 = 흔들리지 않는 나침반', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    const cx=180, cy=120, r=68;
    s += `<circle cx="${cx}" cy="${cy}" r="${r}" fill="#FEF3C7" stroke="#D97706" stroke-width="2.5"/>`;
    s += `<circle cx="${cx}" cy="${cy}" r="${r-8}" fill="none" stroke="#D97706" stroke-width="0.8" stroke-dasharray="3 2"/>`;
    // 동서남북
    s += txt(cx, cy-r+12, 'N', {color:COL.bull, size:14, weight:900, anchor:'middle'});
    s += txt(cx, cy+r-4, 'S', {color:COL.axis, size:14, weight:900, anchor:'middle'});
    s += txt(cx-r+10, cy+5, 'W', {color:COL.axis, size:13, weight:900, anchor:'middle'});
    s += txt(cx+r-10, cy+5, 'E', {color:COL.axis, size:13, weight:900, anchor:'middle'});
    // 바늘 (북쪽 = 빨강, 남쪽 = 회색)
    s += `<polygon points="${cx},${cy-50} ${cx-7},${cy} ${cx},${cy-5} ${cx+7},${cy}" fill="${COL.bull}" stroke="#7F1D1D" stroke-width="0.8"/>`;
    s += `<polygon points="${cx},${cy+50} ${cx-7},${cy} ${cx},${cy+5} ${cx+7},${cy}" fill="${COL.muted}" stroke="${COL.axis}" stroke-width="0.8"/>`;
    s += `<circle cx="${cx}" cy="${cy}" r="5" fill="${COL.ink}"/>`;
    s += `<circle cx="${cx}" cy="${cy}" r="2.5" fill="#FBBF24"/>`;
    // 우측 라벨
    s += txt(280, 70, '나의 기준', {color:COL.ink, size:13, weight:800});
    s += txt(280, 88, '✓ 무엇에 투자', {color:COL.bull, size:11.5, weight:700});
    s += txt(280, 102, '✓ 얼마나 리스크', {color:COL.bull, size:11.5, weight:700});
    s += txt(280, 116, '✓ 매수·매도 기준', {color:COL.bull, size:11.5, weight:700});
    s += txt(280, 142, '시장이 흔들려도', {color:COL.muted, size:11.5, weight:700});
    s += txt(280, 156, '내 방향은 변하지', {color:COL.muted, size:11.5, weight:700});
    s += txt(280, 170, '않음', {color:COL.muted, size:11.5, weight:700});
    return svgWrap(s);
  },

  vault_empty: ()=>{
    // 자본잠식 = 비어가는 곳간 (3단계로 비어가는 그림)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '자본잠식 = 회사의 곳간이 점점 비어가는 상태', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    const phases = [
      { x: 50, label: '✓ 흑자 (정상)', sub: '곳간 가득', color: COL.bull, fill: 0.85 },
      { x: 165, label: '⚠ 적자 누적', sub: '곳간 절반', color: '#F59E0B', fill: 0.45 },
      { x: 280, label: '✗ 자본잠식', sub: '곳간 빈 상태', color: COL.bear, fill: 0.1 },
    ];
    phases.forEach(p=>{
      const w=70, h=80, x=p.x-w/2, y=70;
      // 곳간 박스
      s += `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="#F8FAFC" stroke="${COL.ink}" stroke-width="1.6" rx="2"/>`;
      // 지붕
      s += `<polygon points="${x-5},${y} ${x+w/2},${y-15} ${x+w+5},${y}" fill="#94A3B8" stroke="${COL.ink}" stroke-width="1.4"/>`;
      // 문
      s += `<rect x="${x+w/2-9}" y="${y+h-26}" width="18" height="26" fill="${COL.ink}" rx="2"/>`;
      // 채워진 정도 (돈)
      const fillH = h*p.fill;
      s += `<rect x="${x+3}" y="${y+h-fillH}" width="${w-6}" height="${fillH-2}" fill="${p.color}" opacity="0.45"/>`;
      // 동전 표시 (3개)
      for(let i=0;i<3;i++){
        const cy = y+h-fillH+5+i*8;
        if(cy < y+h-3 && p.fill>0.1){
          s += `<circle cx="${x+15+i*15}" cy="${cy}" r="3" fill="#FBBF24"/>`;
        }
      }
      // 라벨
      s += txt(p.x, y+h+15, p.label, {color:p.color, size:12, weight:800, anchor:'middle'});
      s += txt(p.x, y+h+28, p.sub, {color:COL.muted, size:12, weight:700, anchor:'middle'});
    });
    // 화살표 (왼→오)
    s += `<line x1="92" y1="115" x2="125" y2="115" stroke="${COL.muted}" stroke-width="1.5" marker-end="url(#vaArr)"/>`;
    s += `<line x1="207" y1="115" x2="240" y2="115" stroke="${COL.muted}" stroke-width="1.5" marker-end="url(#vaArr)"/>`;
    s += `<defs><marker id="vaArr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="${COL.muted}"/></marker></defs>`;
    s += txt(W/2, 200, '계속 적자 → 자본 줄어듦 → 자본잠식 → 상장폐지 위험', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  vault_full: ()=>{
    // 이익잉여금 = 꽉 찬 곳간
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '이익잉여금 = 매년 쌓이는 회사의 곳간', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 큰 곳간 가운데
    const cx = 130, cy = 110, w = 110, h = 95;
    const x = cx-w/2, y = cy-h/2;
    s += `<polygon points="${x-8},${y} ${cx},${y-22} ${x+w+8},${y}" fill="#94A3B8" stroke="${COL.ink}" stroke-width="1.6"/>`;
    s += `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="#FAFAF9" stroke="${COL.ink}" stroke-width="1.8" rx="2"/>`;
    s += `<rect x="${cx-13}" y="${y+h-32}" width="26" height="32" fill="${COL.ink}" rx="2"/>`;
    s += `<circle cx="${cx+8}" cy="${y+h-15}" r="2" fill="#FBBF24"/>`;
    // 동전 산처럼 쌓기
    for(let row=0; row<5; row++){
      const yy = y+h-12-row*10;
      const cnt = 5-row;
      const startX = cx - (cnt-1)*7;
      for(let i=0;i<cnt;i++){
        s += `<circle cx="${startX+i*14}" cy="${yy}" r="6" fill="#FBBF24" stroke="#D97706" stroke-width="0.8"/>`;
        s += txt(startX+i*14, yy+3, '₩', {color:'#92400E', size:10, weight:800, anchor:'middle'});
      }
    }
    // 화살표 들어옴 (Y1 매출 → Y2 영업이익 → 곳간)
    s += `<line x1="20" y1="80" x2="${x-12}" y2="80" stroke="${COL.bull}" stroke-width="1.6" marker-end="url(#vfArr)"/>`;
    s += `<defs><marker id="vfArr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="${COL.bull}"/></marker></defs>`;
    s += txt(20, 70, '매년 이익', {color:COL.bull, size:12.5, weight:800});
    // 우측 라벨
    s += txt(265, 75, '✓ 위기 때 버틸 돈', {color:COL.bull, size:12, weight:800});
    s += txt(265, 92, '✓ 신사업 투자', {color:COL.bull, size:12, weight:800});
    s += txt(265, 109, '✓ 배당 재원', {color:COL.bull, size:12, weight:800});
    s += txt(265, 130, '👉 회사의', {color:COL.ink, size:12, weight:700});
    s += txt(265, 144, '   체력', {color:COL.ink, size:13, weight:900});
    return svgWrap(s);
  },

  fomo_runners: ()=>{
    // FOMO = 모두 뛰어가는데 한 명만 뒤처짐
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, 'FOMO = "나만 못 따라간다"는 불안', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 뛰어가는 사람들 4명 + 뒤처진 사람 1명
    function person(x, y, color, fast){
      let p = '';
      // 머리
      p += `<circle cx="${x}" cy="${y}" r="8" fill="${color}"/>`;
      // 몸
      p += `<rect x="${x-7}" y="${y+7}" width="14" height="22" fill="${color}" rx="2"/>`;
      // 팔 (앞으로 뻗은)
      if(fast){
        p += `<line x1="${x-7}" y1="${y+12}" x2="${x-15}" y2="${y+18}" stroke="${color}" stroke-width="3.5" stroke-linecap="round"/>`;
        p += `<line x1="${x+7}" y1="${y+12}" x2="${x+18}" y2="${y+8}" stroke="${color}" stroke-width="3.5" stroke-linecap="round"/>`;
        // 다리 (뛰는 자세)
        p += `<line x1="${x-3}" y1="${y+29}" x2="${x-9}" y2="${y+42}" stroke="${color}" stroke-width="4" stroke-linecap="round"/>`;
        p += `<line x1="${x+3}" y1="${y+29}" x2="${x+10}" y2="${y+38}" stroke="${color}" stroke-width="4" stroke-linecap="round"/>`;
      } else {
        p += `<line x1="${x-7}" y1="${y+12}" x2="${x-12}" y2="${y+22}" stroke="${color}" stroke-width="3" stroke-linecap="round"/>`;
        p += `<line x1="${x+7}" y1="${y+12}" x2="${x+12}" y2="${y+22}" stroke="${color}" stroke-width="3" stroke-linecap="round"/>`;
        p += `<line x1="${x-3}" y1="${y+29}" x2="${x-3}" y2="${y+45}" stroke="${color}" stroke-width="3.5" stroke-linecap="round"/>`;
        p += `<line x1="${x+3}" y1="${y+29}" x2="${x+3}" y2="${y+45}" stroke="${color}" stroke-width="3.5" stroke-linecap="round"/>`;
      }
      return p;
    }
    // 4명 뛰어가는 사람
    [
      {x:140, y:90, c:'#3B82F6'},
      {x:185, y:88, c:'#8B5CF6'},
      {x:230, y:90, c:'#10B981'},
      {x:275, y:88, c:'#F59E0B'},
    ].forEach(p=>{ s += person(p.x, p.y, p.c, true); });
    // 뒤처진 사람 (왼쪽, 회색)
    s += person(50, 110, '#94A3B8', false);
    // 말풍선 (FOMO)
    s += `<path d="M70 100 Q90 90 90 75 L130 75 Q140 75 140 65 Q140 55 130 55 L90 55 Q80 55 80 65 Q80 75 65 95 Z" fill="${COL.bear}" opacity="0.95"/>`;
    s += txt(110, 70, '나만 늦은건가?!', {color:'#fff', size:12, weight:800, anchor:'middle'});
    // 길 표시 (점선)
    s += `<line x1="20" y1="160" x2="${W-20}" y2="160" stroke="${COL.muted}" stroke-width="1" stroke-dasharray="4 4"/>`;
    // "수익률 +30%" 같은 라벨이 위
    s += txt(220, 78, '+30% 수익', {color:COL.bull, size:11, weight:800, anchor:'middle'});
    s += txt(50, 175, '뒤처진 나', {color:COL.muted, size:12, weight:800, anchor:'middle'});
    s += txt(W/2, 200, '👉 충동 매수 → 고점 매수 위험. 내 기준대로 가는 게 우선!', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  trading_journal: ()=>{
    // 매매 일지 = 노트 + 펜
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '매매 일지 = 내 거래의 기록·반성·성장', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 노트 베이스
    const x=60, y=40, w=200, h=150;
    s += `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="#FFFBEB" stroke="${COL.ink}" stroke-width="1.6" rx="3"/>`;
    // 줄 무늬
    for(let i=1;i<10;i++){
      s += `<line x1="${x+10}" y1="${y+i*15}" x2="${x+w-10}" y2="${y+i*15}" stroke="${COL.grid}" stroke-width="0.6"/>`;
    }
    // 좌측 빨간 마진선
    s += `<line x1="${x+25}" y1="${y+5}" x2="${x+25}" y2="${y+h-5}" stroke="${COL.bull}" stroke-width="0.8" stroke-dasharray="2 2"/>`;
    // 노트 내용 (스크리블)
    s += txt(x+30, y+18, '2026.04.27 매매 일지', {color:COL.ink, size:12, weight:800});
    s += txt(x+30, y+33, '• 종목: 삼성전자', {color:COL.axis, size:12, weight:700});
    s += txt(x+30, y+48, '• 매수가: 71,400 (+3% 후 진입)', {color:COL.bull, size:12, weight:700});
    s += txt(x+30, y+63, '• 매도가: 73,800 (+3.4% 익절)', {color:COL.bull, size:12, weight:700});
    s += txt(x+30, y+78, '• 이유: 저항선 돌파+볼륨', {color:COL.axis, size:12, weight:700});
    s += txt(x+30, y+93, '• 결과: ✓ 계획대로', {color:COL.bull, size:11, weight:800});
    s += txt(x+30, y+108, '• 배운 점: 손절 -2%', {color:COL.axis, size:12, weight:700});
    s += txt(x+30, y+123, '       지킨게 컸음', {color:COL.axis, size:12, weight:700});
    s += txt(x+30, y+138, '⭐⭐⭐⭐☆ (4/5)', {color:'#F59E0B', size:12.5, weight:800});
    // 펜
    s += `<g transform="translate(280,90) rotate(35)">
      <rect x="-3" y="-40" width="6" height="60" fill="#3B82F6" stroke="${COL.ink}" stroke-width="0.8" rx="2"/>
      <polygon points="-3,20 3,20 0,30" fill="#1F2937"/>
      <rect x="-3" y="-40" width="6" height="14" fill="#1E40AF"/>
      <rect x="-3" y="-26" width="6" height="3" fill="#FBBF24"/>
    </g>`;
    return svgWrap(s);
  },

  fear_greed_gauge: ()=>{
    // 공포-탐욕 지수 게이지
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '공포·탐욕 지수 = 시장 심리 0~100', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    const cx = W/2, cy = 145, r = 80;
    // 반원 게이지 (0=좌, 100=우)
    function arc(start, end, color, opacity){
      const a1 = Math.PI*(1+start/100), a2 = Math.PI*(1+end/100);
      const p1 = { x: cx + r*Math.cos(a1), y: cy + r*Math.sin(a1) };
      const p2 = { x: cx + r*Math.cos(a2), y: cy + r*Math.sin(a2) };
      return `<path d="M ${p1.x} ${p1.y} A ${r} ${r} 0 0 1 ${p2.x} ${p2.y}" stroke="${color}" stroke-width="22" fill="none" opacity="${opacity||0.85}"/>`;
    }
    // 5단계 (0-25 극공포, 25-45 공포, 45-55 중립, 55-75 탐욕, 75-100 극탐욕)
    s += arc(0, 25, '#1E40AF');     // 극공포 짙은 파랑
    s += arc(25, 45, COL.bear);     // 공포
    s += arc(45, 55, COL.muted);    // 중립
    s += arc(55, 75, '#F59E0B');    // 탐욕
    s += arc(75, 100, COL.bull);    // 극탐욕
    // 5개 라벨
    [{p:12,t:'극공포',c:'#1E40AF'},{p:35,t:'공포',c:COL.bear},{p:50,t:'중립',c:COL.muted},{p:65,t:'탐욕',c:'#B45309'},{p:88,t:'극탐욕',c:COL.bull}].forEach(L=>{
      const a = Math.PI*(1+L.p/100);
      const lx = cx + (r+18)*Math.cos(a), ly = cy + (r+18)*Math.sin(a);
      s += txt(lx, ly+3, L.t, {color:L.c, size:11, weight:800, anchor:'middle'});
    });
    // 바늘 (현재 = 72 = 탐욕)
    const val = 72;
    const aa = Math.PI*(1+val/100);
    const tipX = cx + (r-10)*Math.cos(aa), tipY = cy + (r-10)*Math.sin(aa);
    s += `<line x1="${cx}" y1="${cy}" x2="${tipX}" y2="${tipY}" stroke="${COL.ink}" stroke-width="3" stroke-linecap="round"/>`;
    s += `<circle cx="${cx}" cy="${cy}" r="7" fill="${COL.ink}"/>`;
    s += `<circle cx="${cx}" cy="${cy}" r="3" fill="#fff"/>`;
    // 현재 값
    s += txt(cx, cy+30, `현재: ${val}점`, {color:'#B45309', size:13, weight:900, anchor:'middle'});
    s += txt(cx, cy+45, '탐욕 구간', {color:'#B45309', size:12.5, weight:800, anchor:'middle'});
    s += txt(W/2, 205, '극공포(매수 기회) ←→ 극탐욕(주의 매도)', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  eggs_baskets: ()=>{
    // 분산투자 = 계란 한 바구니 vs 여러 바구니
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '"계란을 한 바구니에 담지 마라"', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 좌측: 한 바구니에 계란 다 담은 그림 + 위험
    function basket(cx, cy, eggs, color, label){
      let p = '';
      // 바구니
      p += `<path d="M${cx-32} ${cy} Q${cx-32} ${cy+32} ${cx-22} ${cy+38} L${cx+22} ${cy+38} Q${cx+32} ${cy+32} ${cx+32} ${cy} Z" fill="#A78BFA" stroke="${COL.ink}" stroke-width="1.4"/>`;
      // 손잡이
      p += `<path d="M${cx-32} ${cy} Q${cx} ${cy-22} ${cx+32} ${cy}" fill="none" stroke="#7C3AED" stroke-width="2.5"/>`;
      // 계란 (egg shape)
      eggs.forEach(([ex,ey])=>{
        p += `<ellipse cx="${cx+ex}" cy="${cy+ey}" rx="6" ry="8" fill="#FEF3C7" stroke="#D97706" stroke-width="0.8"/>`;
      });
      // 라벨
      p += `<text x="${cx}" y="${cy+62}" font-size="12" font-weight="800" fill="${color}" text-anchor="middle">${label}</text>`;
      return p;
    }
    // 좌측: 한 바구니 (모든 계란 + 깨진 계란 표시)
    s += basket(85, 85, [[-15,8],[-5,4],[5,8],[15,4],[-10,-3],[5,-3]], COL.bear, '✗ 한 바구니');
    s += `<line x1="50" y1="90" x2="120" y2="120" stroke="${COL.bear}" stroke-width="2"/>`;
    s += `<line x1="50" y1="120" x2="120" y2="90" stroke="${COL.bear}" stroke-width="2"/>`;
    s += txt(85, 156, '바구니 떨어지면 전부 깨짐', {color:COL.bear, size:12, weight:700, anchor:'middle'});
    // 우측: 여러 바구니 (분산)
    const right_x = 245;
    s += basket(right_x-50, 80, [[-5,4],[5,8]], COL.bull, '주식');
    s += basket(right_x, 80, [[-5,4],[5,8]], COL.bull, '채권');
    s += basket(right_x+50, 80, [[-5,4],[5,8]], COL.bull, '예금');
    s += txt(right_x, 162, '✓ 여러 바구니', {color:COL.bull, size:12.5, weight:800, anchor:'middle'});
    s += txt(right_x, 178, '하나 깨져도 다른 게 남음', {color:COL.bull, size:12, weight:700, anchor:'middle'});
    // 가운데 vs
    s += txt(170, 105, 'vs', {color:COL.muted, size:14, weight:900, anchor:'middle'});
    return svgWrap(s);
  },

  portfolio_pie: ()=>{
    // 포트폴리오 종목 비중 = 파이차트
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '종목 비중 = 포트폴리오 파이', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    const cx = 130, cy = 115, r = 72;
    const slices = [
      {name:'삼성전자', pct:32, color:'#3B82F6'},
      {name:'KODEX 200', pct:25, color:'#10B981'},
      {name:'현대차', pct:15, color:'#F59E0B'},
      {name:'미국 ETF', pct:18, color:'#8B5CF6'},
      {name:'현금', pct:10, color:'#94A3B8'},
    ];
    let acc = 0;
    slices.forEach(sl=>{
      const a1 = (acc/100)*2*Math.PI - Math.PI/2;
      const a2 = ((acc+sl.pct)/100)*2*Math.PI - Math.PI/2;
      const p1 = { x: cx+r*Math.cos(a1), y: cy+r*Math.sin(a1) };
      const p2 = { x: cx+r*Math.cos(a2), y: cy+r*Math.sin(a2) };
      const large = sl.pct>50?1:0;
      s += `<path d="M${cx} ${cy} L${p1.x.toFixed(1)} ${p1.y.toFixed(1)} A${r} ${r} 0 ${large} 1 ${p2.x.toFixed(1)} ${p2.y.toFixed(1)} Z" fill="${sl.color}" stroke="#fff" stroke-width="1.6"/>`;
      acc += sl.pct;
    });
    // 가운데 텍스트
    s += `<circle cx="${cx}" cy="${cy}" r="28" fill="#fff"/>`;
    s += txt(cx, cy-2, '내 포트', {color:COL.ink, size:12, weight:800, anchor:'middle'});
    s += txt(cx, cy+12, '5종목', {color:COL.ink, size:13, weight:900, anchor:'middle'});
    // 범례
    slices.forEach((sl,i)=>{
      const ly = 55 + i*23;
      s += `<rect x="240" y="${ly-8}" width="14" height="14" fill="${sl.color}" rx="2"/>`;
      s += txt(258, ly+2, sl.name, {color:COL.ink, size:11.5, weight:700});
      s += txt(258, ly+13, sl.pct+'%', {color:sl.color, size:12.5, weight:800});
    });
    return svgWrap(s);
  },

  rebalance_scale: ()=>{
    // 리밸런싱 = 양팔 저울 균형 맞추기
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '리밸런싱 = 비중을 다시 맞추는 저울', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 좌측: 기울어진 저울 (Before)
    function scale(cx, cy, tilt, leftLabel, leftColor, rightLabel, rightColor, leftPct, rightPct, title, titleColor){
      let p = '';
      // 받침대
      p += `<rect x="${cx-3}" y="${cy}" width="6" height="50" fill="${COL.ink}"/>`;
      p += `<polygon points="${cx-25},${cy+50} ${cx+25},${cy+50} ${cx+15},${cy+58} ${cx-15},${cy+58}" fill="${COL.ink}"/>`;
      // 가로축 (기울어짐)
      const arm = 50;
      const lEnd = { x: cx-arm*Math.cos(tilt), y: cy+arm*Math.sin(tilt) };
      const rEnd = { x: cx+arm*Math.cos(tilt), y: cy-arm*Math.sin(tilt) };
      p += `<line x1="${lEnd.x}" y1="${lEnd.y}" x2="${rEnd.x}" y2="${rEnd.y}" stroke="${COL.ink}" stroke-width="3" stroke-linecap="round"/>`;
      // 저울판
      function pan(x, y, label, color, pct){
        let q = '';
        q += `<line x1="${x}" y1="${y}" x2="${x}" y2="${y+12}" stroke="${COL.ink}" stroke-width="1.2"/>`;
        q += `<ellipse cx="${x}" cy="${y+15}" rx="22" ry="3" fill="${COL.muted}"/>`;
        q += `<rect x="${x-20}" y="${y+15}" width="40" height="${10+pct*0.25}" fill="${color}" opacity="0.85" rx="2"/>`;
        q += `<text x="${x}" y="${y+33+pct*0.25}" font-size="11.5" font-weight="800" fill="${color}" text-anchor="middle">${label}</text>`;
        q += `<text x="${x}" y="${y+45+pct*0.25}" font-size="12" font-weight="800" fill="${color}" text-anchor="middle">${pct}%</text>`;
        return q;
      }
      p += pan(lEnd.x, lEnd.y, leftLabel, leftColor, leftPct);
      p += pan(rEnd.x, rEnd.y, rightLabel, rightColor, rightPct);
      // 타이틀
      p += `<text x="${cx}" y="${cy-15}" font-size="13" font-weight="800" fill="${titleColor}" text-anchor="middle">${title}</text>`;
      return p;
    }
    // Before: 주식 60%, 채권 40% → 주식 80% (기울어짐)
    s += scale(85, 60, -0.25, '주식', COL.bull, '채권', COL.bear, 80, 30, 'Before (기울어짐)', '#B45309');
    // After: 주식 60%, 채권 40% (균형)
    s += scale(275, 60, 0, '주식', COL.bull, '채권', COL.bear, 60, 40, 'After (균형)', COL.bull);
    // 화살표 가운데
    s += `<line x1="155" y1="100" x2="205" y2="100" stroke="${COL.muted}" stroke-width="1.6" marker-end="url(#rbArr)"/>`;
    s += `<defs><marker id="rbArr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="${COL.muted}"/></marker></defs>`;
    s += txt(180, 92, '리밸런싱', {color:COL.muted, size:12.5, weight:800, anchor:'middle'});
    s += txt(W/2, 200, '주식 일부 매도 → 채권 매수 → 원래 비중 회복', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  report_magnifier: ()=>{
    // 증권사 리포트 = 돋보기 + 문서
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '증권사 리포트 = 애널리스트의 기업 분석', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 문서
    const dx=70, dy=45, dw=160, dh=145;
    s += `<rect x="${dx}" y="${dy}" width="${dw}" height="${dh}" fill="#fff" stroke="${COL.ink}" stroke-width="1.4" rx="3"/>`;
    s += `<rect x="${dx}" y="${dy}" width="${dw}" height="22" fill="${COL.bear}" rx="3"/>`;
    s += txt(dx+8, dy+15, '삼성전자 (005930)', {color:'#fff', size:12, weight:800});
    s += txt(dx+dw-8, dy+15, 'BUY', {color:'#fff', size:12, weight:900, anchor:'end'});
    // 문서 내용
    s += txt(dx+8, dy+38, '목표가: 95,000원', {color:COL.bull, size:12.5, weight:800});
    s += txt(dx+8, dy+52, '현재가: 71,400원 (+33%)', {color:COL.axis, size:12, weight:700});
    // 미니 차트
    s += `<rect x="${dx+8}" y="${dy+62}" width="${dw-16}" height="32" fill="#F8FAFC"/>`;
    s += pathLine([[dx+12,dy+85],[dx+30,dy+80],[dx+50,dy+78],[dx+70,dy+72],[dx+90,dy+68],[dx+110,dy+65],[dx+130,dy+72],[dx+150,dy+66]], COL.bull, null, 1.4);
    // 점선
    for(let i=0;i<8;i++){
      s += `<line x1="${dx+8}" y1="${dy+105+i*8}" x2="${dx+dw-12}" y2="${dy+105+i*8}" stroke="${COL.grid}" stroke-width="0.6"/>`;
    }
    s += txt(dx+8, dy+103, '✓ 매출 성장 견조', {color:COL.axis, size:12, weight:700});
    s += txt(dx+8, dy+115, '✓ 메모리 가격 회복', {color:COL.axis, size:12, weight:700});
    s += txt(dx+8, dy+127, '⚠ 환율 리스크 존재', {color:'#B45309', size:12, weight:700});
    // 돋보기 (우상단에서 들여다봄)
    const mx=255, my=80;
    s += `<circle cx="${mx}" cy="${my}" r="32" fill="rgba(59,130,246,0.05)" stroke="${COL.bear}" stroke-width="3"/>`;
    s += `<line x1="${mx+22}" y1="${my+22}" x2="${mx+45}" y2="${my+45}" stroke="${COL.bear}" stroke-width="6" stroke-linecap="round"/>`;
    s += `<line x1="${mx+22}" y1="${my+22}" x2="${mx+45}" y2="${my+45}" stroke="${COL.ink}" stroke-width="3" stroke-linecap="round"/>`;
    s += txt(mx, my+5, '🔍', {size:18, anchor:'middle'});
    return svgWrap(s);
  },

  seesaw_rate_stock: ()=>{
    // 금리와 주가의 시소
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '금리와 주가의 시소 관계', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 받침대
    const cx=W/2, cy=140;
    s += `<polygon points="${cx-30},${cy+28} ${cx+30},${cy+28} ${cx},${cy-2}" fill="${COL.ink}"/>`;
    // 시소 가로축 (기울어짐 - 금리 올라감 → 주가 하락)
    const tilt = -0.22; // 금리(왼쪽) 위로, 주가(오른쪽) 아래로
    const arm = 110;
    const lEnd = { x: cx-arm*Math.cos(tilt), y: cy+arm*Math.sin(tilt) };
    const rEnd = { x: cx+arm*Math.cos(tilt), y: cy-arm*Math.sin(tilt) };
    s += `<line x1="${lEnd.x.toFixed(1)}" y1="${lEnd.y.toFixed(1)}" x2="${rEnd.x.toFixed(1)}" y2="${rEnd.y.toFixed(1)}" stroke="${COL.ink}" stroke-width="4" stroke-linecap="round"/>`;
    // 좌측 (금리, 무거움 = 위)
    s += `<rect x="${(lEnd.x-30).toFixed(1)}" y="${(lEnd.y-50).toFixed(1)}" width="60" height="44" fill="${COL.bear}" rx="6" stroke="${COL.ink}" stroke-width="1.6"/>`;
    s += txt(lEnd.x, lEnd.y-30, '금리', {color:'#fff', size:13, weight:800, anchor:'middle'});
    s += txt(lEnd.x, lEnd.y-15, '↑ 상승', {color:'#fff', size:13, weight:800, anchor:'middle'});
    // 우측 (주가, 가벼움 = 아래로)
    s += `<rect x="${(rEnd.x-30).toFixed(1)}" y="${(rEnd.y-30).toFixed(1)}" width="60" height="38" fill="${COL.bull}" opacity="0.4" rx="6" stroke="${COL.bull}" stroke-width="1.6"/>`;
    s += txt(rEnd.x, rEnd.y-12, '주가', {color:COL.bull, size:13, weight:800, anchor:'middle'});
    s += txt(rEnd.x, rEnd.y+2, '↓ 하락', {color:COL.bull, size:13, weight:800, anchor:'middle'});
    // 화살표
    s += `<text x="${lEnd.x}" y="${lEnd.y-58}" font-size="14" fill="${COL.bear}" text-anchor="middle">↑</text>`;
    s += `<text x="${rEnd.x}" y="${rEnd.y+22}" font-size="14" fill="${COL.bull}" text-anchor="middle">↓</text>`;
    s += txt(W/2, 200, '한쪽이 올라가면 반대쪽은 내려감 (반비례 관계)', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  snowball_compound: ()=>{
    // 눈덩이 = 복리. 시간이 지날수록 커지는 눈덩이
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '복리 = 시간이 지날수록 커지는 눈덩이', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 비탈 (그라디언트 없이 사선)
    s += `<path d="M 30 145 Q 180 150 ${W-30} 175 L ${W-30} 195 L 30 165 Z" fill="#E0E7FF" stroke="${COL.muted}" stroke-width="0.8"/>`;
    // 눈덩이 4단계 (점점 커짐)
    const balls = [
      {cx:60, cy:142, r:10, t:'1년', v:'100만'},
      {cx:130, cy:140, r:18, t:'5년', v:'160만'},
      {cx:215, cy:135, r:28, t:'10년', v:'260만'},
      {cx:310, cy:127, r:42, t:'20년', v:'670만'},
    ];
    balls.forEach((b,i)=>{
      // 음영 표시
      s += `<ellipse cx="${b.cx+3}" cy="${b.cy+b.r-2}" rx="${b.r*0.9}" ry="${b.r*0.25}" fill="${COL.muted}" opacity="0.3"/>`;
      // 눈덩이
      s += `<circle cx="${b.cx}" cy="${b.cy}" r="${b.r}" fill="#fff" stroke="${COL.bear}" stroke-width="1.6"/>`;
      // 텍스처 (작은 원들)
      s += `<circle cx="${b.cx-b.r*0.3}" cy="${b.cy-b.r*0.2}" r="${b.r*0.18}" fill="#E0E7FF" opacity="0.7"/>`;
      s += `<circle cx="${b.cx+b.r*0.3}" cy="${b.cy+b.r*0.1}" r="${b.r*0.12}" fill="#E0E7FF" opacity="0.7"/>`;
      // ₩ 마크
      s += txt(b.cx, b.cy+b.r*0.3, '₩', {color:COL.bear, size:Math.round(b.r*0.7), weight:900, anchor:'middle'});
      // 라벨
      s += txt(b.cx, b.cy-b.r-7, b.t, {color:COL.axis, size:12.5, weight:800, anchor:'middle'});
      s += txt(b.cx, b.cy+b.r+15, b.v, {color:COL.bull, size:12, weight:800, anchor:'middle'});
    });
    // 굴러가는 화살표
    s += txt(W/2, 70, '연 5% 복리, 100만 → 670만 (20년)', {color:COL.bull, size:12.5, weight:800, anchor:'middle'});
    s += `<path d="M 80 95 Q 200 75 ${W-50} 90" fill="none" stroke="${COL.bull}" stroke-width="1.4" stroke-dasharray="3 2" marker-end="url(#snowArr)"/>`;
    s += `<defs><marker id="snowArr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="${COL.bull}"/></marker></defs>`;
    s += txt(W/2, 205, '오래 굴릴수록 기하급수적으로 커져요', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  health_checkup: ()=>{
    // 재무제표 = 기업의 건강검진표
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '재무제표 = 기업의 건강검진표', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 청진기 (좌측) — 머리 + 튜브 + 클로즈
    s += `<circle cx="60" cy="105" r="22" fill="none" stroke="${COL.ink}" stroke-width="3"/>`;
    s += `<circle cx="60" cy="105" r="14" fill="#E0E7FF"/>`;
    s += `<path d="M 80 100 Q 105 95 110 75 Q 110 55 95 50 Q 80 50 75 60" fill="none" stroke="${COL.ink}" stroke-width="2.5"/>`;
    s += `<circle cx="73" cy="60" r="4" fill="${COL.ink}"/>`;
    s += `<path d="M 75 50 L 70 35 L 90 30 L 110 35" fill="none" stroke="${COL.ink}" stroke-width="2"/>`;
    s += `<circle cx="113" cy="36" r="4" fill="${COL.ink}"/>`;
    // 검진표 (우측 큰 박스)
    const dx=145, dy=42, dw=180, dh=145;
    s += `<rect x="${dx}" y="${dy}" width="${dw}" height="${dh}" fill="#fff" stroke="${COL.ink}" stroke-width="1.6" rx="4"/>`;
    s += `<rect x="${dx}" y="${dy}" width="${dw}" height="22" fill="${COL.bear}" rx="4"/>`;
    s += txt(dx+10, dy+15, '🩺 기업 건강검진', {color:'#fff', size:12, weight:800});
    s += txt(dx+dw-10, dy+15, 'Q4 2025', {color:'#fff', size:12, weight:700, anchor:'end'});
    // 검진 항목 (체크 박스 형태)
    const items = [
      {label:'유동비율 (200%)', ok:true, txt:'정상'},
      {label:'부채비율 (75%)', ok:true, txt:'양호'},
      {label:'영업이익률 (12%)', ok:true, txt:'양호'},
      {label:'이익잉여금 (+15%)', ok:true, txt:'정상'},
      {label:'영업현금흐름 (+)', ok:true, txt:'정상'},
    ];
    items.forEach((it,i)=>{
      const y = dy+38+i*20;
      s += `<rect x="${dx+8}" y="${y-10}" width="13" height="13" fill="${it.ok?COL.bull:COL.bear}" opacity="0.85" rx="2"/>`;
      s += txt(dx+14.5, y+1, '✓', {color:'#fff', size:13, weight:900, anchor:'middle'});
      s += txt(dx+28, y, it.label, {color:COL.ink, size:11.5, weight:700});
      s += txt(dx+dw-10, y, it.txt, {color:COL.bull, size:12.5, weight:800, anchor:'end'});
    });
    // 종합 결과
    s += `<rect x="${dx+8}" y="${dy+dh-22}" width="${dw-16}" height="14" fill="${COL.bull}" opacity="0.12" rx="2"/>`;
    s += txt(dx+dw/2, dy+dh-12, '종합: 건강 양호 ✓', {color:COL.bull, size:12, weight:800, anchor:'middle'});
    return svgWrap(s);
  },

  basement_below_floor: ()=>{
    // 바닥 밑에 지하실 — "내려갈 수 있다" 경고
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '"바닥 밑에 지하실이 있다"', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 1층 (현재 바닥, 50%)
    const f1 = 75, f2 = 130, f3 = 185;
    function floor(y, label, sub, color, depth, prevY){
      let p = '';
      // 바닥 라인
      p += `<line x1="40" y1="${y}" x2="${W-40}" y2="${y}" stroke="${COL.ink}" stroke-width="1.6"/>`;
      p += txt(50, y-3, label, {color:color, size:12.5, weight:800});
      p += txt(W-50, y-3, sub, {color:color, size:12, weight:800, anchor:'end'});
      // 화살표 (이전 위치 → 현재)
      if(prevY!=null){
        p += `<line x1="200" y1="${prevY+5}" x2="200" y2="${y-8}" stroke="${COL.bear}" stroke-width="1.6" stroke-dasharray="3 2" marker-end="url(#bArr)"/>`;
      }
      return p;
    }
    s += `<defs><marker id="bArr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="${COL.bear}"/></marker></defs>`;
    // 1차 바닥 (-30%)
    s += floor(f1, '1차 "바닥"', '−30%', COL.axis);
    s += `<text x="200" y="${f1+18}" font-size="12.5" font-weight="800" fill="${COL.muted}" text-anchor="middle">매수했는데...</text>`;
    // 2차 (지하 1층, -50%)
    s += floor(f2, '지하 1층', '−50%', COL.bear, null, f1);
    s += `<text x="200" y="${f2+18}" font-size="12.5" font-weight="800" fill="${COL.bear}" text-anchor="middle">"바닥인 줄 알았는데..."</text>`;
    // 3차 (지하 2층, -70%)
    s += floor(f3, '지하 2층', '−70%', '#7F1D1D', null, f2);
    s += `<text x="200" y="${f3+18}" font-size="12.5" font-weight="800" fill="#7F1D1D" text-anchor="middle">"또 내려갈 수 있어!"</text>`;
    return svgWrap(s);
  },

  turtle_vs_hare: ()=>{
    // 거북이 vs 토끼: 장기 vs 단기 투자
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '장기 투자 (거북이) vs 단타 (토끼)', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 트랙 (도로)
    s += `<rect x="20" y="115" width="${W-40}" height="40" fill="#FEF3C7" rx="3"/>`;
    s += `<line x1="20" y1="135" x2="${W-40}" y2="135" stroke="${COL.muted}" stroke-width="0.8" stroke-dasharray="6 4"/>`;
    // FINISH 라인
    s += `<rect x="${W-50}" y="115" width="6" height="40" fill="repeating-linear-gradient(45deg,#000,#000 4px,#fff 4px,#fff 8px)"/>`;
    for(let i=0;i<5;i++){
      s += `<rect x="${W-50}" y="${115+i*8}" width="3" height="4" fill="${COL.ink}"/>`;
      s += `<rect x="${W-47}" y="${115+i*8+4}" width="3" height="4" fill="${COL.ink}"/>`;
    }
    s += txt(W-46, 110, '🏁 목표', {color:COL.ink, size:12, weight:800, anchor:'middle'});
    // 거북이 (오른쪽 끝, 도착 직전 — 천천히 꾸준히)
    function turtle(cx, cy){
      let p = '';
      // 등껍질 (큰 반원)
      p += `<ellipse cx="${cx}" cy="${cy}" rx="22" ry="14" fill="#10B981" stroke="#047857" stroke-width="1.5"/>`;
      // 등 무늬
      p += `<circle cx="${cx-8}" cy="${cy-3}" r="4" fill="#047857" opacity="0.5"/>`;
      p += `<circle cx="${cx+5}" cy="${cy-2}" r="4" fill="#047857" opacity="0.5"/>`;
      p += `<circle cx="${cx-2}" cy="${cy+4}" r="3.5" fill="#047857" opacity="0.5"/>`;
      // 머리
      p += `<ellipse cx="${cx+22}" cy="${cy+2}" rx="6" ry="5" fill="#10B981" stroke="#047857" stroke-width="1.5"/>`;
      // 눈
      p += `<circle cx="${cx+24}" cy="${cy}" r="1.5" fill="#000"/>`;
      // 다리
      p += `<rect x="${cx-15}" y="${cy+11}" width="5" height="5" fill="#047857" rx="1"/>`;
      p += `<rect x="${cx+8}" y="${cy+11}" width="5" height="5" fill="#047857" rx="1"/>`;
      return p;
    }
    s += turtle(255, 138);
    // 토끼 (왼쪽 → 빠르게 가지만 멈춤/낮잠)
    function hare(cx, cy){
      let p = '';
      // 몸통 (타원)
      p += `<ellipse cx="${cx}" cy="${cy}" rx="16" ry="11" fill="#fff" stroke="${COL.ink}" stroke-width="1.4"/>`;
      // 머리
      p += `<circle cx="${cx-12}" cy="${cy-4}" r="9" fill="#fff" stroke="${COL.ink}" stroke-width="1.4"/>`;
      // 귀 (긴)
      p += `<ellipse cx="${cx-15}" cy="${cy-15}" rx="3" ry="9" fill="#fff" stroke="${COL.ink}" stroke-width="1.4"/>`;
      p += `<ellipse cx="${cx-9}" cy="${cy-15}" rx="3" ry="9" fill="#fff" stroke="${COL.ink}" stroke-width="1.4"/>`;
      p += `<ellipse cx="${cx-15}" cy="${cy-15}" rx="1.5" ry="6" fill="#FCA5A5"/>`;
      p += `<ellipse cx="${cx-9}" cy="${cy-15}" rx="1.5" ry="6" fill="#FCA5A5"/>`;
      // 눈 (Z 자, 자고 있음)
      p += `<text x="${cx-13}" y="${cy-3}" font-size="6" font-weight="700" fill="${COL.ink}">Z</text>`;
      p += `<text x="${cx-9}" y="${cy-1}" font-size="5" fill="${COL.ink}">z</text>`;
      // 다리
      p += `<ellipse cx="${cx+5}" cy="${cy+9}" rx="4" ry="3" fill="#fff" stroke="${COL.ink}" stroke-width="1.2"/>`;
      // 꼬리 (작은 원)
      p += `<circle cx="${cx+15}" cy="${cy-2}" r="3" fill="#fff" stroke="${COL.ink}" stroke-width="1.2"/>`;
      // Z 표시 (자고 있는 흔적)
      p += `<text x="${cx-22}" y="${cy-22}" font-size="13" font-weight="700" fill="${COL.muted}">Zzz</text>`;
      return p;
    }
    s += hare(80, 138);
    // 라벨
    s += txt(80, 175, '토끼 (단타)', {color:COL.bear, size:13, weight:800, anchor:'middle'});
    s += txt(80, 188, '빠르지만 자주 멈춤·실패', {color:COL.bear, size:11, weight:700, anchor:'middle'});
    s += txt(255, 175, '거북이 (장기투자)', {color:COL.bull, size:13, weight:800, anchor:'middle'});
    s += txt(255, 188, '느리지만 꾸준히 도착', {color:COL.bull, size:11, weight:700, anchor:'middle'});
    // 화살표 (출발선 → FINISH)
    s += `<text x="30" y="108" font-size="12" font-weight="700" fill="${COL.muted}">출발</text>`;
    s += txt(W/2, 200, '👉 시간이 지나면 거북이가 결국 더 멀리 갑니다 (복리 + 인내)', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  traffic_light: ()=>{
    // 신호등: 매수/관망/매도 시그널
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '매매 신호등 = 매수·관망·매도', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 신호등 박스 (중앙)
    const lx=140, ly=35, lw=80, lh=160;
    s += `<rect x="${lx}" y="${ly}" width="${lw}" height="${lh}" fill="#1F2937" rx="10" stroke="${COL.ink}" stroke-width="2"/>`;
    s += `<rect x="${lx+lw/2-3}" y="${ly+lh}" width="6" height="12" fill="${COL.ink}"/>`;
    // 빨강 (매도, 위)
    s += `<circle cx="${lx+lw/2}" cy="${ly+30}" r="20" fill="${COL.bear}" opacity="0.9" stroke="#1E3A8A" stroke-width="1.5"/>`;
    s += txt(lx+lw/2, ly+34, '↓', {color:'#fff', size:18, weight:900, anchor:'middle'});
    // 노랑 (관망, 가운데)
    s += `<circle cx="${lx+lw/2}" cy="${ly+80}" r="20" fill="#FBBF24" opacity="0.4"/>`;
    s += txt(lx+lw/2, ly+85, '?', {color:'#92400E', size:20, weight:900, anchor:'middle'});
    // 초록 (매수, 아래) — 활성
    s += `<circle cx="${lx+lw/2}" cy="${ly+130}" r="20" fill="${COL.bull}" stroke="#7F1D1D" stroke-width="1.5"/>`;
    s += `<circle cx="${lx+lw/2}" cy="${ly+130}" r="24" fill="none" stroke="${COL.bull}" stroke-width="1.5" opacity="0.4" stroke-dasharray="3 2"/>`;
    s += txt(lx+lw/2, ly+135, '↑', {color:'#fff', size:20, weight:900, anchor:'middle'});
    // 좌측 라벨
    s += `<rect x="20" y="${ly+18}" width="100" height="24" fill="#FEE2E2" rx="3"/>`;
    s += txt(70, ly+33, '🔴 매도 (위험)', {color:COL.bear, size:12.5, weight:800, anchor:'middle'});
    s += `<rect x="20" y="${ly+68}" width="100" height="24" fill="#FEF3C7" rx="3"/>`;
    s += txt(70, ly+83, '🟡 관망 (대기)', {color:'#92400E', size:12.5, weight:800, anchor:'middle'});
    s += `<rect x="20" y="${ly+118}" width="100" height="24" fill="#D1FAE5" rx="3" stroke="${COL.bull}" stroke-width="1.4"/>`;
    s += txt(70, ly+133, '🟢 매수 OK', {color:COL.bull, size:12.5, weight:800, anchor:'middle'});
    // 우측 신호 조건
    s += txt(240, ly+16, '신호 조건', {color:COL.ink, size:12.5, weight:800});
    s += txt(240, ly+33, '• 저항선 부근', {color:COL.bear, size:11, weight:700});
    s += txt(240, ly+45, '• RSI 70+', {color:COL.bear, size:11, weight:700});
    s += txt(240, ly+62, '• 가격 횡보', {color:'#92400E', size:11, weight:700});
    s += txt(240, ly+74, '• 거래량 ↓', {color:'#92400E', size:11, weight:700});
    s += txt(240, ly+91, '• 지지선 반등', {color:COL.bull, size:11, weight:700});
    s += txt(240, ly+103, '• 골든크로스', {color:COL.bull, size:11, weight:700});
    s += txt(240, ly+115, '• 거래량 ↑', {color:COL.bull, size:11, weight:700});
    return svgWrap(s);
  },

  domino_chain: ()=>{
    // 도미노 = 연쇄 효과 (시장 충격 전파)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '도미노 효과 — 한 곳의 충격이 시장 전체로', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 5개 도미노 (왼쪽부터 차례로 넘어짐)
    const baseY = 145;
    const dominoes = [
      {x:50, y:baseY, label:'美 금리 인상', color:'#EF4444', tilt:-1.4},  // 완전 넘어짐
      {x:110, y:baseY, label:'환율 급등', color:'#F59E0B', tilt:-1.0},   // 넘어지는 중
      {x:170, y:baseY, label:'외국인 매도', color:'#FBBF24', tilt:-0.6},  // 기울어짐
      {x:230, y:baseY, label:'코스피 하락', color:'#10B981', tilt:-0.3}, // 살짝
      {x:290, y:baseY, label:'개미 손실', color:'#3B82F6', tilt:0},      // 아직 서있음
    ];
    dominoes.forEach((d,i)=>{
      const dw = 14, dh = 60;
      // 도미노 회전 변환
      const cx = d.x, cy = d.y;
      s += `<g transform="translate(${cx},${cy}) rotate(${d.tilt*30})">`;
      s += `<rect x="${-dw/2}" y="${-dh}" width="${dw}" height="${dh}" fill="${d.color}" stroke="${COL.ink}" stroke-width="1.5" rx="2"/>`;
      // 점 (도미노 점)
      s += `<circle cx="0" cy="${-dh*0.7}" r="2" fill="#fff" opacity="0.85"/>`;
      s += `<circle cx="0" cy="${-dh*0.4}" r="2" fill="#fff" opacity="0.85"/>`;
      s += `</g>`;
      // 라벨 (도미노 아래)
      s += txt(cx, baseY+22, d.label, {color:d.color, size:11.5, weight:800, anchor:'middle'});
      // 번호
      s += `<circle cx="${cx-25}" cy="${baseY+15}" r="9" fill="${d.color}" opacity="0.85"/>`;
      s += txt(cx-25, baseY+18, String(i+1), {color:'#fff', size:12, weight:900, anchor:'middle'});
    });
    // 바닥
    s += `<line x1="20" y1="${baseY+1}" x2="${W-20}" y2="${baseY+1}" stroke="${COL.ink}" stroke-width="1.6"/>`;
    // 화살표 (전파 방향)
    s += `<path d="M 30 50 Q 180 35 ${W-30} 60" fill="none" stroke="${COL.bear}" stroke-width="1.6" stroke-dasharray="4 3" marker-end="url(#dArr)"/>`;
    s += `<defs><marker id="dArr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="${COL.bear}"/></marker></defs>`;
    s += txt(W/2, 50, '한 곳의 충격이 차례로 영향을 줍니다', {color:COL.bear, size:12, weight:800, anchor:'middle'});
    return svgWrap(s);
  },

  storm_market: ()=>{
    // 시장 위기 = 폭풍 속의 배
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '시장 위기 = 폭풍 속의 항해', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 하늘 (폭풍 — 회색)
    s += `<rect x="0" y="30" width="${W}" height="120" fill="#475569" opacity="0.15"/>`;
    // 번개
    s += `<path d="M 80 40 L 75 60 L 85 60 L 78 80" fill="none" stroke="#FBBF24" stroke-width="2.2" stroke-linejoin="round" stroke-linecap="round"/>`;
    s += `<path d="M 280 50 L 275 70 L 285 70 L 278 90" fill="none" stroke="#FBBF24" stroke-width="2" stroke-linejoin="round"/>`;
    // 빗줄기
    for(let i=0;i<14;i++){
      const x = 30 + i*22;
      s += `<line x1="${x}" y1="55" x2="${x-3}" y2="80" stroke="#3B82F6" stroke-width="0.8" opacity="0.5"/>`;
    }
    // 파도 (왼쪽에서 오른쪽으로)
    s += `<path d="M 0 150 Q 45 130 90 150 T 180 150 T 270 150 T 360 150 L 360 220 L 0 220 Z" fill="#3B82F6" opacity="0.5"/>`;
    s += `<path d="M 0 165 Q 45 145 90 165 T 180 165 T 270 165 T 360 165 L 360 220 L 0 220 Z" fill="#3B82F6" opacity="0.4"/>`;
    s += `<path d="M 0 180 Q 45 165 90 180 T 180 180 T 270 180 T 360 180 L 360 220 L 0 220 Z" fill="#1E40AF" opacity="0.6"/>`;
    // 배 (가운데, 약간 기울어짐)
    s += `<g transform="translate(180, 145) rotate(-15)">`;
    s += `<path d="M -32 0 L 32 0 L 25 18 L -25 18 Z" fill="#92400E" stroke="${COL.ink}" stroke-width="1.5"/>`;
    s += `<rect x="-3" y="-30" width="3" height="30" fill="${COL.ink}"/>`;
    s += `<path d="M 0 -30 L 18 -10 L 0 -10 Z" fill="#fff" stroke="${COL.ink}" stroke-width="1.2"/>`;
    s += `</g>`;
    // 라벨 박스
    s += `<rect x="14" y="40" width="76" height="35" fill="#fff" stroke="${COL.bear}" stroke-width="1.4" rx="4" opacity="0.95"/>`;
    s += txt(52, 53, '⛈️ 시장 폭풍', {color:COL.bear, size:12.5, weight:800, anchor:'middle'});
    s += txt(52, 67, '주가 -30%', {color:COL.bear, size:13, weight:900, anchor:'middle'});
    // 우측 박스
    s += `<rect x="${W-90}" y="40" width="76" height="35" fill="#fff" stroke="${COL.bull}" stroke-width="1.4" rx="4" opacity="0.95"/>`;
    s += txt(W-52, 53, '대응', {color:COL.bull, size:12.5, weight:800, anchor:'middle'});
    s += txt(W-52, 67, '현금 비중↑', {color:COL.bull, size:12, weight:800, anchor:'middle'});
    // 안내
    s += txt(W/2, 207, '👉 폭풍에도 침몰하지 않는 견고한 포트폴리오가 중요', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  fishing_patience: ()=>{
    // 낚시 = 인내. 좋은 매수 기회를 기다림
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '좋은 가격은 기다림에서 옵니다 (낚시 메타포)', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 하늘
    s += `<rect x="0" y="30" width="${W}" height="100" fill="#DBEAFE" opacity="0.5"/>`;
    // 해
    s += `<circle cx="${W-50}" cy="55" r="14" fill="#FBBF24"/>`;
    s += `<circle cx="${W-50}" cy="55" r="10" fill="#F59E0B"/>`;
    // 물 (수면 + 깊은물)
    s += `<rect x="0" y="130" width="${W}" height="90" fill="#3B82F6" opacity="0.25"/>`;
    s += `<line x1="0" y1="130" x2="${W}" y2="130" stroke="#1E40AF" stroke-width="1.6"/>`;
    // 수면 잔물결
    for(let i=0;i<8;i++){
      s += `<path d="M ${i*45+20} 130 Q ${i*45+30} 127 ${i*45+40} 130" fill="none" stroke="#1E40AF" stroke-width="0.8" opacity="0.5"/>`;
    }
    // 사람 (왼쪽, 낚시대 들고)
    function fisher(x, y){
      let p = '';
      // 머리
      p += `<circle cx="${x}" cy="${y-30}" r="8" fill="#FBBF24"/>`;
      // 몸 (앉아있는)
      p += `<rect x="${x-9}" y="${y-22}" width="18" height="22" fill="#3B82F6" rx="2"/>`;
      // 다리 (앉음)
      p += `<rect x="${x-9}" y="${y}" width="6" height="14" fill="#1E40AF" rx="2"/>`;
      p += `<rect x="${x+3}" y="${y}" width="6" height="14" fill="#1E40AF" rx="2"/>`;
      // 모자
      p += `<ellipse cx="${x}" cy="${y-37}" rx="11" ry="3" fill="#92400E"/>`;
      p += `<rect x="${x-5}" y="${y-42}" width="10" height="6" fill="#92400E" rx="1"/>`;
      // 낚시대
      p += `<line x1="${x+5}" y1="${y-16}" x2="${x+90}" y2="${y-50}" stroke="#92400E" stroke-width="2" stroke-linecap="round"/>`;
      // 낚싯줄 (수면 아래로 내려감)
      p += `<line x1="${x+90}" y1="${y-50}" x2="${x+95}" y2="${y+30}" stroke="${COL.ink}" stroke-width="0.8"/>`;
      // 낚시바늘 (물고기 옆)
      p += `<path d="M ${x+95} ${y+30} Q ${x+97} ${y+34} ${x+93} ${y+34}" fill="none" stroke="${COL.ink}" stroke-width="1"/>`;
      return p;
    }
    s += fisher(50, 130);
    // 물고기들 (저점 = 매수 타이밍)
    function fish(x, y, color, label){
      let p = '';
      // 몸통
      p += `<ellipse cx="${x}" cy="${y}" rx="14" ry="7" fill="${color}" stroke="${COL.ink}" stroke-width="1.2"/>`;
      // 꼬리
      p += `<polygon points="${x+12},${y} ${x+22},${y-7} ${x+22},${y+7}" fill="${color}" stroke="${COL.ink}" stroke-width="1.2"/>`;
      // 눈
      p += `<circle cx="${x-7}" cy="${y-2}" r="2" fill="#fff"/>`;
      p += `<circle cx="${x-7}" cy="${y-2}" r="1" fill="#000"/>`;
      // 라벨
      if(label) p += `<text x="${x}" y="${y+18}" font-size="11" font-weight="800" fill="${color}" text-anchor="middle">${label}</text>`;
      return p;
    }
    s += fish(150, 175, COL.bull, '좋은 매수');
    s += fish(255, 195, COL.bull, '');
    // 미끼 (낚시바늘 위치)
    s += `<circle cx="145" cy="160" r="3.5" fill="#F59E0B"/>`;
    // 우측 메시지 박스
    s += `<rect x="${W-110}" y="35" width="100" height="58" fill="#fff" stroke="${COL.bull}" stroke-width="1.4" rx="4" opacity="0.95"/>`;
    s += txt(W-60, 50, '🎣 인내', {color:COL.bull, size:13, weight:800, anchor:'middle'});
    s += txt(W-60, 65, '✓ 큰 하락 대기', {color:COL.bull, size:11.5, weight:700, anchor:'middle'});
    s += txt(W-60, 78, '✓ 적정 가격까지', {color:COL.bull, size:11.5, weight:700, anchor:'middle'});
    s += txt(W-60, 89, '✓ 충동 매수 NO', {color:COL.bear, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  // ====== 섹터 종합 일러스트 ======
  sector_battery: ()=>{
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '🔋 2차전지 산업 — 전기차·ESS 수요 증가', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 큰 배터리 아이콘 (좌측)
    const bx = 50;
    s += `<rect x="${bx-14}" y="60" width="28" height="55" fill="#34D399" stroke="${COL.ink}" stroke-width="1.6" rx="3"/>`;
    s += `<rect x="${bx-7}" y="56" width="14" height="6" fill="${COL.ink}" rx="1"/>`;
    // 충전 표시
    s += `<text x="${bx}" y="93" font-size="20" font-weight="900" fill="#fff" text-anchor="middle">⚡</text>`;
    s += txt(bx, 130, '리튬 배터리', {color:'#047857', size:12, weight:800, anchor:'middle'});
    // 밸류체인 (좌→우 흐름)
    const chainY = 75;
    const stages = [
      {x:115, label:'소재', co:'에코프로\n비엠', color:'#A78BFA'},
      {x:175, label:'셀', co:'LG엔솔\n삼성SDI', color:'#3B82F6'},
      {x:235, label:'모듈', co:'SK온', color:'#F59E0B'},
      {x:295, label:'완성차', co:'현대차\n테슬라', color:'#EF4444'},
    ];
    stages.forEach((st,i)=>{
      s += `<rect x="${st.x-22}" y="${chainY-12}" width="44" height="22" fill="${st.color}" opacity="0.85" rx="4" stroke="${COL.ink}" stroke-width="0.8"/>`;
      s += txt(st.x, chainY+3, st.label, {color:'#fff', size:13, weight:800, anchor:'middle'});
      // 회사
      const lines = st.co.split('\n');
      lines.forEach((ln,j)=>{
        s += txt(st.x, chainY+22+j*11, ln, {color:st.color, size:11, weight:700, anchor:'middle'});
      });
      // 화살표
      if(i<stages.length-1){
        s += `<line x1="${st.x+22}" y1="${chainY-1}" x2="${stages[i+1].x-22}" y2="${chainY-1}" stroke="${COL.muted}" stroke-width="1.4" marker-end="url(#bChain)"/>`;
      }
    });
    s += `<defs><marker id="bChain" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="${COL.muted}"/></marker></defs>`;
    s += txt(W/2, 165, '👉 핵심 지표: 수주잔고, 영업이익률, 고객사 믹스', {color:COL.ink, weight:800, size:12, anchor:'middle'});
    s += txt(W/2, 184, '⚠️ 리스크: 증설 과잉 · 원재료 가격 · 고객사 의존', {color:COL.bear, size:11.5, weight:700, anchor:'middle'});
    s += txt(W/2, 200, '🌱 성장 동력: EV 수요 + 美 IRA 정책 + ESS 확대', {color:COL.bull, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  sector_bio: ()=>{
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '🧬 바이오 — 신약·CDMO·진단·바이오시밀러', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // DNA 나선 (좌측)
    const dx = 50, dy0 = 50, dy1 = 145;
    for(let i=0;i<10;i++){
      const t = i/9;
      const y = dy0 + (dy1-dy0)*t;
      const xL = dx - 18*Math.sin(t*Math.PI*3);
      const xR = dx + 18*Math.sin(t*Math.PI*3);
      s += `<line x1="${xL}" y1="${y}" x2="${xR}" y2="${y}" stroke="#10B981" stroke-width="1.5"/>`;
      s += `<circle cx="${xL}" cy="${y}" r="3" fill="#10B981"/>`;
      s += `<circle cx="${xR}" cy="${y}" r="3" fill="#3B82F6"/>`;
    }
    s += txt(dx, 165, 'DNA / 신약', {color:'#047857', size:12, weight:800, anchor:'middle'});
    // 4개 카테고리 박스
    const cats = [
      {x:130, label:'CDMO', co:'삼성바이오\n로직스', color:'#3B82F6', desc:'위탁 생산'},
      {x:200, label:'신약', co:'HLB\nSK바이오', color:'#EF4444', desc:'개발·임상'},
      {x:270, label:'바이오시밀러', co:'셀트리온', color:'#10B981', desc:'복제약'},
    ];
    cats.forEach(c=>{
      s += `<rect x="${c.x-30}" y="55" width="60" height="40" fill="${c.color}" opacity="0.15" stroke="${c.color}" stroke-width="1.5" rx="4"/>`;
      s += txt(c.x, 70, c.label, {color:c.color, size:12.5, weight:800, anchor:'middle'});
      s += txt(c.x, 84, c.desc, {color:c.color, size:11, weight:700, anchor:'middle'});
      const lines = c.co.split('\n');
      lines.forEach((ln,j)=>{
        s += txt(c.x, 106+j*11, ln, {color:'#0F172A', size:11.5, weight:700, anchor:'middle'});
      });
    });
    // 타임라인 (임상 1상 → 3상)
    s += `<line x1="115" y1="155" x2="295" y2="155" stroke="${COL.muted}" stroke-width="1.4"/>`;
    ['1상','2상','3상','출시'].forEach((p,i)=>{
      const x = 115 + i*60;
      s += `<circle cx="${x}" cy="155" r="4" fill="${i<3?COL.muted:COL.bull}"/>`;
      s += txt(x, 170, p, {color:i<3?COL.muted:COL.bull, size:10.5, weight:800, anchor:'middle'});
    });
    s += txt(W/2, 187, '⚠️ 리스크: 임상 실패 · 허가 지연 · 기대감 선반영', {color:COL.bear, size:11.5, weight:700, anchor:'middle'});
    s += txt(W/2, 202, '👉 체크: 현금 보유 · 파이프라인 단계 · 기술료 수익', {color:COL.ink, weight:800, size:11.5, anchor:'middle'});
    return svgWrap(s);
  },

  sector_nuclear: ()=>{
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '☢️ 원전 — SMR·수출·정책 수혜', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 원자력 발전소 (cooling tower 2개)
    function tower(cx, cy, scale){
      let p = '';
      const w = 28*scale, h = 60*scale;
      // 곡선 형태 cooling tower (위 좁고 아래 넓음)
      p += `<path d="M ${cx-w/3} ${cy-h} L ${cx-w/2} ${cy} L ${cx+w/2} ${cy} L ${cx+w/3} ${cy-h} Z" fill="#94A3B8" stroke="${COL.ink}" stroke-width="1.4"/>`;
      // 김 (steam)
      p += `<ellipse cx="${cx-3}" cy="${cy-h-5}" rx="${w/3}" ry="4" fill="#F1F5F9" opacity="0.9"/>`;
      p += `<ellipse cx="${cx+5}" cy="${cy-h-12}" rx="${w/4}" ry="3" fill="#F1F5F9" opacity="0.7"/>`;
      // 가로줄
      p += `<line x1="${cx-w/2.2}" y1="${cy-h*0.3}" x2="${cx+w/2.2}" y2="${cy-h*0.3}" stroke="${COL.ink}" stroke-width="0.6"/>`;
      return p;
    }
    s += tower(85, 130, 1.0);
    s += tower(135, 130, 0.8);
    // 원전 표시
    s += `<circle cx="60" cy="155" r="14" fill="#FBBF24" stroke="${COL.ink}" stroke-width="1.4"/>`;
    s += txt(60, 159, '☢', {size:18, anchor:'middle'});
    // 원전 산업 구조
    s += `<rect x="190" y="50" width="155" height="65" fill="#FEF3C7" rx="5" stroke="#D97706" stroke-width="1.3"/>`;
    s += txt(267, 65, '원전 밸류체인', {color:'#92400E', size:12.5, weight:800, anchor:'middle'});
    [
      {y:80, label:'설계', co:'한전기술'},
      {y:92, label:'건설·기자재', co:'두산에너빌리티'},
      {y:104, label:'운영·정비', co:'한전KPS'},
    ].forEach(r=>{
      s += `<rect x="195" y="${r.y-7}" width="56" height="11" fill="#FBBF24" opacity="0.4" rx="2"/>`;
      s += txt(223, r.y+2, r.label, {color:'#92400E', size:11, weight:800, anchor:'middle'});
      s += txt(257, r.y+2, '→ ' + r.co, {color:'#7C2D12', size:11, weight:700});
    });
    // 핵심 포인트
    s += txt(W/2, 138, '🌱 성장 동력: SMR (소형원전) · 수출 · 탄소중립', {color:COL.bull, size:11.5, weight:700, anchor:'middle'});
    s += txt(W/2, 158, '👉 핵심 지표: 수주잔고 · 프로젝트 진행률 · 수익성', {color:COL.ink, weight:800, size:12, anchor:'middle'});
    s += txt(W/2, 178, '⚠️ 리스크: 정책 변경 · 발주 지연 · 기대감 과열', {color:COL.bear, size:11.5, weight:700, anchor:'middle'});
    s += txt(W/2, 198, '대표 종목: 두산에너빌리티 · 한전기술 · 한전KPS', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  sector_power: ()=>{
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '⚡ 전력기기 — 美 노후 변압기 교체·전력망 확장', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 송전탑
    function pylon(cx, cy){
      let p = '';
      // 좌우 다리 (역삼각형 격자)
      p += `<line x1="${cx-15}" y1="${cy+30}" x2="${cx-3}" y2="${cy-25}" stroke="${COL.ink}" stroke-width="1.6"/>`;
      p += `<line x1="${cx+15}" y1="${cy+30}" x2="${cx+3}" y2="${cy-25}" stroke="${COL.ink}" stroke-width="1.6"/>`;
      // 격자
      for(let i=0;i<4;i++){
        p += `<line x1="${cx-12+i*3}" y1="${cy+20-i*15}" x2="${cx+12-i*3}" y2="${cy+20-i*15}" stroke="${COL.ink}" stroke-width="0.8"/>`;
      }
      // 가로 팔
      p += `<line x1="${cx-13}" y1="${cy-22}" x2="${cx+13}" y2="${cy-22}" stroke="${COL.ink}" stroke-width="1.5"/>`;
      p += `<line x1="${cx-10}" y1="${cy-15}" x2="${cx+10}" y2="${cy-15}" stroke="${COL.ink}" stroke-width="1.2"/>`;
      // 절연체 (전선 매다는 점)
      [-13,-10,10,13].forEach(dx=>{
        p += `<circle cx="${cx+dx}" cy="${cy-22}" r="1.6" fill="${COL.bear}"/>`;
      });
      return p;
    }
    s += pylon(70, 110);
    s += pylon(180, 110);
    s += pylon(290, 110);
    // 전선
    [-22,-15].forEach(dy=>{
      s += `<path d="M 70 ${110+dy} Q 125 ${110+dy+8} 180 ${110+dy} T 290 ${110+dy}" fill="none" stroke="${COL.ink}" stroke-width="1"/>`;
    });
    // 변압기 (오른쪽 박스)
    s += `<rect x="225" y="135" width="35" height="22" fill="#3B82F6" stroke="${COL.ink}" stroke-width="1.4" rx="2"/>`;
    s += `<rect x="220" y="137" width="3" height="18" fill="${COL.ink}" rx="1"/>`;
    s += `<rect x="262" y="137" width="3" height="18" fill="${COL.ink}" rx="1"/>`;
    // 코일 표시
    s += `<line x1="232" y1="142" x2="252" y2="142" stroke="#fff" stroke-width="0.8"/>`;
    s += `<line x1="232" y1="146" x2="252" y2="146" stroke="#fff" stroke-width="0.8"/>`;
    s += `<line x1="232" y1="150" x2="252" y2="150" stroke="#fff" stroke-width="0.8"/>`;
    s += txt(243, 167, '변압기', {color:'#3B82F6', size:11.5, weight:800, anchor:'middle'});
    // 라벨
    s += txt(70, 168, '발전', {color:COL.muted, size:11.5, weight:800, anchor:'middle'});
    s += txt(180, 168, '송전', {color:COL.muted, size:11.5, weight:800, anchor:'middle'});
    s += txt(310, 168, '배전', {color:COL.muted, size:11.5, weight:800, anchor:'middle'});
    // 핵심 메시지
    s += txt(W/2, 188, '대표 종목: HD현대일렉트릭 · 효성중공업 · LS ELECTRIC', {color:COL.ink, weight:800, size:12, anchor:'middle'});
    s += txt(W/2, 205, '👉 수주잔고 + 수출 비중 + 환율 효과 함께 보기', {color:COL.bull, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  sector_space: ()=>{
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '🚀 우주·로켓 — 발사체·위성·정부 프로젝트', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 별 배경
    [[40,55],[80,40],[300,50],[320,75],[280,60],[55,75],[330,40]].forEach(p=>{
      s += `<text x="${p[0]}" y="${p[1]}" font-size="11" fill="#94A3B8">★</text>`;
    });
    // 로켓 (가운데, 발사 중)
    const rx = 75;
    // 본체 (긴 캡슐)
    s += `<path d="M ${rx} 50 Q ${rx-12} 60 ${rx-12} 110 L ${rx-12} 145 L ${rx+12} 145 L ${rx+12} 110 Q ${rx+12} 60 ${rx} 50 Z" fill="#fff" stroke="${COL.ink}" stroke-width="1.6"/>`;
    // 창문
    s += `<circle cx="${rx}" cy="80" r="5" fill="#3B82F6" stroke="${COL.ink}" stroke-width="1"/>`;
    // 핀 (날개)
    s += `<polygon points="${rx-12},135 ${rx-22},150 ${rx-12},148" fill="${COL.bear}"/>`;
    s += `<polygon points="${rx+12},135 ${rx+22},150 ${rx+12},148" fill="${COL.bear}"/>`;
    // 화염 (로켓 아래)
    s += `<path d="M ${rx-8} 145 L ${rx-12} 175 L ${rx-4} 165 L ${rx} 180 L ${rx+4} 165 L ${rx+12} 175 L ${rx+8} 145 Z" fill="#F59E0B"/>`;
    s += `<path d="M ${rx-5} 145 L ${rx-7} 165 L ${rx} 158 L ${rx+7} 165 L ${rx+5} 145 Z" fill="#FBBF24"/>`;
    // 연기
    s += `<ellipse cx="${rx-15}" cy="195" rx="20" ry="6" fill="#CBD5E1" opacity="0.7"/>`;
    s += `<ellipse cx="${rx+15}" cy="195" rx="20" ry="6" fill="#CBD5E1" opacity="0.7"/>`;
    // 위성 (오른쪽 위)
    const sx = 280, sy = 100;
    s += `<rect x="${sx-10}" y="${sy-5}" width="20" height="14" fill="#94A3B8" stroke="${COL.ink}" stroke-width="1.2" rx="1"/>`;
    s += `<rect x="${sx-22}" y="${sy-1}" width="10" height="6" fill="#FBBF24" stroke="${COL.ink}" stroke-width="0.8"/>`;
    s += `<rect x="${sx+12}" y="${sy-1}" width="10" height="6" fill="#FBBF24" stroke="${COL.ink}" stroke-width="0.8"/>`;
    s += `<line x1="${sx-22}" y1="${sy+2}" x2="${sx+22}" y2="${sy+2}" stroke="${COL.ink}" stroke-width="0.5"/>`;
    s += txt(sx, sy+25, '위성', {color:COL.muted, size:11, weight:800, anchor:'middle'});
    // 산업 구조 박스 (오른쪽)
    s += `<rect x="155" y="55" width="100" height="65" fill="#EEF2FF" rx="5" stroke="#6366F1" stroke-width="1.3"/>`;
    s += txt(205, 70, '우주 밸류체인', {color:'#4338CA', size:12.5, weight:800, anchor:'middle'});
    s += txt(165, 87, '🚀 발사체', {color:'#4338CA', size:11.5, weight:700});
    s += txt(165, 100, '🛰️ 위성·통신', {color:'#4338CA', size:11.5, weight:700});
    s += txt(165, 113, '📡 지상 시스템', {color:'#4338CA', size:11.5, weight:700});
    // 라벨
    s += txt(W/2, 167, '대표 종목: 한국항공우주(KAI) · 쎄트렉아이', {color:COL.ink, weight:800, size:12, anchor:'middle'});
    s += txt(W/2, 184, '🌱 성장 동력: 정부 우주개발 + 위성 통신 + 우주 관광', {color:COL.bull, size:11.5, weight:700, anchor:'middle'});
    s += txt(W/2, 201, '⚠️ 리스크: 발사 실패 · 일정 지연 · 테마 과열', {color:COL.bear, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  // ====== 실생활 비유 일러스트 ======
  per_real_estate: ()=>{
    // PER = 부동산 임대 수익률 비유
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, 'PER = "몇 년이면 본전?" — 부동산 vs 주식', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 좌측: 부동산
    function building(cx, cy){
      let p = '';
      p += `<rect x="${cx-25}" y="${cy-50}" width="50" height="60" fill="#94A3B8" stroke="${COL.ink}" stroke-width="1.5" rx="2"/>`;
      // 창문
      for(let r=0;r<4;r++){
        for(let c=0;c<3;c++){
          p += `<rect x="${cx-19+c*14}" y="${cy-44+r*13}" width="8" height="8" fill="#FBBF24" rx="1"/>`;
        }
      }
      // 지붕
      p += `<polygon points="${cx-28},${cy-50} ${cx},${cy-65} ${cx+28},${cy-50}" fill="#7C2D12" stroke="${COL.ink}" stroke-width="1.4"/>`;
      return p;
    }
    s += building(70, 130);
    s += `<rect x="35" y="135" width="70" height="14" fill="#fff" stroke="${COL.muted}" stroke-width="0.8" rx="2"/>`;
    s += txt(70, 145, '🏠 아파트', {color:COL.ink, size:12, weight:800, anchor:'middle'});
    // 부동산 정보
    s += `<rect x="14" y="155" width="120" height="48" fill="#F1F5F9" rx="4"/>`;
    s += txt(74, 168, '가격: 10억원', {color:COL.ink, size:12, weight:800, anchor:'middle'});
    s += txt(74, 182, '월세: 매월 100만', {color:COL.bull, size:12, weight:700, anchor:'middle'});
    s += txt(74, 196, '연 수입: 1,200만', {color:COL.bull, size:12, weight:700, anchor:'middle'});
    // VS 가운데
    s += `<rect x="148" y="100" width="64" height="36" fill="${COL.bull}" opacity="0.12" rx="6"/>`;
    s += txt(180, 117, '같은 식', {color:COL.bull, size:13, weight:800, anchor:'middle'});
    s += txt(180, 130, 'PER ≈ 83', {color:COL.bull, size:12.5, weight:800, anchor:'middle'});
    // 우측: 주식
    function stockTicker(cx, cy){
      let p = '';
      p += `<rect x="${cx-30}" y="${cy-30}" width="60" height="42" fill="#1F2937" stroke="${COL.ink}" stroke-width="1.5" rx="3"/>`;
      // 가격 라인
      p += `<polyline points="${cx-25},${cy-15} ${cx-15},${cy-20} ${cx-5},${cy-12} ${cx+8},${cy-22} ${cx+22},${cy-18}" fill="none" stroke="#34D399" stroke-width="1.4"/>`;
      // 가격 표시
      p += `<text x="${cx}" y="${cy-3}" font-size="13" font-weight="900" fill="#34D399" text-anchor="middle">+2.1%</text>`;
      return p;
    }
    s += stockTicker(290, 130);
    s += `<rect x="255" y="135" width="70" height="14" fill="#fff" stroke="${COL.muted}" stroke-width="0.8" rx="2"/>`;
    s += txt(290, 145, '📈 삼성전자', {color:COL.ink, size:12, weight:800, anchor:'middle'});
    // 주식 정보
    s += `<rect x="234" y="155" width="120" height="48" fill="#F1F5F9" rx="4"/>`;
    s += txt(294, 168, '주가: 71,400원', {color:COL.ink, size:12, weight:800, anchor:'middle'});
    s += txt(294, 182, 'EPS: 5,800원', {color:COL.bull, size:12, weight:700, anchor:'middle'});
    s += txt(294, 196, 'PER = 12.3', {color:COL.bull, size:12, weight:700, anchor:'middle'});
    // 메시지
    s += txt(W/2, 60, '👉 PER이 작을수록 "원금 회수 빠름" = 저평가', {color:COL.bull, size:12.5, weight:800, anchor:'middle'});
    s += txt(W/2, 78, 'PER 10 = 10년이면 이익으로 본전', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  eps_per_share: ()=>{
    // EPS = 1주가 1년 동안 번 돈
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, 'EPS = 1주가 1년 동안 번 돈', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 1주식 종이 (좌측)
    s += `<rect x="40" y="55" width="80" height="100" fill="#FFFBEB" stroke="${COL.ink}" stroke-width="1.6" rx="4"/>`;
    s += `<rect x="40" y="55" width="80" height="20" fill="#F59E0B" rx="4"/>`;
    s += txt(80, 70, '주식 1주', {color:'#fff', size:13, weight:800, anchor:'middle'});
    s += txt(80, 90, '🎫', {size:24, anchor:'middle'});
    s += txt(80, 115, '삼성전자', {color:COL.ink, size:12, weight:800, anchor:'middle'});
    s += txt(80, 130, '1주 보유', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    s += txt(80, 145, '👤 = 주주', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    // 화살표 + 1년 경과
    s += `<text x="135" y="100" font-size="20" fill="${COL.muted}">→</text>`;
    s += txt(155, 90, '1년 후', {color:COL.muted, size:11.5, weight:800});
    // 결과: 5,800원 받음
    s += `<rect x="200" y="55" width="140" height="100" fill="#ECFDF5" stroke="${COL.bull}" stroke-width="1.6" rx="4"/>`;
    s += txt(270, 75, '내가 번 돈', {color:COL.bull, size:13, weight:800, anchor:'middle'});
    s += txt(270, 105, '5,800', {color:COL.bull, size:28, weight:900, anchor:'middle'});
    s += txt(270, 122, '원', {color:COL.bull, size:13, weight:800, anchor:'middle'});
    s += txt(270, 140, '(1주 × 1년)', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    // 막대 차트 - 연도별 EPS 증가
    s += `<rect x="20" y="165" width="${W-40}" height="42" fill="#F8FAFC" rx="3"/>`;
    s += txt(W/2, 178, '꾸준히 늘어나는 EPS = 좋은 기업', {color:COL.bull, size:12, weight:800, anchor:'middle'});
    [
      {y:'2022', v:3500, h:8},
      {y:'2023', v:4200, h:11},
      {y:'2024', v:4900, h:14},
      {y:'2025', v:5800, h:18},
    ].forEach((d,i)=>{
      const x = 70 + i*65;
      s += `<rect x="${x-15}" y="${204-d.h}" width="30" height="${d.h}" fill="${COL.bull}" opacity="${0.5+i*0.15}" rx="1"/>`;
      s += txt(x, 213, d.y, {color:COL.muted, size:10, weight:700, anchor:'middle'});
    });
    return svgWrap(s);
  },

  etf_box: ()=>{
    // ETF = 여러 회사 한 장바구니
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, 'ETF = "여러 회사를 한 번에" 장바구니', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 장바구니
    const bx = 180, by = 110;
    s += `<path d="M ${bx-90} ${by-30} L ${bx-100} ${by+50} L ${bx+100} ${by+50} L ${bx+90} ${by-30} Z" fill="#FBBF24" opacity="0.85" stroke="${COL.ink}" stroke-width="2" stroke-linejoin="round"/>`;
    // 손잡이
    s += `<path d="M ${bx-60} ${by-30} Q ${bx-60} ${by-60} ${bx-30} ${by-60} L ${bx+30} ${by-60} Q ${bx+60} ${by-60} ${bx+60} ${by-30}" fill="none" stroke="#92400E" stroke-width="3"/>`;
    // 기업 종목 (장바구니 안)
    const items = [
      {x:bx-65, y:by, label:'삼성', color:'#3B82F6'},
      {x:bx-30, y:by-5, label:'SK하이', color:'#10B981'},
      {x:bx+5, y:by, label:'현대차', color:'#EF4444'},
      {x:bx+40, y:by-5, label:'LG엔솔', color:'#8B5CF6'},
      {x:bx+70, y:by, label:'NAVER', color:'#F59E0B'},
      {x:bx-50, y:by+25, label:'KB금융', color:'#EC4899'},
      {x:bx-15, y:by+30, label:'카카오', color:'#FBBF24'},
      {x:bx+25, y:by+25, label:'POSCO', color:'#06B6D4'},
      {x:bx+58, y:by+30, label:'셀트', color:'#8B5CF6'},
    ];
    items.forEach(it=>{
      s += `<circle cx="${it.x}" cy="${it.y}" r="13" fill="${it.color}" stroke="#fff" stroke-width="1.5"/>`;
      s += txt(it.x, it.y+3, it.label.substring(0,2), {color:'#fff', size:10.5, weight:800, anchor:'middle'});
    });
    // 라벨
    s += `<rect x="120" y="${by+57}" width="120" height="22" fill="${COL.bull}" rx="11"/>`;
    s += txt(180, by+72, 'KODEX 200', {color:'#fff', size:13, weight:900, anchor:'middle'});
    // 메시지
    s += txt(W/2, 195, '👉 한 종목 사면 = 200개 회사 분산 투자 자동', {color:COL.bull, size:12, weight:800, anchor:'middle'});
    s += txt(W/2, 210, '✓ 분산 효과 ✓ 운용비 저렴 ✓ 초보자 친화', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  dividend_bonus: ()=>{
    // 배당 = 정기 보너스
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '배당 = 회사가 주주에게 주는 정기 보너스', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 캘린더 (분기 배당 표시)
    s += `<rect x="20" y="40" width="${W-40}" height="120" fill="#fff" stroke="${COL.ink}" stroke-width="1.5" rx="6"/>`;
    s += `<rect x="20" y="40" width="${W-40}" height="22" fill="${COL.bear}" rx="6"/>`;
    s += txt(W/2, 56, '2026년 배당 일정', {color:'#fff', size:13, weight:800, anchor:'middle'});
    // 4개 분기
    const quarters = [
      {x:60, label:'1분기', date:'4/15', amt:'1,200원', month:'4'},
      {x:140, label:'2분기', date:'7/15', amt:'1,200원', month:'7'},
      {x:220, label:'3분기', date:'10/15', amt:'1,200원', month:'10'},
      {x:300, label:'4분기', date:'1/15', amt:'1,500원', month:'1'},
    ];
    quarters.forEach(q=>{
      // 동전 아이콘
      s += `<circle cx="${q.x}" cy="90" r="20" fill="#FBBF24" stroke="#D97706" stroke-width="1.6"/>`;
      s += txt(q.x, 96, '₩', {color:'#92400E', size:18, weight:900, anchor:'middle'});
      s += txt(q.x, 122, q.label, {color:COL.ink, size:12, weight:800, anchor:'middle'});
      s += txt(q.x, 134, q.date, {color:COL.muted, size:11, weight:700, anchor:'middle'});
      s += txt(q.x, 148, q.amt, {color:COL.bull, size:12, weight:800, anchor:'middle'});
    });
    // 합산
    s += `<rect x="20" y="170" width="${W-40}" height="38" fill="${COL.bull}" opacity="0.1" rx="4"/>`;
    s += txt(W/2, 184, '연간 배당 = 5,100원/주', {color:COL.bull, size:13, weight:800, anchor:'middle'});
    s += txt(W/2, 200, '주가 71,400원 기준 → 배당수익률 ≈ 7.1%', {color:COL.bull, size:12, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  growth_sapling: ()=>{
    // 성장주 = 어린 묘목 → 큰 나무
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '성장주 = 어린 묘목이 큰 나무로 자란다', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 4단계 성장 (씨앗 → 묘목 → 작은나무 → 큰나무)
    function tree(cx, ground, h, leaves, label, year, mcap){
      let p = '';
      // 줄기
      p += `<rect x="${cx-2}" y="${ground-h}" width="4" height="${h}" fill="#7C2D12"/>`;
      // 잎
      if(leaves > 0){
        p += `<circle cx="${cx}" cy="${ground-h-leaves/2}" r="${leaves}" fill="#10B981"/>`;
        if(leaves > 12){
          p += `<circle cx="${cx-leaves*0.6}" cy="${ground-h-leaves*0.3}" r="${leaves*0.7}" fill="#10B981" opacity="0.85"/>`;
          p += `<circle cx="${cx+leaves*0.6}" cy="${ground-h-leaves*0.3}" r="${leaves*0.7}" fill="#10B981" opacity="0.85"/>`;
        }
      }
      // 라벨
      p += `<text x="${cx}" y="${ground+15}" font-size="11.5" font-weight="800" fill="${COL.ink}" text-anchor="middle">${label}</text>`;
      p += `<text x="${cx}" y="${ground+27}" font-size="11" font-weight="700" fill="${COL.muted}" text-anchor="middle">${year}</text>`;
      p += `<text x="${cx}" y="${ground+39}" font-size="12" font-weight="800" fill="${COL.bull}" text-anchor="middle">${mcap}</text>`;
      return p;
    }
    const ground = 130;
    s += tree(60, ground, 4, 4, '🌱 씨앗', '창업', '1억');
    s += tree(140, ground, 18, 8, '🌿 묘목', '+3년', '50억');
    s += tree(225, ground, 35, 14, '🌳 작은 나무', '+5년', '500억');
    s += tree(310, ground, 60, 22, '🌲 거목', '+10년', '5조');
    // 바닥
    s += `<line x1="20" y1="${ground+1}" x2="${W-20}" y2="${ground+1}" stroke="${COL.ink}" stroke-width="1.4"/>`;
    s += `<rect x="20" y="${ground+1}" width="${W-40}" height="6" fill="#7C2D12" opacity="0.4"/>`;
    // 메시지
    s += `<rect x="20" y="180" width="${W-40}" height="32" fill="${COL.bull}" opacity="0.1" rx="4"/>`;
    s += txt(W/2, 195, '📈 성장주 = 미래 성장에 베팅 (탄력 ↑ 변동성 ↑)', {color:COL.bull, size:12.5, weight:800, anchor:'middle'});
    s += txt(W/2, 207, '예: 한화에어로스페이스 · 반도체 장비 · 바이오 등', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  bluechip_solid: ()=>{
    // 우량주 = 견고한 빌딩
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '우량주 (Blue Chip) = 견고하고 안정된 회사', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 큰 견고한 빌딩 (가운데)
    const bx = 180, by = 145;
    // 빌딩 본체
    s += `<rect x="${bx-50}" y="${by-90}" width="100" height="120" fill="#1E40AF" stroke="${COL.ink}" stroke-width="2" rx="3"/>`;
    // 창문 격자
    for(let r=0;r<6;r++){
      for(let c=0;c<5;c++){
        s += `<rect x="${bx-43+c*18}" y="${by-82+r*18}" width="14" height="13" fill="#FBBF24" opacity="${0.6+r*0.05}" rx="1"/>`;
      }
    }
    // 옥상
    s += `<rect x="${bx-25}" y="${by-105}" width="50" height="15" fill="#374151" rx="2"/>`;
    s += `<rect x="${bx-3}" y="${by-115}" width="6" height="12" fill="${COL.ink}"/>`;
    // 입구
    s += `<rect x="${bx-15}" y="${by-15}" width="30" height="45" fill="#1F2937"/>`;
    s += `<rect x="${bx-12}" y="${by-12}" width="24" height="38" fill="#3B82F6" opacity="0.5"/>`;
    // 좌우 보조 빌딩 (작게)
    s += `<rect x="${bx-100}" y="${by-50}" width="40" height="80" fill="#3B82F6" stroke="${COL.ink}" stroke-width="1.4" rx="2"/>`;
    for(let r=0;r<4;r++) for(let c=0;c<3;c++)
      s += `<rect x="${bx-95+c*12}" y="${by-43+r*15}" width="8" height="8" fill="#FBBF24" opacity="0.7"/>`;
    s += `<rect x="${bx+60}" y="${by-50}" width="40" height="80" fill="#3B82F6" stroke="${COL.ink}" stroke-width="1.4" rx="2"/>`;
    for(let r=0;r<4;r++) for(let c=0;c<3;c++)
      s += `<rect x="${bx+65+c*12}" y="${by-43+r*15}" width="8" height="8" fill="#FBBF24" opacity="0.7"/>`;
    // 메달 표시 (1등)
    s += `<circle cx="${bx}" cy="50" r="18" fill="#FBBF24" stroke="${COL.ink}" stroke-width="1.6"/>`;
    s += txt(bx, 56, '🏆', {size:18, anchor:'middle'});
    s += txt(bx, 30, '시총 1등', {color:COL.muted, size:11, weight:800, anchor:'middle'});
    // 라벨 (우측 빌딩 옆 — 하단 위치)
    s += `<rect x="20" y="${by+30}" width="${W-40}" height="36" fill="#F1F5F9" rx="4"/>`;
    s += txt(W/2, by+44, '✓ 시총 큼 ✓ 안정적 매출 ✓ 꾸준한 배당', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    s += txt(W/2, by+58, '예: 삼성전자 · KB금융 · 현대차 · POSCO', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  // ====== 추가 메타포 일러스트 ======
  lighthouse_index: ()=>{
    // 등대 = 시장 지표 (코스피, 다우, 나스닥)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '시장 지수 = 시장 흐름의 등대 🏮', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 바다 (하단)
    s += `<rect x="0" y="170" width="${W}" height="50" fill="#1E40AF" opacity="0.4"/>`;
    // 파도
    for(let i=0;i<6;i++){
      s += `<path d="M ${i*60+15} 170 Q ${i*60+30} 165 ${i*60+45} 170" fill="none" stroke="#1E40AF" stroke-width="0.8" opacity="0.7"/>`;
    }
    // 절벽 (등대 받침)
    s += `<polygon points="155,170 155,135 165,125 195,125 205,135 205,170" fill="#94A3B8" stroke="${COL.ink}" stroke-width="1.4"/>`;
    s += `<line x1="160" y1="155" x2="200" y2="155" stroke="${COL.ink}" stroke-width="0.6" opacity="0.5"/>`;
    // 등대 본체
    const lx = 180;
    s += `<rect x="${lx-15}" y="55" width="30" height="70" fill="#fff" stroke="${COL.ink}" stroke-width="1.6"/>`;
    // 빨강 줄무늬
    s += `<rect x="${lx-15}" y="65" width="30" height="10" fill="${COL.bear}"/>`;
    s += `<rect x="${lx-15}" y="95" width="30" height="10" fill="${COL.bear}"/>`;
    // 등대 위 등 (전망대)
    s += `<rect x="${lx-12}" y="40" width="24" height="15" fill="#FBBF24" stroke="${COL.ink}" stroke-width="1.4"/>`;
    s += `<polygon points="${lx-15},40 ${lx},27 ${lx+15},40" fill="${COL.bear}" stroke="${COL.ink}" stroke-width="1.4"/>`;
    s += `<rect x="${lx-1}" y="22" width="2" height="6" fill="${COL.ink}"/>`;
    // 빛 (좌우 부채꼴)
    s += `<path d="M ${lx} 47 L 50 75 L 50 30 Z" fill="#FBBF24" opacity="0.4"/>`;
    s += `<path d="M ${lx} 47 L 310 75 L 310 30 Z" fill="#FBBF24" opacity="0.4"/>`;
    // 라벨 (좌측 = 코스피, 우측 = 다우)
    s += `<rect x="20" y="50" width="65" height="35" fill="#fff" stroke="${COL.bull}" stroke-width="1.4" rx="4"/>`;
    s += txt(52, 64, '🇰🇷 KOSPI', {color:COL.bull, size:12.5, weight:800, anchor:'middle'});
    s += txt(52, 78, '한국 시장', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    s += `<rect x="${W-85}" y="50" width="65" height="35" fill="#fff" stroke="${COL.bear}" stroke-width="1.4" rx="4"/>`;
    s += txt(W-52, 64, '🇺🇸 다우/S&P', {color:COL.bear, size:12, weight:800, anchor:'middle'});
    s += txt(W-52, 78, '미국 시장', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    // 메시지
    s += `<rect x="20" y="${H-40}" width="${W-40}" height="36" fill="#FEF3C7" rx="3"/>`;
    s += txt(W/2, H-26, '👉 시장 평균 = 내 종목과 비교할 기준점', {color:'#92400E', size:12, weight:800, anchor:'middle'});
    s += txt(W/2, H-12, '코스피 -2% 인데 내 종목 -10% = 더 빠진 상태', {color:'#92400E', size:11, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  report_card: ()=>{
    // 실적 = 기업 성적표 (3컬럼: 항목 / 직전→이번 / 등급)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '📋 분기 실적 = 기업의 성적표', {color:COL.ink, weight:800, size:14, anchor:'middle'});
    const px = 25, py = 42, pw = W-50, ph = 195;
    s += `<rect x="${px}" y="${py}" width="${pw}" height="${ph}" fill="#FFFBEB" stroke="${COL.ink}" stroke-width="1.6" rx="6"/>`;
    s += `<rect x="${px}" y="${py}" width="${pw}" height="32" fill="${COL.bear}" rx="6"/>`;
    s += txt(px+12, py+20, '삼성전자 Q4', {color:'#fff', size:12, weight:800});
    s += txt(px+pw-12, py+20, '⭐⭐⭐⭐', {color:'#FBBF24', size:13, weight:900, anchor:'end'});
    // 컬럼 헤더
    const colItem = px+15, colVal = px+pw-100, colGrade = px+pw-25;
    s += txt(colItem, py+48, '항목', {color:COL.muted, size:10, weight:800});
    s += txt(colVal, py+48, '직전 → 이번', {color:COL.muted, size:10, weight:800, anchor:'middle'});
    s += txt(colGrade, py+48, '등급', {color:COL.muted, size:10, weight:800, anchor:'middle'});
    s += `<line x1="${px+8}" y1="${py+54}" x2="${px+pw-8}" y2="${py+54}" stroke="${COL.grid}" stroke-width="1"/>`;
    const items = [
      {name:'매출액',     prev:'71조',   score:'74조',   grade:'A',  color:COL.bull},
      {name:'영업이익',   prev:'5.8조',  score:'6.5조',  grade:'A',  color:COL.bull},
      {name:'당기순이익', prev:'5.1조',  score:'5.5조',  grade:'B+', color:'#F59E0B'},
      {name:'EPS',       prev:'5,400', score:'5,800', grade:'A',  color:COL.bull},
      {name:'영업이익률', prev:'8.2%',  score:'8.8%',  grade:'A',  color:COL.bull},
    ];
    items.forEach((it,i)=>{
      const y = py+76+i*22;
      // 항목명
      s += txt(colItem, y, `${i+1}. ${it.name}`, {color:COL.ink, size:11, weight:800});
      // 직전 → 이번 (가운데 정렬)
      s += txt(colVal-30, y, it.prev, {color:COL.muted, size:10.5, weight:700, anchor:'middle'});
      s += txt(colVal, y, '→', {color:it.color, size:12, weight:800, anchor:'middle'});
      s += txt(colVal+28, y, it.score, {color:it.color, size:11, weight:800, anchor:'middle'});
      // 등급
      s += `<rect x="${colGrade-13}" y="${y-11}" width="26" height="16" fill="${it.color}" rx="3"/>`;
      s += txt(colGrade, y+1, it.grade, {color:'#fff', size:11, weight:900, anchor:'middle'});
      if(i<items.length-1)
        s += `<line x1="${px+8}" y1="${y+10}" x2="${px+pw-8}" y2="${y+10}" stroke="${COL.grid}" stroke-width="0.5"/>`;
    });
    s += `<rect x="${px+5}" y="${py+ph-26}" width="${pw-10}" height="20" fill="${COL.bull}" opacity="0.15" rx="3"/>`;
    s += txt(px+pw/2, py+ph-12, '✓ 종합: 우상향 견고 — Buy 의견 유지', {color:COL.bull, size:11.5, weight:800, anchor:'middle'});
    return svgWrap(s);
  },

  tax_scissors: ()=>{
    // 수수료/세금 = 가위
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '✂️ 수수료·세금 = 매번 잘려나가는 수익', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 큰 지폐 (왼쪽)
    const bx = 80, by = 105;
    s += `<rect x="${bx-50}" y="${by-30}" width="100" height="60" fill="#10B981" stroke="${COL.ink}" stroke-width="1.6" rx="4"/>`;
    s += `<circle cx="${bx}" cy="${by}" r="14" fill="#fff" opacity="0.85"/>`;
    s += txt(bx, by+5, '₩', {color:'#10B981', size:18, weight:900, anchor:'middle'});
    s += txt(bx-40, by-22, '원금 +5%', {color:'#fff', size:11, weight:800});
    s += txt(bx-40, by+25, '수익 발생', {color:'#fff', size:11, weight:700});
    // 절단 점선
    s += `<line x1="135" y1="${by-30}" x2="135" y2="${by+30}" stroke="${COL.bear}" stroke-width="1.5" stroke-dasharray="3 2"/>`;
    // 가위 (가운데)
    const sx = 175, sy = 105;
    // 손잡이 두 개
    s += `<circle cx="${sx-20}" cy="${sy-15}" r="10" fill="none" stroke="${COL.bear}" stroke-width="3"/>`;
    s += `<circle cx="${sx-20}" cy="${sy+15}" r="10" fill="none" stroke="${COL.bear}" stroke-width="3"/>`;
    // 칼날
    s += `<polygon points="${sx-10},${sy-15} ${sx+25},${sy-3} ${sx-10},${sy-7}" fill="${COL.muted}" stroke="${COL.ink}" stroke-width="1.2"/>`;
    s += `<polygon points="${sx-10},${sy+15} ${sx+25},${sy+3} ${sx-10},${sy+7}" fill="${COL.muted}" stroke="${COL.ink}" stroke-width="1.2"/>`;
    // 핀 (가운데)
    s += `<circle cx="${sx-10}" cy="${sy}" r="2.5" fill="${COL.ink}"/>`;
    // 잘려나간 조각들 (오른쪽)
    const fx = 270;
    const cuts = [
      {y:60, label:'증권사 수수료', amt:'-0.015%', w:75},
      {y:90, label:'유관기관 비용', amt:'-0.0036%', w:78},
      {y:120, label:'증권거래세', amt:'-0.20%', w:65},
      {y:150, label:'배당소득세', amt:'-15.4%', w:65},
    ];
    cuts.forEach(cu=>{
      s += `<rect x="${fx-cu.w/2}" y="${cu.y-9}" width="${cu.w}" height="18" fill="#FEE2E2" stroke="${COL.bear}" stroke-width="1.2" rx="3"/>`;
      s += txt(fx, cu.y-1, cu.label, {color:COL.bear, size:11, weight:800, anchor:'middle'});
      s += txt(fx, cu.y+9, cu.amt, {color:COL.bear, size:10.5, weight:700, anchor:'middle'});
    });
    // 메시지
    s += txt(W/2, H-12, '👉 짧게 사고팔수록 누적 수수료가 수익을 갉아먹음', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  stock_split: ()=>{
    // 액면분할 = 큰 피자 → 여러 조각
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '액면분할 = 큰 피자를 여러 조각으로', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 좌측: 큰 동전/주식 1개
    function bigCoin(cx, cy, r){
      let p = '';
      p += `<circle cx="${cx}" cy="${cy}" r="${r}" fill="#FBBF24" stroke="#D97706" stroke-width="2"/>`;
      p += `<circle cx="${cx}" cy="${cy}" r="${r-5}" fill="none" stroke="#92400E" stroke-width="1" stroke-dasharray="3 2"/>`;
      p += `<text x="${cx}" y="${cy+8}" font-size="22" font-weight="900" fill="#92400E" text-anchor="middle">₩</text>`;
      return p;
    }
    s += bigCoin(75, 110, 40);
    s += txt(75, 65, '분할 전', {color:COL.ink, size:12.5, weight:800, anchor:'middle'});
    s += txt(75, 165, '1주 = 50,000원', {color:COL.ink, size:12, weight:800, anchor:'middle'});
    s += txt(75, 178, '발행 100만 주', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    // 화살표
    s += `<line x1="120" y1="110" x2="170" y2="110" stroke="${COL.muted}" stroke-width="2" marker-end="url(#splitArr)"/>`;
    s += `<defs><marker id="splitArr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="${COL.muted}"/></marker></defs>`;
    s += txt(145, 100, '5:1 분할', {color:COL.muted, size:12, weight:800, anchor:'middle'});
    s += txt(145, 125, '✂', {color:COL.bear, size:14, weight:900, anchor:'middle'});
    // 우측: 작은 동전 5개 (분할 후)
    function smallCoin(cx, cy){
      return `<circle cx="${cx}" cy="${cy}" r="13" fill="#FBBF24" stroke="#D97706" stroke-width="1.4"/>` +
             `<text x="${cx}" y="${cy+5}" font-size="12" font-weight="900" fill="#92400E" text-anchor="middle">₩</text>`;
    }
    const positions = [[230,90],[265,80],[300,90],[230,130],[265,140],[300,130]];
    positions.slice(0,5).forEach(p => s += smallCoin(p[0], p[1]));
    s += txt(265, 65, '분할 후', {color:COL.ink, size:12.5, weight:800, anchor:'middle'});
    s += txt(265, 165, '1주 = 10,000원', {color:COL.bull, size:12, weight:800, anchor:'middle'});
    s += txt(265, 178, '발행 500만 주', {color:COL.bull, size:11.5, weight:700, anchor:'middle'});
    // 메시지
    s += `<rect x="20" y="${H-30}" width="${W-40}" height="22" fill="#F1F5F9" rx="3"/>`;
    s += txt(W/2, H-15, '✓ 시가총액 동일 ✓ 가격↓ 접근성↑ ✓ 거래 활성화', {color:COL.ink, size:11.5, weight:800, anchor:'middle'});
    return svgWrap(s);
  },

  hands_shake: ()=>{
    // 수주 계약 = 악수
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '수주 계약 = 두 회사의 약속 🤝', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 두 회사 박스
    function company(x, y, name, color, role){
      let p = '';
      p += `<rect x="${x-35}" y="${y-22}" width="70" height="42" fill="${color}" opacity="0.85" stroke="${COL.ink}" stroke-width="1.6" rx="6"/>`;
      // 빌딩 아이콘
      p += `<rect x="${x-12}" y="${y-15}" width="24" height="20" fill="#fff" rx="1"/>`;
      for(let r=0;r<2;r++) for(let c=0;c<3;c++)
        p += `<rect x="${x-9+c*8}" y="${y-12+r*8}" width="4" height="4" fill="${color}"/>`;
      p += `<text x="${x}" y="${y+15}" font-size="11.5" font-weight="800" fill="#fff" text-anchor="middle">${name}</text>`;
      p += `<text x="${x}" y="${y+30}" font-size="10.5" font-weight="700" fill="${color}" text-anchor="middle">${role}</text>`;
      return p;
    }
    s += company(70, 100, 'LS\nELECTRIC', '#3B82F6', '수주 받음');
    s += company(W-70, 100, '美 전력회사', '#10B981', '발주');
    // 가운데 악수
    const hx = W/2, hy = 100;
    s += `<circle cx="${hx}" cy="${hy}" r="32" fill="#FEF3C7" stroke="#D97706" stroke-width="2"/>`;
    s += `<text x="${hx}" y="${hy+8}" font-size="32" text-anchor="middle">🤝</text>`;
    // 화살표 양쪽
    s += `<line x1="105" y1="100" x2="${hx-32}" y2="100" stroke="${COL.muted}" stroke-width="1.4" stroke-dasharray="3 2"/>`;
    s += `<line x1="${hx+32}" y1="100" x2="${W-105}" y2="100" stroke="${COL.muted}" stroke-width="1.4" stroke-dasharray="3 2"/>`;
    // 계약 정보 박스
    s += `<rect x="40" y="160" width="${W-80}" height="50" fill="#fff" stroke="#D97706" stroke-width="1.5" rx="6"/>`;
    s += `<rect x="40" y="160" width="${W-80}" height="18" fill="#FEF3C7" rx="6"/>`;
    s += txt(W/2, 173, '📜 공시: 수주 계약 체결 안내', {color:'#92400E', size:12.5, weight:800, anchor:'middle'});
    s += txt(50, 191, '계약금액', {color:COL.muted, size:11, weight:700});
    s += txt(125, 191, '4,200억원', {color:COL.bull, size:12, weight:800});
    s += txt(200, 191, '기간', {color:COL.muted, size:11, weight:700});
    s += txt(230, 191, '2025~2028', {color:COL.bull, size:12, weight:800});
    s += txt(50, 205, '👉 매출 반영 시점·고객사 집중도까지 함께 봐야 정확', {color:COL.muted, size:11, weight:700});
    return svgWrap(s);
  },

  mountain_climb: ()=>{
    // 장기 투자 = 산 등반 (단계별)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '장기 투자 = 한 걸음씩 정상까지 🏔️', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 하늘 (그라디언트 대신 단색)
    s += `<rect x="0" y="30" width="${W}" height="120" fill="#DBEAFE" opacity="0.4"/>`;
    // 해
    s += `<circle cx="${W-50}" cy="55" r="14" fill="#FBBF24"/>`;
    // 큰 산 (배경)
    s += `<polygon points="0,170 80,80 130,120 200,40 260,90 ${W},170" fill="#94A3B8"/>`;
    // 정상 눈
    s += `<polygon points="180,55 200,40 220,55 215,70 200,60 185,70" fill="#fff"/>`;
    // 등반자 4명 (단계별)
    function climber(cx, cy, label, year, alt, color){
      let p = '';
      // 머리
      p += `<circle cx="${cx}" cy="${cy-10}" r="4" fill="${color}"/>`;
      // 몸
      p += `<line x1="${cx}" y1="${cy-6}" x2="${cx}" y2="${cy+5}" stroke="${color}" stroke-width="2.2" stroke-linecap="round"/>`;
      // 팔
      p += `<line x1="${cx-4}" y1="${cy-3}" x2="${cx-7}" y2="${cy+2}" stroke="${color}" stroke-width="2" stroke-linecap="round"/>`;
      p += `<line x1="${cx+4}" y1="${cy-3}" x2="${cx+8}" y2="${cy-1}" stroke="${color}" stroke-width="2" stroke-linecap="round"/>`;
      // 다리
      p += `<line x1="${cx-1}" y1="${cy+5}" x2="${cx-4}" y2="${cy+12}" stroke="${color}" stroke-width="2" stroke-linecap="round"/>`;
      p += `<line x1="${cx+1}" y1="${cy+5}" x2="${cx+4}" y2="${cy+12}" stroke="${color}" stroke-width="2" stroke-linecap="round"/>`;
      // 등반 도구 (지팡이)
      p += `<line x1="${cx+8}" y1="${cy-1}" x2="${cx+12}" y2="${cy+13}" stroke="#7C2D12" stroke-width="1.4"/>`;
      // 라벨
      p += `<text x="${cx}" y="${cy+25}" font-size="11" font-weight="800" fill="${COL.ink}" text-anchor="middle">${label}</text>`;
      p += `<text x="${cx}" y="${cy+36}" font-size="10.5" font-weight="700" fill="${color}" text-anchor="middle">${year}</text>`;
      return p;
    }
    s += climber(40, 165, '시작', '0년', '0%', COL.muted);
    s += climber(115, 130, '+1년', '+1년', '+10%', '#3B82F6');
    s += climber(170, 110, '+5년', '+5년', '+50%', '#10B981');
    s += climber(195, 50, '🎯 정상', '+20년', '+670%', COL.bull);
    // 깃발 (정상)
    s += `<line x1="200" y1="40" x2="200" y2="22" stroke="#7C2D12" stroke-width="2"/>`;
    s += `<polygon points="200,22 215,28 200,34" fill="${COL.bull}"/>`;
    // 화살표 곡선 (등반 경로)
    s += `<path d="M 50 165 Q 90 145 120 130 T 175 110 T 200 50" fill="none" stroke="${COL.bull}" stroke-width="1.4" stroke-dasharray="3 2"/>`;
    // 메시지
    s += `<rect x="20" y="${H-32}" width="${W-40}" height="26" fill="${COL.bull}" opacity="0.12" rx="3"/>`;
    s += txt(W/2, H-13, '👉 시간 + 인내 = 결국 정상 (복리의 힘)', {color:COL.bull, size:12.5, weight:800, anchor:'middle'});
    return svgWrap(s);
  },

  gap_up_down: ()=>{
    // 갭 상승 / 갭 하락 차트
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '갭 = 시가가 전날 종가에서 점프', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 좌측: 갭 상승
    function gapChart(x0, x1, isUp, title){
      let p = '';
      // 패널 베이스
      p += `<rect x="${x0}" y="40" width="${x1-x0}" height="155" fill="#fff" stroke="${COL.grid}" stroke-width="0.8" rx="4"/>`;
      // 그리드
      for(let i=1;i<4;i++){
        const y = 40 + (155)*i/4;
        p += `<line x1="${x0}" y1="${y}" x2="${x1}" y2="${y}" stroke="${COL.grid}" stroke-width="0.4"/>`;
      }
      // 캔들 5개 + 갭 + 다음 캔들
      const cw = 8;
      const baseY = isUp ? 130 : 80;
      const candles = [
        {x:x0+22, o:baseY, c:baseY-15, h:baseY-22, l:baseY+5, bull:true},
        {x:x0+45, o:baseY-15, c:baseY-25, h:baseY-30, l:baseY-12, bull:true},
        {x:x0+68, o:baseY-25, c:baseY-30, h:baseY-35, l:baseY-22, bull:true},
        {x:x0+91, o:baseY-30, c:baseY-32, h:baseY-37, l:baseY-28, bull:true},
      ];
      candles.forEach(cd=>{
        const color = cd.bull ? COL.bull : COL.bear;
        // 꼬리
        p += `<line x1="${cd.x}" y1="${cd.h}" x2="${cd.x}" y2="${cd.l}" stroke="${color}" stroke-width="1"/>`;
        // 몸통
        const top = Math.min(cd.o, cd.c);
        const h = Math.abs(cd.c - cd.o);
        p += `<rect x="${cd.x-cw/2}" y="${top}" width="${cw}" height="${h}" fill="${color}" rx="0.5"/>`;
      });
      // 갭 영역 (다음 캔들과의 빈 공간)
      const lastCandle = candles[candles.length-1];
      const gapStart = lastCandle.c;
      const gapEnd = isUp ? lastCandle.c - 30 : lastCandle.c + 30;
      const nextX = x0+118;
      // 갭 영역 표시
      p += `<rect x="${nextX-cw}" y="${Math.min(gapStart, gapEnd)}" width="${cw*2}" height="${Math.abs(gapEnd-gapStart)}" fill="${isUp?COL.bull:COL.bear}" opacity="0.15" stroke="${isUp?COL.bull:COL.bear}" stroke-width="1.2" stroke-dasharray="3 2"/>`;
      // 다음 캔들 (갭 후)
      const nextO = gapEnd;
      const nextC = isUp ? gapEnd - 12 : gapEnd + 12;
      const nextH = isUp ? gapEnd - 18 : gapEnd + 4;
      const nextL = isUp ? gapEnd + 4 : gapEnd + 18;
      const color = isUp ? COL.bull : COL.bear;
      p += `<line x1="${nextX}" y1="${nextH}" x2="${nextX}" y2="${nextL}" stroke="${color}" stroke-width="1"/>`;
      const nTop = Math.min(nextO, nextC);
      const nH = Math.abs(nextC - nextO);
      p += `<rect x="${nextX-cw/2}" y="${nTop}" width="${cw}" height="${nH}" fill="${color}" rx="0.5"/>`;
      // 갭 표시 화살표
      const arrowX = nextX + 14;
      p += `<line x1="${arrowX}" y1="${gapStart}" x2="${arrowX}" y2="${gapEnd}" stroke="${color}" stroke-width="1.4" marker-end="url(#g${isUp?'U':'D'})"/>`;
      p += `<defs><marker id="g${isUp?'U':'D'}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="${color}"/></marker></defs>`;
      // 라벨
      p += `<text x="${(x0+x1)/2}" y="32" font-size="12.5" font-weight="800" fill="${color}" text-anchor="middle">${title}</text>`;
      // 점선 (전날 종가)
      p += `<line x1="${x0+5}" y1="${gapStart}" x2="${nextX-cw}" y2="${gapStart}" stroke="${COL.muted}" stroke-width="0.8" stroke-dasharray="2 2"/>`;
      p += `<text x="${x0+8}" y="${gapStart-3}" font-size="10" font-weight="700" fill="${COL.muted}">전날 종가</text>`;
      return p;
    }
    s += gapChart(20, 175, true, '🔺 갭 상승 (호재)');
    s += gapChart(185, 340, false, '🔻 갭 하락 (악재)');
    // 안내
    s += txt(W/2, 207, '👉 갭 발생 = 강한 뉴스·실적·해외 영향', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  investor_groups: ()=>{
    // 외국인·기관·개인 3인 비교
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '시장 참여자 = 외국인·기관·개인', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 3개 그룹 (각각 인물 + 특징)
    function person(cx, cy, color, emoji, label, char1, char2, money){
      let p = '';
      // 머리 (큰 이모지)
      p += `<circle cx="${cx}" cy="${cy}" r="22" fill="${color}" opacity="0.12" stroke="${color}" stroke-width="1.6"/>`;
      p += `<text x="${cx}" y="${cy+8}" font-size="26" text-anchor="middle">${emoji}</text>`;
      // 라벨
      p += `<rect x="${cx-32}" y="${cy+30}" width="64" height="20" fill="${color}" rx="3"/>`;
      p += `<text x="${cx}" y="${cy+44}" font-size="13" font-weight="800" fill="#fff" text-anchor="middle">${label}</text>`;
      // 특징
      p += `<text x="${cx}" y="${cy+62}" font-size="11.5" font-weight="700" fill="${color}" text-anchor="middle">${char1}</text>`;
      p += `<text x="${cx}" y="${cy+74}" font-size="11.5" font-weight="700" fill="${color}" text-anchor="middle">${char2}</text>`;
      // 자금 규모
      p += `<text x="${cx}" y="${cy+92}" font-size="12" font-weight="800" fill="${COL.muted}" text-anchor="middle">${money}</text>`;
      return p;
    }
    s += person(70, 75, COL.bear, '🌍', '외국인', '✓ 거대 자금', '✓ 장기 추세', '시총의 약 30%');
    s += person(180, 75, '#7C3AED', '🏛️', '기관', '✓ 펀드·연기금', '✓ 분석 깊음', '시총의 약 30%');
    s += person(290, 75, COL.bull, '👤', '개인', '✓ 개인 투자자', '✓ 단기 매매', '시총의 약 40%');
    // 영향력 그래프 (하단)
    s += `<rect x="20" y="180" width="${W-40}" height="32" fill="#F1F5F9" rx="3"/>`;
    s += txt(W/2, 195, '👉 외국인+기관 동반 매수 = 강한 신호', {color:COL.bull, size:12, weight:800, anchor:'middle'});
    s += txt(W/2, 207, '⚠ 단, 단기 트레이딩이나 지수 편입 매수일 수도 있음', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  broker_app: ()=>{
    // 증권사 앱 = 폰 화면 (계좌 개설/주문)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '증권사 앱 = 내 손안의 거래소 📱', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 폰 프레임
    const phx = 100, phy = 35, phw = 75, phh = 165;
    s += `<rect x="${phx}" y="${phy}" width="${phw}" height="${phh}" fill="#1F2937" rx="10" stroke="${COL.ink}" stroke-width="1.5"/>`;
    // 노치
    s += `<rect x="${phx+phw/2-12}" y="${phy+5}" width="24" height="5" fill="#000" rx="2.5"/>`;
    // 화면
    s += `<rect x="${phx+4}" y="${phy+15}" width="${phw-8}" height="${phh-25}" fill="#F8FAFC" rx="4"/>`;
    // 상단 (가격)
    s += `<rect x="${phx+4}" y="${phy+15}" width="${phw-8}" height="35" fill="${COL.bear}"/>`;
    s += txt(phx+phw/2, phy+27, '삼성전자', {color:'#fff', size:11.5, weight:800, anchor:'middle'});
    s += txt(phx+phw/2, phy+40, '71,400', {color:'#fff', size:13, weight:900, anchor:'middle'});
    s += txt(phx+phw/2, phy+48, '+2.1%', {color:'#FCA5A5', size:10, weight:800, anchor:'middle'});
    // 미니 차트
    s += `<polyline points="${phx+8},${phy+72} ${phx+18},${phy+68} ${phx+28},${phy+74} ${phx+38},${phy+62} ${phx+48},${phy+65} ${phx+58},${phy+58} ${phx+67},${phy+55}" fill="none" stroke="${COL.bear}" stroke-width="1.4"/>`;
    // 매수/매도 버튼
    s += `<rect x="${phx+8}" y="${phy+98}" width="28" height="20" fill="${COL.bear}" rx="3"/>`;
    s += txt(phx+22, phy+111, '매수', {color:'#fff', size:12, weight:800, anchor:'middle'});
    s += `<rect x="${phx+39}" y="${phy+98}" width="28" height="20" fill="${COL.bull}" rx="3"/>`;
    s += txt(phx+53, phy+111, '매도', {color:'#fff', size:12, weight:800, anchor:'middle'});
    // 메뉴 4개 (아이콘 그리드)
    [['📊','차트'],['📋','잔고'],['🔍','종목'],['📰','뉴스']].forEach((m,i)=>{
      const ix = phx+8+(i%2)*28;
      const iy = phy+125+Math.floor(i/2)*22;
      s += `<rect x="${ix}" y="${iy}" width="25" height="18" fill="#fff" stroke="${COL.grid}" stroke-width="0.6" rx="2"/>`;
      s += `<text x="${ix+12}" y="${iy+10}" font-size="11" text-anchor="middle">${m[0]}</text>`;
      s += `<text x="${ix+12}" y="${iy+17}" font-size="6" font-weight="700" fill="${COL.ink}" text-anchor="middle">${m[1]}</text>`;
    });
    // 좌측 안내 (MTS)
    s += `<rect x="14" y="50" width="78" height="80" fill="#EFF6FF" stroke="${COL.bear}" stroke-width="1.4" rx="4"/>`;
    s += txt(53, 65, '📱 MTS', {color:COL.bear, size:13, weight:800, anchor:'middle'});
    s += txt(53, 78, '모바일', {color:COL.bear, size:11, weight:700, anchor:'middle'});
    s += txt(53, 95, '✓ 어디서든', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    s += txt(53, 107, '✓ 간편', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    s += txt(53, 119, '✓ 초보 친화', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    // 우측 안내 (HTS)
    s += `<rect x="${W-92}" y="50" width="78" height="80" fill="#EEF2FF" stroke="#6366F1" stroke-width="1.4" rx="4"/>`;
    s += txt(W-53, 65, '🖥️ HTS', {color:'#4338CA', size:13, weight:800, anchor:'middle'});
    s += txt(W-53, 78, 'PC 데스크톱', {color:'#4338CA', size:11, weight:700, anchor:'middle'});
    s += txt(W-53, 95, '✓ 큰 화면', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    s += txt(W-53, 107, '✓ 깊은 분석', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    s += txt(W-53, 119, '✓ 전문가용', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    // 하단 메시지
    s += txt(W/2, H-8, '👉 비대면 계좌 개설 → MTS로 시작 권장', {color:COL.muted, size:11.5, weight:800, anchor:'middle'});
    return svgWrap(s);
  },

  // ===== 재무제표 D6 슬라이드별 전용 ====================
  revenue_growth_bars: ()=>{
    // 매출액 4년 우상향 막대 (외형 성장)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '💰 매출액 = 기업의 외형·시장 점유율', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    const baseY = 215, x0 = 60;
    const data = [
      {y:'2022', v:55, color:'#94A3B8'},
      {y:'2023', v:72, color:'#94A3B8'},
      {y:'2024', v:88, color:'#3B82F6'},
      {y:'2025', v:108, color:COL.bull},
      {y:'2026E', v:130, color:COL.bull, est:true},
    ];
    data.forEach((d,i)=>{
      const x = x0 + i*65;
      const h = d.v;
      s += `<rect x="${x-22}" y="${baseY-h}" width="44" height="${h}" fill="${d.color}" opacity="${d.est?0.5:0.9}" stroke="${COL.ink}" stroke-width="${d.est?'1.2" stroke-dasharray="4 2':'1.4'}" rx="3"/>`;
      s += txt(x, baseY-h-6, `${d.v}조`, {color:d.color, size:11, weight:800, anchor:'middle'});
      s += txt(x, baseY+14, d.y, {color:COL.muted, size:10, weight:700, anchor:'middle'});
    });
    // 화살표 (성장 방향) — 짧게, 막대 위
    s += `<path d="M 50 ${baseY-65} Q 200 ${baseY-105} ${W-30} ${baseY-145}" fill="none" stroke="${COL.bull}" stroke-width="1.8" stroke-dasharray="4 3" marker-end="url(#rgArr)"/>`;
    s += `<defs><marker id="rgArr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="${COL.bull}"/></marker></defs>`;
    // 부제목 박스 (제목 아래, 막대 위)
    s += `<rect x="50" y="32" width="${W-100}" height="22" fill="${COL.bull}" opacity="0.12" rx="11"/>`;
    s += txt(W/2, 47, '↗ 매년 매출 파이 ↑ = 성장 신호', {color:COL.bull, size:11, weight:800, anchor:'middle'});
    s += `<line x1="20" y1="${baseY+1}" x2="${W-20}" y2="${baseY+1}" stroke="${COL.ink}" stroke-width="1.4"/>`;
    return svgWrap(s);
  },

  operating_breakdown: ()=>{
    // 영업이익 = 매출 - 원가 - 인건비 - 광고비 (3컬럼: 라벨 / 막대 / 값+설명)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '🛒 영업이익 = 진짜 장사 실력', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    const startY = 50, barH = 28, gap = 38;
    const colLabel = 12, barX0 = 100, barMaxW = 200, colValue = barX0 + barMaxW + 12;
    const stages = [
      {label:'매출액',    val:115, color:'#3B82F6', txt:'전체 매출'},
      {label:'− 원가',    val:-50, color:'#EF4444', txt:'재료·생산'},
      {label:'− 인건비',  val:-25, color:'#F59E0B', txt:'월급'},
      {label:'− 광고',    val:-15, color:'#A78BFA', txt:'마케팅·임대'},
      {label:'= 영업이익',val:25,  color:COL.bull, txt:'본업 이익'},
    ];
    const maxAbs = Math.max(...stages.map(s=>Math.abs(s.val)));
    stages.forEach((st,i)=>{
      const y = startY + i*gap;
      const w = Math.abs(st.val) / maxAbs * barMaxW;
      const isFinal = i === stages.length-1;
      // 라벨 (왼쪽 컬럼)
      s += txt(colLabel, y+barH/2+5, st.label, {color:st.color, size:12, weight:800});
      // 막대
      s += `<rect x="${barX0}" y="${y}" width="${w}" height="${barH}" fill="${st.color}" opacity="${isFinal?0.95:0.85}" stroke="${COL.ink}" stroke-width="${isFinal?'2':'1'}" rx="3"/>`;
      // 막대 안 설명
      if(w > 60){
        s += txt(barX0+8, y+barH/2+5, st.txt, {color:'#fff', size:10.5, weight:700});
      }
      // 값 (오른쪽 컬럼)
      s += txt(colValue, y+barH/2+5, `${st.val>0?'+':''}${st.val}조`, {color:st.color, size:12, weight:800});
    });
    return svgWrap(s);
  },

  net_profit_waterfall: ()=>{
    // 매출 → 영업이익 → 순이익 폭포 차트
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '💵 당기순이익 = 최종 남는 돈', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 폭포 차트
    const barW = 60, baseY = 200;
    const data = [
      {label:'매출액', val:115, color:'#3B82F6', stack:115},
      {label:'영업이익', val:25, color:COL.bull, stack:25},
      {label:'세금', val:-5, color:'#EF4444', stack:25, deduct:true},
      {label:'이자', val:-3, color:'#F59E0B', stack:20, deduct:true},
      {label:'기타', val:-2, color:'#A78BFA', stack:17, deduct:true},
      {label:'당기순이익', val:15, color:COL.bull, stack:15, final:true},
    ];
    data.forEach((d,i)=>{
      const x = 35 + i*60;
      const h = d.val > 0 ? d.stack*1.3 : Math.abs(d.val)*1.3;
      const y = d.deduct ? baseY - d.stack*1.3 : baseY - h;
      s += `<rect x="${x}" y="${y}" width="${barW-12}" height="${h}" fill="${d.color}" opacity="${d.final?0.95:0.85}" stroke="${COL.ink}" stroke-width="${d.final?'1.8':'1'}" rx="2"/>`;
      // 값 표시
      s += txt(x+barW/2-6, y-5, `${d.val>0?'+':''}${d.val}`, {color:d.color, size:11, weight:800, anchor:'middle'});
      // 라벨
      s += txt(x+barW/2-6, baseY+15, d.label, {color:COL.ink, size:10.5, weight:700, anchor:'middle'});
      // 화살표 (좌측으로 이어지는 흐름)
      if(i>0 && i<data.length-1){
        s += `<line x1="${x-3}" y1="${baseY-data[i-1].stack*1.3-3}" x2="${x-3}" y2="${y-3}" stroke="${COL.muted}" stroke-width="0.8" stroke-dasharray="2 2"/>`;
      }
    });
    s += txt(W/2, H-8, '👉 영업이익 ≠ 당기순이익 (세금·이자 차감 후)', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  revenue_price_diverge: ()=>{
    // 매출 늘었는데 주가는 빠짐 (다이버전스)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '📈 매출 ↑  vs  📉 주가 ↓ — 왜?', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 좌측: 매출 우상향
    const lx = 40, ly = 70, lw = 130, lh = 100;
    s += `<rect x="${lx}" y="${ly}" width="${lw}" height="${lh}" fill="#fff" stroke="${COL.bull}" stroke-width="1.4" rx="4"/>`;
    s += txt(lx+lw/2, ly-5, '매출액', {color:COL.bull, size:11.5, weight:800, anchor:'middle'});
    // 우상향 막대 4개
    [40,55,70,90].forEach((v,i)=>{
      const x = lx+15+i*28;
      s += `<rect x="${x}" y="${ly+lh-v}" width="20" height="${v}" fill="${COL.bull}" opacity="0.85" rx="2"/>`;
    });
    s += `<polyline points="${lx+25},${ly+lh-40} ${lx+53},${ly+lh-55} ${lx+81},${ly+lh-70} ${lx+109},${ly+lh-90}" fill="none" stroke="${COL.bull}" stroke-width="2"/>`;
    // 우측: 주가 우하향
    const rx = W-lx-lw, ry = ly;
    s += `<rect x="${rx}" y="${ry}" width="${lw}" height="${lh}" fill="#fff" stroke="${COL.bear}" stroke-width="1.4" rx="4"/>`;
    s += txt(rx+lw/2, ry-5, '주가', {color:COL.bear, size:11.5, weight:800, anchor:'middle'});
    s += `<polyline points="${rx+15},${ry+15} ${rx+45},${ry+30} ${rx+75},${ry+25} ${rx+105},${ry+50} ${rx+lw-10},${ry+lh-15}" fill="none" stroke="${COL.bear}" stroke-width="2.4"/>`;
    // 점들
    [[15,15],[45,30],[75,25],[105,50],[lw-10,lh-15]].forEach(p=>{
      s += `<circle cx="${rx+p[0]}" cy="${ry+p[1]}" r="3.5" fill="${COL.bear}"/>`;
    });
    // 가운데 충돌 표시
    s += txt(W/2, ly+lh+15, '⚡', {color:'#F59E0B', size:24, anchor:'middle'});
    // 이유 박스 (하단)
    s += `<rect x="20" y="${H-50}" width="${W-40}" height="40" fill="#FEF3C7" rx="4"/>`;
    s += txt(W/2, ly+lh+35, '왜?', {color:'#92400E', size:11.5, weight:800, anchor:'middle'});
    s += txt(W/2, H-32, '✓ 영업이익률은 떨어졌나?', {color:'#92400E', size:10.5, weight:700, anchor:'middle'});
    s += txt(W/2, H-18, '✓ 시장이 이미 호재 반영? (이익실현)', {color:'#92400E', size:10.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  revenue_vs_profit_compare: ()=>{
    // 초기 기업 (매출 성장) vs 성숙 기업 (이익 성장)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '📊 초기 기업: 매출 ↑  /  성숙 기업: 이익 ↑', {color:COL.ink, weight:800, size:12.5, anchor:'middle'});
    // 좌측: 초기 기업
    function company(x0, x1, title, color, revGrow, profGrow){
      let p = '';
      p += `<rect x="${x0}" y="50" width="${x1-x0}" height="160" fill="#fff" stroke="${color}" stroke-width="1.6" rx="4"/>`;
      p += `<rect x="${x0}" y="50" width="${x1-x0}" height="24" fill="${color}" rx="4"/>`;
      p += `<text x="${(x0+x1)/2}" y="67" font-size="11.5" font-weight="800" fill="#fff" text-anchor="middle">${title}</text>`;
      // 매출 막대 (좌)
      p += `<text x="${x0+30}" y="90" font-size="10" font-weight="800" fill="${COL.muted}" text-anchor="middle">매출</text>`;
      const revH1 = 30, revH2 = revGrow;
      p += `<rect x="${x0+18}" y="${190-revH1}" width="20" height="${revH1}" fill="${color}" opacity="0.4" rx="2"/>`;
      p += `<rect x="${x0+42}" y="${190-revH2}" width="20" height="${revH2}" fill="${color}" opacity="0.85" rx="2"/>`;
      p += `<text x="${x0+30}" y="${200}" font-size="9" fill="${COL.muted}" text-anchor="middle">+${Math.round((revH2-revH1)/revH1*100)}%</text>`;
      // 이익 막대 (우)
      p += `<text x="${x1-30}" y="90" font-size="10" font-weight="800" fill="${COL.muted}" text-anchor="middle">이익</text>`;
      const proH1 = 30, proH2 = profGrow;
      p += `<rect x="${x1-44}" y="${190-proH1}" width="20" height="${proH1}" fill="${color}" opacity="0.4" rx="2"/>`;
      p += `<rect x="${x1-20}" y="${190-proH2}" width="20" height="${proH2}" fill="${color}" opacity="0.85" rx="2"/>`;
      p += `<text x="${x1-30}" y="${200}" font-size="9" fill="${COL.muted}" text-anchor="middle">+${Math.round((proH2-proH1)/proH1*100)}%</text>`;
      // 라벨
      p += `<line x1="${x0+30}" y1="${190-revH1}" x2="${x0+30}" y2="${190-revH2}" stroke="${color}" stroke-width="1" stroke-dasharray="2 2"/>`;
      p += `<line x1="${x1-30}" y1="${190-proH1}" x2="${x1-30}" y2="${190-proH2}" stroke="${color}" stroke-width="1" stroke-dasharray="2 2"/>`;
      // 화살표
      const revArrow = revH2 > revH1 ? '↑' : '→';
      const proArrow = proH2 > proH1 ? '↑' : '→';
      p += `<text x="${x0+30}" y="78" font-size="14" font-weight="900" fill="${color}" text-anchor="middle">${revArrow}</text>`;
      p += `<text x="${x1-30}" y="78" font-size="14" font-weight="900" fill="${color}" text-anchor="middle">${proArrow}</text>`;
      return p;
    }
    s += company(20, 195, '🌱 초기 (플랫폼)', '#3B82F6', 90, 35);
    s += company(205, W-20, '🏢 성숙 (제조·금융)', COL.bull, 35, 90);
    // 안내
    s += txt(W/2, H-15, '👉 시장은 기업 단계에 따라 다른 숫자에 반응', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  operating_margin_donut: ()=>{
    // 영업이익률 도넛 (애플 vs 일반 기업)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '💪 영업이익률 = 기업의 기초 체력', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    function donut(cx, cy, pct, label, sub, color){
      color = color || COL.bull;
      let p = '';
      const r = 50, sw = 16;
      // 배경 원
      p += `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${COL.grid}" stroke-width="${sw}"/>`;
      // 진행 호 (위 12시부터 시계방향)
      const ang = pct/100 * 2*Math.PI - Math.PI/2;
      const ex = cx + r*Math.cos(ang), ey = cy + r*Math.sin(ang);
      const sxp = cx, syp = cy - r;
      const large = pct > 50 ? 1 : 0;
      p += `<path d="M ${sxp} ${syp} A ${r} ${r} 0 ${large} 1 ${ex} ${ey}" fill="none" stroke="${color}" stroke-width="${sw}" stroke-linecap="round"/>`;
      // 가운데 % 표시
      p += `<text x="${cx}" y="${cy+5}" font-size="22" font-weight="900" fill="${color}" text-anchor="middle">${pct}%</text>`;
      // 라벨
      p += `<text x="${cx}" y="${cy+r+22}" font-size="12" font-weight="800" fill="${COL.ink}" text-anchor="middle">${label}</text>`;
      p += `<text x="${cx}" y="${cy+r+38}" font-size="10" font-weight="700" fill="${COL.muted}" text-anchor="middle">${sub}</text>`;
      return p;
    }
    s += donut(110, 130, 30, '🍎 Apple 같은', '강한 체력');
    s += donut(290, 130, 8, '일반 기업', '약한 체력', '#F59E0B');
    // 두 도넛 가운데 색상
    // (위 함수에서 color 파라미터 사용 - 첫번째는 default green, 두번째는 amber로 수정)
    s = s.replace('<path d="M 110 80 A 50 50 0 1 1 ', `<path d="M 110 80 A 50 50 0 1 1 ` ); // (no-op safer)
    // 비교 메시지
    s += txt(W/2, H-15, '👉 매출 100억 중 30억 남는 vs 8억 남는 차이', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  // ===== 재무제표 D2-D3 ==========================
  financial_5docs: ()=>{
    // 재무제표 5가지 종류
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '📑 재무제표 5가지 = 기업 보고서 세트', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    const docs = [
      {x:55,  label:'재무상태표',     icon:'⚖️', desc:'자산 vs 부채', color:'#3B82F6', priority:'★ 기본'},
      {x:140, label:'손익계산서',     icon:'📊', desc:'얼마 벌었나',  color:'#10B981', priority:'★ 기본'},
      {x:225, label:'현금흐름표',     icon:'💧', desc:'실제 돈 흐름', color:'#06B6D4', priority:'★ 기본'},
      {x:310, label:'자본변동표',     icon:'🔄', desc:'자본 변화',    color:'#8B5CF6'},
      {x:35, label:'주석',            icon:'📝', desc:'설명·세부정보',color:'#94A3B8', x_only:true, single:true},
    ];
    // 4개 가로 + 1개 (주석)는 아래
    docs.slice(0,4).forEach(d=>{
      s += `<rect x="${d.x-30}" y="60" width="60" height="105" fill="${d.color}" opacity="0.15" stroke="${d.color}" stroke-width="1.5" rx="6"/>`;
      // 상단 헤더
      s += `<rect x="${d.x-30}" y="60" width="60" height="22" fill="${d.color}" rx="6"/>`;
      s += txt(d.x, 75, d.icon, {size:14, anchor:'middle'});
      // 본문
      s += txt(d.x, 100, d.label, {color:d.color, size:10.5, weight:800, anchor:'middle'});
      s += txt(d.x, 117, d.desc, {color:COL.muted, size:9.5, weight:700, anchor:'middle'});
      // 우선순위
      if(d.priority){
        s += `<rect x="${d.x-25}" y="${135}" width="50" height="18" fill="${COL.bull}" opacity="0.85" rx="2"/>`;
        s += txt(d.x, 148, d.priority, {color:'#fff', size:9.5, weight:800, anchor:'middle'});
      }
      // 가로 줄
      for(let i=0;i<3;i++)
        s += `<line x1="${d.x-25}" y1="${165+i*4}" x2="${d.x+25}" y2="${165+i*4}" stroke="${d.color}" stroke-width="0.6" opacity="0.4"/>`;
    });
    // 5번째 (주석) - 하단
    s += `<rect x="40" y="190" width="${W-80}" height="32" fill="#94A3B8" opacity="0.15" stroke="#94A3B8" stroke-width="1.4" rx="6"/>`;
    s += txt(60, 205, '📝 주석', {color:'#475569', size:11, weight:800});
    s += txt(60, 218, '숫자에 대한 추가 설명·세부 정보', {color:COL.muted, size:10, weight:700});
    s += txt(W-30, 213, '(보충)', {color:'#94A3B8', size:10, weight:700, anchor:'end'});
    return svgWrap(s);
  },

  four_indicators: ()=>{
    // 재무 핵심 4지표 (유동/부채/잉여금/현금흐름)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '🎯 재무상태표 핵심 4지표 = 안전 체력', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    const items = [
      {x:60,  y:75,  emoji:'💧', label:'유동비율',     desc:'단기 부채 갚을 능력',  good:'>200%',   color:'#3B82F6'},
      {x:200, y:75,  emoji:'🏋', label:'부채비율',     desc:'전체 부채 부담',      good:'<100%',   color:'#EF4444'},
      {x:60,  y:170, emoji:'📦', label:'이익잉여금',   desc:'쌓아둔 돈',           good:'+증가',    color:COL.bull},
      {x:200, y:170, emoji:'💵', label:'영업현금흐름', desc:'실제 본업으로 번 돈', good:'+양수',    color:'#06B6D4'},
    ];
    items.forEach(it=>{
      s += `<rect x="${it.x}" y="${it.y}" width="160" height="80" fill="#fff" stroke="${it.color}" stroke-width="1.6" rx="6"/>`;
      // 좌측 이모지 박스
      s += `<rect x="${it.x}" y="${it.y}" width="44" height="80" fill="${it.color}" opacity="0.15" rx="6"/>`;
      s += `<text x="${it.x+22}" y="${it.y+50}" font-size="28" text-anchor="middle">${it.emoji}</text>`;
      // 우측 정보
      s += txt(it.x+50, it.y+22, it.label, {color:it.color, size:12, weight:800});
      s += txt(it.x+50, it.y+38, it.desc, {color:COL.muted, size:10, weight:700});
      // 좋은 기준
      s += `<rect x="${it.x+50}" y="${it.y+50}" width="55" height="18" fill="${it.color}" rx="3"/>`;
      s += txt(it.x+78, it.y+63, it.good, {color:'#fff', size:10.5, weight:800, anchor:'middle'});
    });
    return svgWrap(s);
  },

  cash_flow_river: ()=>{
    // 현금흐름표 = 흐르는 강물
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '💧 현금흐름표 = 실제 돈의 강물', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 강 (좌→우 흐름)
    s += `<path d="M 30 110 Q 130 95 200 105 T ${W-30} 115 L ${W-30} 145 Q 280 135 200 145 T 30 140 Z" fill="#3B82F6" opacity="0.4" stroke="#1E40AF" stroke-width="1.4"/>`;
    // 강물 흐름 표시 (방향 화살표)
    [[80,123],[180,127],[280,130]].forEach(p=>{
      s += `<text x="${p[0]}" y="${p[1]}" font-size="16" fill="#1E40AF" font-weight="900">→</text>`;
    });
    // 좌측: 영업활동 (들어오는 돈)
    s += `<rect x="20" y="60" width="100" height="36" fill="${COL.bull}" rx="6"/>`;
    s += txt(70, 76, '🏪 영업활동', {color:'#fff', size:11.5, weight:800, anchor:'middle'});
    s += txt(70, 90, '+30조 (들어옴)', {color:'#fff', size:9.5, weight:800, anchor:'middle'});
    s += `<line x1="70" y1="100" x2="70" y2="115" stroke="${COL.bull}" stroke-width="2" marker-end="url(#cfArr)"/>`;
    s += `<defs><marker id="cfArr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="${COL.bull}"/></marker></defs>`;
    // 가운데: 투자활동 (나가는 돈)
    s += `<rect x="155" y="60" width="100" height="36" fill="#F59E0B" rx="6"/>`;
    s += txt(205, 76, '🏗️ 투자활동', {color:'#fff', size:11.5, weight:800, anchor:'middle'});
    s += txt(205, 90, '−15조 (설비투자)', {color:'#fff', size:9.5, weight:800, anchor:'middle'});
    s += `<line x1="205" y1="100" x2="205" y2="115" stroke="#F59E0B" stroke-width="2" marker-end="url(#cfArr2)"/>`;
    s += `<defs><marker id="cfArr2" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#F59E0B"/></marker></defs>`;
    // 우측: 재무활동
    s += `<rect x="290" y="60" width="100" height="36" fill="#8B5CF6" rx="6"/>`;
    s += txt(340, 76, '🏦 재무활동', {color:'#fff', size:11.5, weight:800, anchor:'middle'});
    s += txt(340, 90, '−5조 (배당지급)', {color:'#fff', size:9.5, weight:800, anchor:'middle'});
    s += `<line x1="340" y1="100" x2="340" y2="115" stroke="#8B5CF6" stroke-width="2" marker-end="url(#cfArr3)"/>`;
    s += `<defs><marker id="cfArr3" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#8B5CF6"/></marker></defs>`;
    // 결과
    s += `<rect x="100" y="170" width="${W-200}" height="50" fill="${COL.bull}" opacity="0.15" stroke="${COL.bull}" stroke-width="1.6" rx="6"/>`;
    s += txt(W/2, 188, '✓ 영업 현금흐름 + (양수)', {color:COL.bull, size:12, weight:800, anchor:'middle'});
    s += txt(W/2, 207, '= 본업으로 진짜 돈 버는 기업 (안전 신호)', {color:COL.bull, size:10.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  // ===== PER/PBR 슬라이드별 =========================
  per_formula: ()=>{
    // PER 공식 = 주가/EPS = PER (수평 배치, = 결과)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '🧮 PER 공식 = 주가 ÷ EPS', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 좌측: 분수 (주가/EPS)
    const fx = 130, fyTop = 70, fyBot = 165;
    // 주가 박스
    s += `<rect x="${fx-70}" y="${fyTop}" width="140" height="40" fill="${COL.bear}" opacity="0.15" stroke="${COL.bear}" stroke-width="1.6" rx="4"/>`;
    s += txt(fx, fyTop+15, '주가', {color:COL.bear, size:11, weight:800, anchor:'middle'});
    s += txt(fx, fyTop+32, '71,400원', {color:COL.bear, size:14, weight:900, anchor:'middle'});
    // 분수선
    s += `<line x1="${fx-80}" y1="${fyTop+50}" x2="${fx+80}" y2="${fyTop+50}" stroke="${COL.ink}" stroke-width="2.5"/>`;
    // EPS 박스
    s += `<rect x="${fx-70}" y="${fyTop+58}" width="140" height="40" fill="${COL.bull}" opacity="0.15" stroke="${COL.bull}" stroke-width="1.6" rx="4"/>`;
    s += txt(fx, fyTop+73, 'EPS', {color:COL.bull, size:11, weight:800, anchor:'middle'});
    s += txt(fx, fyTop+90, '5,800원', {color:COL.bull, size:14, weight:900, anchor:'middle'});
    // 등호
    s += txt(fx+105, fyTop+50, '=', {color:COL.ink, size:24, weight:900, anchor:'middle'});
    // 결과 박스 (우측)
    const rx = fx+170;
    s += `<rect x="${rx-45}" y="${fyTop+15}" width="90" height="70" fill="#FBBF24" stroke="${COL.ink}" stroke-width="1.8" rx="8"/>`;
    s += txt(rx, fyTop+32, 'PER', {color:'#92400E', size:13, weight:800, anchor:'middle'});
    s += txt(rx, fyTop+58, '12.3', {color:'#92400E', size:24, weight:900, anchor:'middle'});
    s += txt(rx, fyTop+76, '배', {color:'#92400E', size:11, weight:800, anchor:'middle'});
    // 안내
    s += `<rect x="40" y="${H-40}" width="${W-80}" height="28" fill="#FEF3C7" rx="4"/>`;
    s += txt(W/2, H-22, '👉 12.3년이면 이익으로 원금 회수', {color:'#92400E', size:11.5, weight:800, anchor:'middle'});
    return svgWrap(s);
  },

  per_sector_compare: ()=>{
    // 업종별 PER 비교 (바이오 30 vs 금융 5)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '📊 업종별 PER — 같은 기준으로 비교', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    const sectors = [
      {label:'바이오',     per:35, color:'#10B981', exam:'셀트리온'},
      {label:'IT 플랫폼',  per:28, color:'#3B82F6', exam:'NAVER'},
      {label:'반도체',     per:18, color:'#8B5CF6', exam:'삼성전자'},
      {label:'자동차',     per:10, color:'#F59E0B', exam:'현대차'},
      {label:'금융',       per:6,  color:'#EF4444', exam:'KB금융'},
      {label:'철강',       per:5,  color:'#94A3B8', exam:'POSCO'},
    ];
    const baseY = 230, x0 = 35, barW = 50, gap = 56;
    sectors.forEach((s_,i)=>{
      const x = x0 + i*gap;
      const h = Math.min(s_.per * 4.5, 150); // 막대 높이 제한
      s += `<rect x="${x}" y="${baseY-h}" width="${barW}" height="${h}" fill="${s_.color}" opacity="0.85" stroke="${COL.ink}" stroke-width="1" rx="3"/>`;
      // PER 값
      s += txt(x+barW/2, Math.max(baseY-h-8, 78), `${s_.per}`, {color:s_.color, size:14, weight:900, anchor:'middle'});
      // 라벨 (회전)
      s += `<g transform="translate(${x+barW/2}, ${baseY+12}) rotate(-12)">`;
      s += `<text x="0" y="0" font-size="10.5" font-weight="800" fill="${COL.ink}" text-anchor="middle">${s_.label}</text>`;
      s += `<text x="0" y="13" font-size="9" font-weight="700" fill="${COL.muted}" text-anchor="middle">${s_.exam}</text>`;
      s += `</g>`;
    });
    // 라벨
    s += txt(W/2, 60, '성장↑ 업종은 PER 高 / 성숙↓ 업종은 PER 低', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    s += `<line x1="20" y1="${baseY+1}" x2="${W-20}" y2="${baseY+1}" stroke="${COL.ink}" stroke-width="1.4"/>`;
    return svgWrap(s);
  },

  per_forward_vs_past: ()=>{
    // 과거 PER vs 선행 PER
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '⏪ 과거 PER  vs  ⏩ 선행 PER', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 좌측: 과거
    function box(x0, x1, title, val, sub, color, isPast){
      let p = '';
      p += `<rect x="${x0}" y="55" width="${x1-x0}" height="170" fill="${color}" opacity="0.1" stroke="${color}" stroke-width="1.6" rx="6"/>`;
      p += `<rect x="${x0}" y="55" width="${x1-x0}" height="32" fill="${color}" rx="6"/>`;
      p += `<text x="${(x0+x1)/2}" y="76" font-size="13" font-weight="800" fill="#fff" text-anchor="middle">${title}</text>`;
      p += `<text x="${(x0+x1)/2}" y="120" font-size="32" font-weight="900" fill="${color}" text-anchor="middle">${val}</text>`;
      p += `<text x="${(x0+x1)/2}" y="138" font-size="10.5" font-weight="700" fill="${COL.muted}" text-anchor="middle">${sub}</text>`;
      // 시계
      const ccx = (x0+x1)/2, ccy = 175;
      p += `<circle cx="${ccx}" cy="${ccy}" r="22" fill="#fff" stroke="${color}" stroke-width="1.8"/>`;
      p += `<text x="${ccx}" y="${ccy+5}" font-size="20" text-anchor="middle">${isPast?'⏪':'⏩'}</text>`;
      // 키워드
      p += `<text x="${(x0+x1)/2}" y="215" font-size="10.5" font-weight="800" fill="${color}" text-anchor="middle">${isPast?'실제 성과':'예상치'}</text>`;
      return p;
    }
    s += box(20, 195, '과거 PER (TTM)', '15.2', '지난 12개월 실적', '#94A3B8', true);
    s += box(205, W-20, '선행 PER (FWD)', '11.8', '내년 예상 실적', COL.bull, false);
    // VS
    s += `<rect x="${W/2-15}" y="135" width="30" height="30" fill="#FEF3C7" stroke="#D97706" stroke-width="1.5" rx="6"/>`;
    s += txt(W/2, 156, 'VS', {color:'#92400E', size:13, weight:900, anchor:'middle'});
    return svgWrap(s);
  },

  per_value_trap: ()=>{
    // PER 가치 함정 (낮은 PER ≠ 좋은 종목)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '⚠️ 가치 함정 — PER 낮다고 다 좋은 게 아님', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 미끼+덫 일러스트
    // 좌측: 미끼 (낮은 PER 표시)
    s += `<circle cx="80" cy="115" r="36" fill="#FBBF24" stroke="${COL.ink}" stroke-width="2"/>`;
    s += txt(80, 110, 'PER 4', {color:COL.ink, size:13, weight:900, anchor:'middle'});
    s += txt(80, 124, '"저평가!"', {color:'#92400E', size:11, weight:800, anchor:'middle'});
    // 함정 (덫)
    s += `<line x1="80" y1="155" x2="80" y2="200" stroke="${COL.ink}" stroke-width="2"/>`;
    // 덫 (X 표시)
    for(let i=0;i<5;i++){
      const x = 50+i*15;
      s += `<polygon points="${x},190 ${x+5},200 ${x-5},200" fill="${COL.bear}"/>`;
    }
    // 화살표 (속아서 사면)
    s += `<text x="125" y="120" font-size="18" font-weight="900" fill="${COL.bear}">→</text>`;
    // 우측: 진짜 이유
    s += `<rect x="155" y="60" width="${W-180}" height="160" fill="#FEE2E2" stroke="${COL.bear}" stroke-width="1.6" rx="6"/>`;
    s += txt(170, 80, '⚠ 실제 이유:', {color:COL.bear, size:12.5, weight:800});
    s += txt(170, 105, '✗ 미래 성장 멈춤', {color:COL.bear, size:11, weight:800});
    s += txt(170, 125, '✗ 시장 외면받음', {color:COL.bear, size:11, weight:800});
    s += txt(170, 145, '✗ 일회성 이익으로', {color:COL.bear, size:11, weight:800});
    s += txt(178, 160, '   이익 부풀림', {color:COL.bear, size:11, weight:700});
    // 체크
    s += `<rect x="170" y="178" width="${W-205}" height="30" fill="${COL.bull}" opacity="0.2" rx="3"/>`;
    s += txt(180, 197, '✓ ROE / 매출 성장도 함께 보기', {color:COL.bull, size:11, weight:800});
    return svgWrap(s);
  },

  pbr_concept: ()=>{
    // PBR = 시가총액 vs 순자산 막대 비교
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '🏦 PBR = 주가 ÷ 1주당 순자산 (BPS)', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 좌측: 자산 (장부 가치)
    const baseY = 200, lh = 130;
    s += `<rect x="55" y="${baseY-lh}" width="80" height="${lh}" fill="${COL.bull}" opacity="0.85" stroke="${COL.ink}" stroke-width="1.5" rx="3"/>`;
    s += txt(95, baseY-lh-8, '순자산', {color:COL.bull, size:11.5, weight:800, anchor:'middle'});
    s += txt(95, baseY-lh+25, '1조원', {color:'#fff', size:14, weight:900, anchor:'middle'});
    s += txt(95, baseY-lh+45, '(장부)', {color:'#fff', size:9.5, weight:700, anchor:'middle'});
    // 우측: 시가총액
    const mh = 130;
    s += `<rect x="265" y="${baseY-mh}" width="80" height="${mh}" fill="${COL.bear}" opacity="0.85" stroke="${COL.ink}" stroke-width="1.5" rx="3"/>`;
    s += txt(305, baseY-mh-8, '시가총액', {color:COL.bear, size:11.5, weight:800, anchor:'middle'});
    s += txt(305, baseY-mh+25, '1조원', {color:'#fff', size:14, weight:900, anchor:'middle'});
    s += txt(305, baseY-mh+45, '(시장 평가)', {color:'#fff', size:9.5, weight:700, anchor:'middle'});
    // VS 가운데
    s += `<rect x="${W/2-30}" y="${baseY-90}" width="60" height="50" fill="#FEF3C7" stroke="#D97706" stroke-width="1.6" rx="8"/>`;
    s += txt(W/2, baseY-70, 'PBR', {color:'#92400E', size:12, weight:900, anchor:'middle'});
    s += txt(W/2, baseY-50, '= 1.0', {color:'#92400E', size:13, weight:900, anchor:'middle'});
    // 수평 화살표
    s += `<line x1="135" y1="120" x2="${W/2-30}" y2="120" stroke="${COL.muted}" stroke-width="1.5" stroke-dasharray="3 2"/>`;
    s += `<line x1="${W/2+30}" y1="120" x2="265" y2="120" stroke="${COL.muted}" stroke-width="1.5" stroke-dasharray="3 2"/>`;
    // 바닥
    s += `<line x1="20" y1="${baseY+1}" x2="${W-20}" y2="${baseY+1}" stroke="${COL.ink}" stroke-width="1.5"/>`;
    // 안내
    s += txt(W/2, H-15, '👉 PBR 1 = 시장이 장부 가치만큼 평가', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  pbr_under_one: ()=>{
    // PBR 1배 미만 = 장부 가치보다 싸게 거래
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '🎯 PBR < 1 = 장부 가치보다 싸게!', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    const baseY = 215;
    // 좌측: 자산 (높은 막대)
    s += `<rect x="60" y="${baseY-150}" width="80" height="150" fill="${COL.bull}" opacity="0.85" stroke="${COL.ink}" stroke-width="1.5" rx="3"/>`;
    s += txt(100, baseY-160, '순자산', {color:COL.bull, size:12, weight:800, anchor:'middle'});
    s += txt(100, baseY-110, '1조', {color:'#fff', size:20, weight:900, anchor:'middle'});
    s += txt(100, baseY-85, '원', {color:'#fff', size:11, weight:800, anchor:'middle'});
    // 우측: 시가총액 (낮은 막대)
    s += `<rect x="220" y="${baseY-100}" width="80" height="100" fill="${COL.bear}" opacity="0.85" stroke="${COL.ink}" stroke-width="1.5" rx="3"/>`;
    s += txt(260, baseY-110, '시가총액', {color:COL.bear, size:12, weight:800, anchor:'middle'});
    s += txt(260, baseY-65, '7천억', {color:'#fff', size:16, weight:900, anchor:'middle'});
    s += txt(260, baseY-40, '원', {color:'#fff', size:11, weight:800, anchor:'middle'});
    // 차이 표시 (자산 막대 위에 줄, 막대 사이에 −30% 할인 라벨)
    s += `<rect x="155" y="${baseY-130}" width="60" height="20" fill="#FEF3C7" stroke="#D97706" stroke-width="1.4" rx="4"/>`;
    s += txt(185, baseY-115, '−30% 할인', {color:'#92400E', size:10.5, weight:800, anchor:'middle'});
    s += `<line x1="155" y1="${baseY-120}" x2="140" y2="${baseY-150}" stroke="#D97706" stroke-width="1.2" stroke-dasharray="2 2"/>`;
    s += `<line x1="215" y1="${baseY-120}" x2="220" y2="${baseY-100}" stroke="#D97706" stroke-width="1.2" stroke-dasharray="2 2"/>`;
    // PBR 표시 (오른쪽 위)
    s += `<rect x="${W-90}" y="55" width="80" height="60" fill="#FEF3C7" stroke="#D97706" stroke-width="1.8" rx="8"/>`;
    s += txt(W-50, 73, 'PBR', {color:'#92400E', size:11, weight:800, anchor:'middle'});
    s += txt(W-50, 100, '0.7', {color:'#92400E', size:22, weight:900, anchor:'middle'});
    // 바닥
    s += `<line x1="20" y1="${baseY+1}" x2="${W-20}" y2="${baseY+1}" stroke="${COL.ink}" stroke-width="1.4"/>`;
    s += txt(W/2, H-12, '👉 다만, ROE도 같이 봐야 진짜 저평가', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  value_diamond: ()=>{
    // 저PBR 가치주 = 흙속 다이아몬드
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '💎 저PBR = 흙속에 묻힌 다이아몬드', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 흙 (땅속)
    s += `<rect x="0" y="130" width="${W}" height="120" fill="#92400E" opacity="0.4"/>`;
    // 흙 무늬
    for(let i=0;i<10;i++){
      const x = i*40+10, y=140+(i%3)*15;
      s += `<circle cx="${x}" cy="${y}" r="3" fill="#7C2D12" opacity="0.5"/>`;
    }
    // 다이아몬드 (흙 안에)
    const cx = W/2, cy = 175;
    s += `<polygon points="${cx-30},${cy} ${cx-15},${cy-20} ${cx+15},${cy-20} ${cx+30},${cy} ${cx},${cy+30}" fill="#06B6D4" stroke="#0891B2" stroke-width="2"/>`;
    // 다이아몬드 빛 반사
    s += `<polygon points="${cx-15},${cy-20} ${cx-22},${cy} ${cx},${cy+30} ${cx},${cy-20}" fill="#22D3EE"/>`;
    s += `<polygon points="${cx},${cy-20} ${cx+15},${cy-20} ${cx+22},${cy} ${cx},${cy+30}" fill="#0EA5E9"/>`;
    s += `<line x1="${cx-15}" y1="${cy-20}" x2="${cx+15}" y2="${cy-20}" stroke="#fff" stroke-width="1.5"/>`;
    // 발견 효과 (반짝)
    s += `<text x="${cx-50}" y="${cy-30}" font-size="20" fill="#FBBF24">✦</text>`;
    s += `<text x="${cx+45}" y="${cy-15}" font-size="16" fill="#FBBF24">✧</text>`;
    s += `<text x="${cx-65}" y="${cy+5}" font-size="14" fill="#FBBF24">✦</text>`;
    // 라벨
    s += `<rect x="40" y="60" width="${W-80}" height="50" fill="#FEF3C7" stroke="#D97706" stroke-width="1.6" rx="6"/>`;
    s += txt(W/2, 78, '✓ PBR 1 미만 (자산보다 싼 가격)', {color:'#92400E', size:11.5, weight:800, anchor:'middle'});
    s += txt(W/2, 95, '✓ 시장이 외면 → 발견 시 큰 수익 기회', {color:'#92400E', size:11, weight:700, anchor:'middle'});
    // 예시 종목
    s += `<rect x="40" y="${H-40}" width="${W-80}" height="30" fill="${COL.bull}" opacity="0.15" rx="4"/>`;
    s += txt(W/2, H-22, '예: 은행주 · 보험주 · 지주사', {color:COL.bull, size:11, weight:800, anchor:'middle'});
    return svgWrap(s);
  },

  liquidation_value: ()=>{
    // 청산 가치 = 자산 - 부채
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '⚖️ 청산 가치 = 자산 − 부채', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    // 자산 (큰 막대)
    const baseY = 220, ax = 60, aw = 70;
    s += `<rect x="${ax}" y="${baseY-150}" width="${aw}" height="150" fill="${COL.bull}" opacity="0.85" stroke="${COL.ink}" stroke-width="1.5" rx="3"/>`;
    s += txt(ax+aw/2, baseY-160, '🏢 자산', {color:COL.bull, size:12, weight:800, anchor:'middle'});
    s += txt(ax+aw/2, baseY-110, '1조원', {color:'#fff', size:14, weight:900, anchor:'middle'});
    s += txt(ax+aw/2, baseY-90, '(공장+현금', {color:'#fff', size:9, weight:700, anchor:'middle'});
    s += txt(ax+aw/2, baseY-78, '+재고+부동산)', {color:'#fff', size:9, weight:700, anchor:'middle'});
    // 부채 (자산의 절반)
    const dx = ax+aw+45, dh = 90;
    s += `<rect x="${dx}" y="${baseY-dh}" width="${aw}" height="${dh}" fill="${COL.bear}" opacity="0.85" stroke="${COL.ink}" stroke-width="1.5" rx="3"/>`;
    s += txt(dx+aw/2, baseY-100, '💳 부채', {color:COL.bear, size:12, weight:800, anchor:'middle'});
    s += txt(dx+aw/2, baseY-55, '6천억', {color:'#fff', size:14, weight:900, anchor:'middle'});
    // 빼기 표시
    s += txt(ax+aw+22, baseY-100, '−', {color:COL.ink, size:24, weight:900, anchor:'middle'});
    // 등호
    s += txt(dx+aw+22, baseY-100, '=', {color:COL.ink, size:24, weight:900, anchor:'middle'});
    // 청산 가치 (남는 돈)
    const lx = dx+aw+45, lh = 60;
    s += `<rect x="${lx}" y="${baseY-lh}" width="${aw}" height="${lh}" fill="#FBBF24" stroke="${COL.ink}" stroke-width="1.8" rx="3"/>`;
    s += txt(lx+aw/2, baseY-70, '💎 청산가치', {color:'#92400E', size:11, weight:800, anchor:'middle'});
    s += txt(lx+aw/2, baseY-30, '4천억', {color:'#92400E', size:13, weight:900, anchor:'middle'});
    // 바닥
    s += `<line x1="20" y1="${baseY+1}" x2="${W-20}" y2="${baseY+1}" stroke="${COL.ink}" stroke-width="1.5"/>`;
    s += txt(W/2, H-12, '👉 회사 정리 시 진짜 남는 순자산 가치', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  // ====== 신규 섹터 5종 (AI 반도체·방산·양자·자율주행·로봇) ======
  sector_ai_semi: ()=>{
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '🤖 AI 반도체 — 생성형 AI · 데이터센터 폭증', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 칩 일러스트 (좌측)
    const chx=70, chy=120;
    s += `<rect x="${chx-32}" y="${chy-32}" width="64" height="64" fill="#1F2937" stroke="${COL.ink}" stroke-width="2" rx="6"/>`;
    // CPU 다이
    s += `<rect x="${chx-18}" y="${chy-18}" width="36" height="36" fill="#3B82F6" rx="3"/>`;
    s += `<text x="${chx}" y="${chy+5}" font-size="14" font-weight="900" fill="#fff" text-anchor="middle">AI</text>`;
    // 핀 (8 + 8 + 8 + 8)
    for(let i=0;i<8;i++){
      const o = -28 + i*8;
      s += `<rect x="${chx+o}" y="${chy-40}" width="4" height="6" fill="#FBBF24"/>`;
      s += `<rect x="${chx+o}" y="${chy+34}" width="4" height="6" fill="#FBBF24"/>`;
      s += `<rect x="${chx-40}" y="${chy+o}" width="6" height="4" fill="#FBBF24"/>`;
      s += `<rect x="${chx+34}" y="${chy+o}" width="6" height="4" fill="#FBBF24"/>`;
    }
    s += txt(chx, chy-46, 'AI 칩', {color:COL.ink, size:11, weight:800, anchor:'middle'});
    // 밸류체인 (오른쪽)
    s += `<rect x="155" y="60" width="${W-175}" height="65" fill="#EFF6FF" rx="6" stroke="${COL.bear}" stroke-width="1.4"/>`;
    s += txt((155+W-20)/2, 76, 'AI 반도체 밸류체인', {color:COL.bear, size:11, weight:800, anchor:'middle'});
    [
      {x:175, label:'설계', co:'NVIDIA', color:'#10B981'},
      {x:235, label:'메모리', co:'삼성·SK', color:'#3B82F6'},
      {x:300, label:'장비', co:'AMAT·LAM', color:'#F59E0B'},
      {x:360, label:'기판', co:'국내 PCB', color:'#8B5CF6'},
    ].forEach(it=>{
      s += `<rect x="${it.x-22}" y="86" width="44" height="20" fill="${it.color}" rx="3"/>`;
      s += txt(it.x, 99, it.label, {color:'#fff', size:10, weight:800, anchor:'middle'});
      s += txt(it.x, 119, it.co, {color:it.color, size:9, weight:700, anchor:'middle'});
    });
    // 메시지
    s += txt(W/2, 160, '🌱 성장 동력: 생성형 AI·LLM·데이터센터 확대', {color:COL.bull, size:11, weight:700, anchor:'middle'});
    s += txt(W/2, 178, '👉 핵심: 메모리·HBM·패키징·장비 어디가 병목인지', {color:COL.ink, weight:800, size:11, anchor:'middle'});
    s += txt(W/2, 196, '⚠️ 리스크: AI 수요 둔화 · 사이클 변동 · 빅테크 의존', {color:COL.bear, size:10.5, weight:700, anchor:'middle'});
    s += txt(W/2, 215, '대표: 삼성전자 · SK하이닉스 · 한미반도체 · 이오테크닉스', {color:COL.muted, size:10.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  sector_defense: ()=>{
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '🛡️ 방산 — 지정학 긴장·국방비 확대', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 방패 + 화살 일러스트
    const sx=70, sy=120;
    s += `<path d="M ${sx} ${sy-40} Q ${sx-30} ${sy-30} ${sx-30} ${sy-5} Q ${sx-30} ${sy+25} ${sx} ${sy+40} Q ${sx+30} ${sy+25} ${sx+30} ${sy-5} Q ${sx+30} ${sy-30} ${sx} ${sy-40} Z" fill="#1E40AF" stroke="${COL.ink}" stroke-width="2"/>`;
    // 별
    s += `<text x="${sx}" y="${sy+8}" font-size="32" text-anchor="middle">⭐</text>`;
    s += txt(sx, sy-50, '방패·국방', {color:COL.ink, size:11, weight:800, anchor:'middle'});
    // 무기 종류
    s += `<rect x="155" y="60" width="${W-175}" height="65" fill="#FEF2F2" rx="6" stroke="${COL.bear}" stroke-width="1.4"/>`;
    s += txt((155+W-20)/2, 76, '방산 밸류체인', {color:COL.bear, size:11, weight:800, anchor:'middle'});
    [
      {x:175, label:'지상장비', co:'현대로템', color:'#10B981'},
      {x:235, label:'항공·미사일', co:'KAI·LIG', color:'#EF4444'},
      {x:300, label:'화약·탄약', co:'풍산·한화에어', color:'#F59E0B'},
      {x:360, label:'위성·통신', co:'한화시스템', color:'#8B5CF6'},
    ].forEach(it=>{
      s += `<rect x="${it.x-22}" y="86" width="44" height="20" fill="${it.color}" rx="3"/>`;
      s += txt(it.x, 99, it.label, {color:'#fff', size:9, weight:800, anchor:'middle'});
      s += txt(it.x, 119, it.co, {color:it.color, size:8.5, weight:700, anchor:'middle'});
    });
    s += txt(W/2, 160, '🌱 성장 동력: K9·K2 수출 · 폴란드/중동 계약', {color:COL.bull, size:11, weight:700, anchor:'middle'});
    s += txt(W/2, 178, '👉 핵심: 수주잔고 · 인도 시점 · 국가별 비중', {color:COL.ink, weight:800, size:11, anchor:'middle'});
    s += txt(W/2, 196, '⚠️ 리스크: 정책 변경 · 분쟁 종료 · 인도 지연', {color:COL.bear, size:10.5, weight:700, anchor:'middle'});
    s += txt(W/2, 215, '대표: 한화에어로스페이스 · 현대로템 · LIG넥스원 · KAI', {color:COL.muted, size:10.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  sector_quantum: ()=>{
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '⚛️ 양자컴퓨터 — 미래 연산·암호 기술', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 큐빗 (원자 모형) 일러스트
    const cx=80, cy=120;
    s += `<circle cx="${cx}" cy="${cy}" r="6" fill="${COL.bear}"/>`;
    // 3개 궤도
    s += `<ellipse cx="${cx}" cy="${cy}" rx="35" ry="14" fill="none" stroke="${COL.bull}" stroke-width="1.6"/>`;
    s += `<ellipse cx="${cx}" cy="${cy}" rx="35" ry="14" fill="none" stroke="#8B5CF6" stroke-width="1.6" transform="rotate(60 ${cx} ${cy})"/>`;
    s += `<ellipse cx="${cx}" cy="${cy}" rx="35" ry="14" fill="none" stroke="#F59E0B" stroke-width="1.6" transform="rotate(-60 ${cx} ${cy})"/>`;
    // 전자
    s += `<circle cx="${cx+35}" cy="${cy}" r="3" fill="${COL.bull}"/>`;
    s += `<circle cx="${cx-22}" cy="${cy-21}" r="3" fill="#8B5CF6"/>`;
    s += `<circle cx="${cx-22}" cy="${cy+21}" r="3" fill="#F59E0B"/>`;
    s += txt(cx, cy-50, '큐빗 (Qubit)', {color:COL.ink, size:11, weight:800, anchor:'middle'});
    // 응용 분야
    s += `<rect x="155" y="60" width="${W-175}" height="70" fill="#EEF2FF" rx="6" stroke="#6366F1" stroke-width="1.4"/>`;
    s += txt((155+W-20)/2, 76, '양자컴퓨터 응용', {color:'#4338CA', size:11, weight:800, anchor:'middle'});
    [
      {x:175, label:'연구·R&D', co:'한국전자통신', color:'#10B981'},
      {x:240, label:'보안·암호', co:'KT·LG', color:'#EF4444'},
      {x:305, label:'장비·소재', co:'국내 부품사', color:'#F59E0B'},
      {x:365, label:'통신', co:'SKT·KT', color:'#8B5CF6'},
    ].forEach(it=>{
      s += `<rect x="${it.x-22}" y="86" width="44" height="22" fill="${it.color}" rx="3"/>`;
      s += txt(it.x, 100, it.label, {color:'#fff', size:9.5, weight:800, anchor:'middle'});
      s += txt(it.x, 122, it.co, {color:it.color, size:8.5, weight:700, anchor:'middle'});
    });
    s += txt(W/2, 158, '🌱 미래 기술: 신약·암호·금융·AI 가속화', {color:COL.bull, size:11, weight:700, anchor:'middle'});
    s += txt(W/2, 176, '👉 아직은 기대형 — 정부 R&D · 보안 시범 단계', {color:COL.ink, weight:800, size:11, anchor:'middle'});
    s += txt(W/2, 194, '⚠️ 리스크: 상용화 한참 멀음 · 기대감 과열', {color:COL.bear, size:10.5, weight:700, anchor:'middle'});
    s += txt(W/2, 213, '대표: KT · ETRI 협력사 · 양자 통신 부품주', {color:COL.muted, size:10.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  sector_autonomous: ()=>{
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '🚗 자율주행 — 센서·AI·전동화 융합', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 자동차 + 센서 (좌측)
    const cx=85, cy=130;
    // 차체
    s += `<path d="M ${cx-40} ${cy} Q ${cx-40} ${cy-25} ${cx-25} ${cy-25} L ${cx+25} ${cy-25} Q ${cx+40} ${cy-25} ${cx+40} ${cy} L ${cx+40} ${cy+12} L ${cx-40} ${cy+12} Z" fill="#3B82F6" stroke="${COL.ink}" stroke-width="1.6"/>`;
    // 창문
    s += `<path d="M ${cx-32} ${cy-3} L ${cx-25} ${cy-22} L ${cx+25} ${cy-22} L ${cx+32} ${cy-3} Z" fill="#DBEAFE"/>`;
    // 바퀴
    s += `<circle cx="${cx-22}" cy="${cy+13}" r="6" fill="${COL.ink}"/>`;
    s += `<circle cx="${cx+22}" cy="${cy+13}" r="6" fill="${COL.ink}"/>`;
    // 센서 (위 LIDAR)
    s += `<rect x="${cx-5}" y="${cy-32}" width="10" height="6" fill="${COL.bear}"/>`;
    s += `<circle cx="${cx}" cy="${cy-29}" r="2" fill="#FBBF24"/>`;
    // 센서 빔
    s += `<path d="M ${cx} ${cy-30} L ${cx-30} ${cy-50} L ${cx+30} ${cy-50} Z" fill="${COL.bear}" opacity="0.2" stroke="${COL.bear}" stroke-width="1" stroke-dasharray="2 1.5"/>`;
    s += txt(cx, cy-58, '자율주행차', {color:COL.ink, size:11, weight:800, anchor:'middle'});
    // 기술 컴포넌트
    s += `<rect x="155" y="60" width="${W-175}" height="70" fill="#FFFBEB" rx="6" stroke="#D97706" stroke-width="1.4"/>`;
    s += txt((155+W-20)/2, 76, '자율주행 부품·SW', {color:'#92400E', size:11, weight:800, anchor:'middle'});
    [
      {x:175, label:'AP·SoC', co:'삼성·LX세미', color:'#10B981'},
      {x:235, label:'센서·LiDAR', co:'에스에이엠', color:'#EF4444'},
      {x:295, label:'배터리', co:'LG엔솔', color:'#F59E0B'},
      {x:360, label:'완성차', co:'현대차·기아', color:'#8B5CF6'},
    ].forEach(it=>{
      s += `<rect x="${it.x-22}" y="86" width="44" height="22" fill="${it.color}" rx="3"/>`;
      s += txt(it.x, 100, it.label, {color:'#fff', size:9.5, weight:800, anchor:'middle'});
      s += txt(it.x, 122, it.co, {color:it.color, size:8.5, weight:700, anchor:'middle'});
    });
    s += txt(W/2, 158, '🌱 성장 동력: 테슬라 FSD · 로보택시 · 전기차 확대', {color:COL.bull, size:11, weight:700, anchor:'middle'});
    s += txt(W/2, 176, '👉 핵심: 레벨 2 → 4 단계별 상용화 시점', {color:COL.ink, weight:800, size:11, anchor:'middle'});
    s += txt(W/2, 194, '⚠️ 리스크: 규제 · 사고 책임 · 기술 안정성', {color:COL.bear, size:10.5, weight:700, anchor:'middle'});
    s += txt(W/2, 213, '대표: 현대차 · 기아 · 만도 · 에이치엘만도', {color:COL.muted, size:10.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  sector_robot: ()=>{
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '🦾 로봇 — 산업용·서비스 로봇 시장 확대', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 로봇 일러스트 (좌측)
    const rx=80, ry=120;
    // 머리
    s += `<rect x="${rx-22}" y="${ry-50}" width="44" height="32" fill="#94A3B8" stroke="${COL.ink}" stroke-width="1.8" rx="6"/>`;
    s += `<circle cx="${rx-10}" cy="${ry-36}" r="4" fill="${COL.bear}"/>`;
    s += `<circle cx="${rx+10}" cy="${ry-36}" r="4" fill="${COL.bear}"/>`;
    // 입 (격자)
    s += `<rect x="${rx-8}" y="${ry-25}" width="16" height="3" fill="${COL.ink}"/>`;
    // 안테나
    s += `<line x1="${rx}" y1="${ry-50}" x2="${rx}" y2="${ry-58}" stroke="${COL.ink}" stroke-width="2"/>`;
    s += `<circle cx="${rx}" cy="${ry-60}" r="3" fill="${COL.bear}"/>`;
    // 몸통
    s += `<rect x="${rx-25}" y="${ry-15}" width="50" height="40" fill="#3B82F6" stroke="${COL.ink}" stroke-width="1.8" rx="4"/>`;
    s += `<rect x="${rx-12}" y="${ry-5}" width="24" height="20" fill="#1E40AF" rx="2"/>`;
    s += `<text x="${rx}" y="${ry+10}" font-size="11" font-weight="900" fill="#FBBF24" text-anchor="middle">⚙</text>`;
    // 팔
    s += `<rect x="${rx-38}" y="${ry-13}" width="13" height="28" fill="#94A3B8" stroke="${COL.ink}" stroke-width="1.5" rx="2"/>`;
    s += `<rect x="${rx+25}" y="${ry-13}" width="13" height="28" fill="#94A3B8" stroke="${COL.ink}" stroke-width="1.5" rx="2"/>`;
    s += txt(rx, ry-66, '협동·서비스 로봇', {color:COL.ink, size:11, weight:800, anchor:'middle'});
    // 분류
    s += `<rect x="155" y="60" width="${W-175}" height="70" fill="#ECFDF5" rx="6" stroke="${COL.bull}" stroke-width="1.4"/>`;
    s += txt((155+W-20)/2, 76, '로봇 산업 분류', {color:COL.bull, size:11, weight:800, anchor:'middle'});
    [
      {x:175, label:'산업용', co:'두산로보', color:'#10B981'},
      {x:235, label:'협동로봇', co:'레인보우', color:'#3B82F6'},
      {x:295, label:'서비스', co:'에브리봇', color:'#F59E0B'},
      {x:360, label:'부품·감속기', co:'에스피지', color:'#8B5CF6'},
    ].forEach(it=>{
      s += `<rect x="${it.x-22}" y="86" width="44" height="22" fill="${it.color}" rx="3"/>`;
      s += txt(it.x, 100, it.label, {color:'#fff', size:9.5, weight:800, anchor:'middle'});
      s += txt(it.x, 122, it.co, {color:it.color, size:8.5, weight:700, anchor:'middle'});
    });
    s += txt(W/2, 158, '🌱 성장 동력: 인구 감소 · 인건비 ↑ · 자동화 수요', {color:COL.bull, size:11, weight:700, anchor:'middle'});
    s += txt(W/2, 176, '👉 핵심: 매출 성장 · 영업이익 전환 시점', {color:COL.ink, weight:800, size:11, anchor:'middle'});
    s += txt(W/2, 194, '⚠️ 리스크: 적자 지속 · 일회성 수주 · 기대감 과열', {color:COL.bear, size:10.5, weight:700, anchor:'middle'});
    s += txt(W/2, 213, '대표: 두산로보틱스 · 레인보우로보틱스 · 로보스타', {color:COL.muted, size:10.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  bubble_burst: ()=>{
    // 거품 = 테마주 버블
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 18, '테마주 거품 — 기대 → 폭등 → 붕괴', {color:COL.ink, weight:800, size:13.5, anchor:'middle'});
    // 거품 3개 (점점 커지는 + 마지막 폭발)
    const bubbles = [
      {cx:60, cy:130, r:18, alpha:0.85, label:'기대',    txt:'+30%', color:'#34D399'},
      {cx:130, cy:120, r:30, alpha:0.65, label:'급등',    txt:'+200%', color:'#10B981'},
      {cx:215, cy:105, r:45, alpha:0.4, label:'과열',    txt:'+500%', color:'#F59E0B'},
    ];
    bubbles.forEach(b=>{
      s += `<circle cx="${b.cx}" cy="${b.cy}" r="${b.r}" fill="${b.color}" opacity="${b.alpha}" stroke="${b.color}" stroke-width="1.5"/>`;
      // 빛반사
      s += `<ellipse cx="${b.cx-b.r*0.3}" cy="${b.cy-b.r*0.4}" rx="${b.r*0.25}" ry="${b.r*0.18}" fill="#fff" opacity="0.6"/>`;
      s += txt(b.cx, b.cy-2, b.label, {color:'#fff', size:13, weight:800, anchor:'middle'});
      s += txt(b.cx, b.cy+10, b.txt, {color:'#fff', size:12, weight:800, anchor:'middle'});
    });
    // 마지막 — 터진 거품 (X)
    const px=305, py=125, pr=42;
    // 터진 조각들
    for(let i=0;i<10;i++){
      const a = i*Math.PI*0.2;
      const dx = Math.cos(a)*(pr+12+(i%3)*5);
      const dy = Math.sin(a)*(pr+12+(i%3)*5);
      s += `<path d="M${px+dx*0.5},${py+dy*0.5} L${px+dx},${py+dy} L${px+dx*0.7+5},${py+dy*0.7-5} Z" fill="${COL.bear}" opacity="${0.6-i*0.04}"/>`;
    }
    s += `<text x="${px}" y="${py-2}" font-size="14" font-weight="900" fill="${COL.bear}" text-anchor="middle">💥</text>`;
    s += txt(px, py+12, '폭락', {color:COL.bear, size:13, weight:800, anchor:'middle'});
    s += txt(px, py+24, '−80%', {color:COL.bear, size:12, weight:800, anchor:'middle'});
    // 화살표 (붕괴)
    s += `<text x="${px-pr-15}" y="${py+5}" font-size="20" fill="${COL.bear}">→</text>`;
    s += txt(W/2, 195, '실적 없는 기대감만의 거품은 결국 터집니다', {color:COL.muted, size:11.5, weight:700, anchor:'middle'});
    return svgWrap(s);
  },

  safety_margin: ()=>{
    // 안전 마진 = 적정가치 - 매수가격 (좌우 막대 분리, 가운데 갭)
    let s = `<rect x="0" y="0" width="${W}" height="${H}" fill="${COL.panel}"/>`;
    s += txt(W/2, 22, '🛡️ 안전 마진 = 적정가치 − 매수가격', {color:COL.ink, weight:800, size:13, anchor:'middle'});
    const baseY = 200, vTop = 60, mTop = 130;
    const vx = 70, mx = W-70;
    // 적정 가치 (높은 막대 — 좌)
    s += `<rect x="${vx-32}" y="${vTop}" width="64" height="${baseY-vTop}" fill="${COL.bull}" opacity="0.3" stroke="${COL.bull}" stroke-width="1.6" rx="4"/>`;
    s += txt(vx, vTop-8, '적정 가치', {color:COL.bull, size:11.5, weight:800, anchor:'middle'});
    s += txt(vx, vTop+25, '100,000', {color:COL.bull, size:13, weight:900, anchor:'middle'});
    s += txt(vx, vTop+40, '원', {color:COL.bull, size:10.5, weight:800, anchor:'middle'});
    // 매수 가격 (낮은 막대 — 우)
    s += `<rect x="${mx-32}" y="${mTop}" width="64" height="${baseY-mTop}" fill="${COL.bear}" opacity="0.3" stroke="${COL.bear}" stroke-width="1.6" rx="4"/>`;
    s += txt(mx, mTop-8, '매수 가격', {color:COL.bear, size:11.5, weight:800, anchor:'middle'});
    s += txt(mx, mTop+22, '70,000', {color:COL.bear, size:13, weight:900, anchor:'middle'});
    s += txt(mx, mTop+37, '원', {color:COL.bear, size:10.5, weight:800, anchor:'middle'});
    // 가운데 갭 라벨 박스
    s += `<rect x="${W/2-55}" y="85" width="110" height="56" fill="#FEF3C7" stroke="#D97706" stroke-width="1.8" rx="6"/>`;
    s += txt(W/2, 102, '안전 마진', {color:'#92400E', size:12, weight:800, anchor:'middle'});
    s += txt(W/2, 124, '+30%', {color:'#92400E', size:20, weight:900, anchor:'middle'});
    // 갭 점선
    s += `<line x1="${vx+32}" y1="${vTop+2}" x2="${W/2-55}" y2="100" stroke="#D97706" stroke-width="1" stroke-dasharray="3 2"/>`;
    s += `<line x1="${W/2+55}" y1="100" x2="${mx-32}" y2="${mTop+2}" stroke="#D97706" stroke-width="1" stroke-dasharray="3 2"/>`;
    // 바닥
    s += `<line x1="20" y1="${baseY+1}" x2="${W-20}" y2="${baseY+1}" stroke="${COL.ink}" stroke-width="1.4"/>`;
    s += `<rect x="20" y="${H-30}" width="${W-40}" height="22" fill="#F1F5F9" rx="3"/>`;
    s += txt(W/2, H-15, '👉 충분히 낮은 가격 = 손실 방어 + 수익 여지', {color:COL.muted, size:11, weight:700, anchor:'middle'});
    return svgWrap(s);
  },
};

// ── idx → preset 매핑 (총 90장: 기존 66 + 차트 신규 10 + 일러스트 14) ────────────────
const IDX_TO_SVG = {
  // 신규(일러스트): 입문 D1 인플레이션
  1: 'inflation_decay',
  // 입문 Day 4 ⑤ 기술적투자
  39: 'ma_with_volume', 40: 'ma_with_volume',
  // 신규: 입문 Day 5 캔들 해부도
  45: 'candle_anatomy', 46: 'candle_anatomy',
  // 신규: 입문 Day 7 시가총액 비중
  71: 'market_cap_treemap',
  // 신규: 입문 Day 8 호가창
  73: 'order_book',
  // 신규(일러스트): 입문 D3 거래 시간표
  25: 'trading_clock',
  // 입문 Day 9 이동평균선
  83: 'ma_basic', 84: 'ma_basic',
  85: 'ma_5_20',
  87: 'ma_basic', 88: 'ma_basic',
  89: 'ma_golden_cross', 90: 'ma_golden_cross',
  // 입문 Day 13 거래량
  119: 'support_line',
  121: 'candle_long_bullish', 122: 'candle_long_bullish',
  // 입문 Day 17 지지·저항
  149: 'support_line', 150: 'support_line',
  151: 'resistance_line', 152: 'resistance_line',
  153: 'breakout_high',
  155: 'breakdown_low',
  // 입문 Day 22 추세선·박스
  191: 'uptrend_line', 192: 'uptrend_line',
  193: 'downtrend_line', 194: 'downtrend_line',
  195: 'trend_break',
  197: 'box_range', 198: 'box_range',
  // 초급 Day 4 거래량+이평
  267: 'ma_with_volume', 268: 'ma_with_volume',
  269: 'ma_aligned', 270: 'ma_inverse',
  // 신규: 초급 Day 4 60·120일선
  271: 'ma_60_120', 272: 'ma_60_120',
  273: 'ma_golden_cross', 274: 'ma_dead_cross',
  275: 'ma_converge', 276: 'ma_diverge',
  // 초급 Day 9 캔들 패턴
  315: 'candle_hammer', 316: 'candle_inverted_hammer',
  317: 'candle_long_bullish', 318: 'candle_doji',
  321: 'candle_harami', 322: 'candle_engulfing',
  // 초급 Day 12 RSI
  339: 'rsi_basic', 340: 'rsi_basic',
  341: 'rsi_overbought', 342: 'rsi_oversold',
  343: 'rsi_oversold', 344: 'rsi_oversold',
  345: 'rsi_divergence', 346: 'rsi_divergence',
  347: 'rsi_ranging_market', 348: 'rsi_strong_trend',
  // 신규: 초급 Day 18 눌림목·손절선 + 이평 지지
  402: 'pullback', 403: 'pullback',
  404: 'ma_support', 405: 'ma_support',
  406: 'pullback_volume',
  408: 'stop_loss', 409: 'stop_loss',
  // 초급 Day 19 MACD
  410: 'macd_basic', 411: 'macd_cross_buy',
  412: 'macd_basic', 413: 'macd_cross_buy',
  414: 'macd_zero_break', 415: 'macd_zero_break',
  416: 'macd_trend', 417: 'macd_trend',
  // 초급 Day 21 박스권
  430: 'box_range', 431: 'box_breakout_top',
  432: 'box_buy_low', 433: 'box_buy_low',
  434: 'fake_breakout', 435: 'fake_breakout',
  436: 'box_target',
  // ===== 신규 일러스트 매핑 (placeholder → image) =====
  // 입문 D1 복리·72의 법칙
  6: 'snowball_compound', 7: 'snowball_compound',
  // 입문 D11 금리·주가 시소
  99: 'seesaw_rate_stock', 100: 'seesaw_rate_stock',
  // 입문 D17 바닥 밑 지하실
  156: 'basement_below_floor',
  // 입문 D21 테마 거품
  185: 'bubble_burst',
  // 초급 D1 복리
  241: 'snowball_compound', 243: 'snowball_compound',
  // 초급 D2 재무제표 = 건강검진표
  247: 'health_checkup',
  // 입문 D4 거북이 vs 토끼 (장기 인내)
  32: 'turtle_vs_hare', 36: 'turtle_vs_hare',
  // 입문 D12 가치주 = 인내 (낚시)
  112: 'fishing_patience',
  // 입문 D8 VI/서킷브레이커 = 폭풍
  81: 'storm_market',
  // 초급 D23 손절 = 폭풍 대응
  201: 'storm_market',
  // 초급 D7 미국 → 한국 시장 도미노
  300: 'domino_chain',
  244: 'investment_compass',  // 초급 D1 투자 철학
  // ===== 섹터 종합 일러스트 (각 섹터 입문 D1 + 초급 D1 대표 카드에 적용) =====
  // 2차전지 (입문 D1·D2 + 초급 D1·D2)
  484: 'sector_battery', 486: 'sector_battery', 524: 'sector_battery', 525: 'sector_battery',
  // 전력기기
  492: 'sector_power', 494: 'sector_power', 532: 'sector_power', 533: 'sector_power',
  // 바이오
  500: 'sector_bio', 502: 'sector_bio', 540: 'sector_bio', 541: 'sector_bio',
  // 원전
  508: 'sector_nuclear', 510: 'sector_nuclear', 548: 'sector_nuclear', 549: 'sector_nuclear',
  // 우주/로켓
  516: 'sector_space', 518: 'sector_space', 556: 'sector_space', 557: 'sector_space',
  // ===== 신규 섹터 5종 (idx 564~643, 각 섹터당 16장이지만 D1·D2 대표 카드 4장씩 매핑) =====
  // AI 반도체 (idx 564~579)
  564: 'sector_ai_semi', 566: 'sector_ai_semi', 572: 'sector_ai_semi', 573: 'sector_ai_semi',
  // 방산 (idx 580~595)
  580: 'sector_defense', 582: 'sector_defense', 588: 'sector_defense', 589: 'sector_defense',
  // 양자컴퓨터 (idx 596~611)
  596: 'sector_quantum', 598: 'sector_quantum', 604: 'sector_quantum', 605: 'sector_quantum',
  // 자율주행 (idx 612~627)
  612: 'sector_autonomous', 614: 'sector_autonomous', 620: 'sector_autonomous', 621: 'sector_autonomous',
  // 로봇 (idx 628~643)
  628: 'sector_robot', 630: 'sector_robot', 636: 'sector_robot', 637: 'sector_robot',
  // ===== 실생활 비유 일러스트 =====
  // PER (입문 D10, 초급 D8 PBR도 비슷한 개념)
  91: 'per_real_estate', 92: 'per_real_estate', 95: 'per_real_estate',
  // EPS (입문 D19)
  175: 'eps_per_share', 176: 'eps_per_share',
  // ETF 장바구니 (입문 D24 분산투자 + ETF 카드)
  378: 'etf_box', 394: 'etf_box',
  // 배당 보너스 (입문 D15)
  131: 'dividend_bonus', 132: 'dividend_bonus', 135: 'dividend_bonus',
  // 성장주 묘목 (입문 D12)
  109: 'growth_sapling', 110: 'growth_sapling',
  // 우량주 빌딩 (입문 D12)
  107: 'bluechip_solid',
  // ===== 추가 메타포 일러스트 =====
  // 코스피·다우·나스닥 = 등대
  20: 'lighthouse_index', 21: 'lighthouse_index',
  295: 'lighthouse_index', 297: 'lighthouse_index',
  // === 재무제표 D6 — 슬라이드별 전용 ===
  51: 'revenue_growth_bars',     // 매출액 정의
  52: 'revenue_growth_bars',     // 매출액 체크
  53: 'operating_breakdown',     // 영업이익 정의
  54: 'operating_breakdown',     // 영업이익 체크
  55: 'net_profit_waterfall',    // 당기순이익 정의
  56: 'net_profit_waterfall',    // 당기순이익 체크
  60: 'revenue_price_diverge',   // 매출↑ 주가↓
  62: 'revenue_vs_profit_compare', // 매출 vs 이익 성장
  64: 'operating_margin_donut',  // 영업이익률
  // === 재무제표 D2-D3 ===
  248: 'financial_5docs',        // 5가지 종류
  257: 'four_indicators',        // 핵심 4지표
  265: 'cash_flow_river',        // 현금흐름표
  266: 'cash_flow_river',        // 흑자부도
  // === PER/PBR ===
  92: 'per_formula',             // PER 계산식
  93: 'per_sector_compare',      // 업종별 PER
  95: 'per_forward_vs_past',     // 선행 PER
  97: 'per_value_trap',          // PER 한계·함정
  304: 'pbr_concept',            // PBR 정의
  306: 'pbr_under_one',          // PBR 1배 미만
  308: 'value_diamond',          // 저PBR 가치주
  311: 'liquidation_value',      // 청산 가치
  // === 옛 매핑 (제거됨) ===
  // 51,53 → revenue_growth_bars/operating_breakdown으로 이동
  // 수수료·세금 = 가위
  123: 'tax_scissors', 125: 'tax_scissors',
  // 액면분할
  223: 'stock_split', 222: 'stock_split',
  // 수주 계약 = 악수
  143: 'hands_shake',
  // 장기 투자 = 산 등반
  2: 'mountain_climb', 35: 'mountain_climb',
  // ===== placeholder 대량 재활용 매핑 =====
  // candle_anatomy 재사용 (양봉·음봉 심리, OHLC)
  48: 'candle_anatomy', 50: 'candle_anatomy',
  // (이전 report_card / health_checkup 매핑은 위쪽 슬라이드 전용으로 이동됨)
  // growth_sapling 재사용 (적자 → 흑자 전환)
  66: 'growth_sapling',
  // market_cap_treemap 재사용 (섹터 분류, 시총 비중)
  68: 'market_cap_treemap', 72: 'market_cap_treemap', 168: 'market_cap_treemap',
  // order_book 재사용 (호가창 실전 포인트)
  74: 'order_book',
  // (304, 306, 308 매핑은 위 PBR 전용으로 이동됨)
  // safety_margin (안전 마진만 — 청산 가치는 liquidation_value)
  314: 'safety_margin',
  // trading_clock 재사용 (시간외 거래, 동시호가)
  438: 'trading_clock', 440: 'trading_clock',
  // storm_market 재사용 (미수·신용 위험)
  80: 'storm_market',
  // 섹터 카드 재사용
  487: 'sector_battery', 490: 'sector_battery',
  496: 'sector_power', 498: 'sector_power', 538: 'sector_power',
  506: 'sector_bio', 546: 'sector_bio',
  512: 'sector_nuclear', 514: 'sector_nuclear', 554: 'sector_nuclear',
  520: 'sector_space', 522: 'sector_space', 562: 'sector_space',
  // ===== 신규 일러스트 매핑 =====
  // 갭 상승·갭 하락
  319: 'gap_up_down', 320: 'gap_up_down',
  // 외국인·기관·개인
  287: 'investor_groups', 289: 'investor_groups',
  291: 'investor_groups', 292: 'investor_groups',
  // 증권사 앱·계좌 개설
  15: 'broker_app', 17: 'broker_app', 19: 'broker_app',
  253: 'vault_empty',         // 초급 D2 자본잠식
  263: 'vault_full',          // 초급 D3 이익잉여금
  277: 'report_magnifier',    // 초급 D5 증권사 리포트
  313: 'safety_margin',       // 초급 D8 안전 마진
  447: 'fomo_runners',        // 초급 D23 FOMO
  453: 'trading_journal',     // 초급 D23 매매 일지
  455: 'fear_greed_gauge',    // 초급 D24 공포탐욕지수
  458: 'fear_greed_gauge',    // 초급 D24 VIX
  464: 'eggs_baskets',        // 초급 D25 분산 투자
  465: 'eggs_baskets',        // 초급 D25 자산 배분
  467: 'portfolio_pie',       // 초급 D25 종목 비중
  469: 'rebalance_scale',     // 초급 D25 리밸런싱
};

global.CHART_SVG_PRESETS = PRESETS;
global.IDX_TO_SVG = IDX_TO_SVG;

})(typeof window !== 'undefined' ? window : globalThis);
