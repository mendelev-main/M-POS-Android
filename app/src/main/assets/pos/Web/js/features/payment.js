/* ---- Payment ---- */
function paymentCartLines(){
  return state.cart.map(i=>{
    const discount=discountValue(i),discountName=(state.discounts.find(d=>d.id===i.discountId)||{}).name||'Скидка';
    return `<div class="receipt-line"><span>${escapeHtml(i.name)} × ${i.qty}</span><span>${money(itemTotal(i))}</span></div>${discount>0?`<div class="receipt-line payment-line-discount"><span>↳ ${escapeHtml(discountName)}</span><span>−${money(discount)}</span></div>`:''}`;
  }).join('');
}
function paymentReceiptHtml(){
  const delivery=state.orderType==='Доставка'?Number(state.deliveryFee||0):0,loyalty=loyaltyReceiptSnapshot();
  return `${state.orderLabel?`<div class="payment-order-label">${escapeHtml(state.orderLabel)}</div>`:''}
    <div class="payment-order-meta">${escapeHtml(state.orderType||'На месте')}${state.customer.name?' · '+escapeHtml(state.customer.name):''}</div>
    ${paymentCartLines()}
    ${loyalty.programs.map(program=>`<div class="receipt-line payment-line-discount"><span>Лояльность · ${escapeHtml(program.name)}</span><span>−${money(program.discount)}</span></div>`).join('')}
    ${delivery>0?`<div class="receipt-line"><span>Доставка</span><span>${fullMoney(delivery)}</span></div>`:''}
    <div class="receipt-total"><span>Итого</span><span>${fullMoney(cartTotal())}</span></div>`;
}
function cashQuickValues(total){
  const vals=[total,Math.ceil(total/5)*5,Math.ceil(total/10)*10,Math.ceil(total/20)*20,Math.ceil(total/50)*50,Math.ceil(total/100)*100,Math.ceil(total/200)*200]
    .map(v=>Math.round(v*100)/100).filter(v=>v>=total && v>0);
  return [...new Set(vals)];
}
function hasDeliveryTariff(){
 return state.orderType!=='Доставка'||(state.deliveryTariffSelected===true&&Number.isFinite(Number(state.deliveryFee))&&Number(state.deliveryFee)>=0&&state.deliveryRates.some(r=>Number(r.amount)===Number(state.deliveryFee)));
}
function requireDeliveryTariff(){
 if(hasDeliveryTariff())return true;
 flash('Выберите тариф доставки перед оплатой');openOrderSettings();return false;
}
function openPaymentModal(){
  if(!requireDeliveryTariff())return;
  if(!state.cart.length){flash('Заказ пуст');return;}
  if(!currentShift()){flash('Смена не открыта');return;}
  if(!canFulfillCart()) return;
  state.paymentPage='main'; renderPaymentScreen();
  if(hasPaidSplitPayment())setTimeout(()=>renderSplitPayment(),0);
}
function renderPaymentScreen(){
  state.paymentPage='main';
  const total=cartTotal();
  closeModal();
  document.getElementById('payment-page-root')?.remove();
  const page=document.createElement('div');
  page.id='payment-page-root';
  page.className='payment-page';
  page.innerHTML=`<div class="payment-page-header"><button class="payment-page-back" onclick="closePaymentPage()">← Назад</button><div class="payment-page-title">Оплата</div></div><div class="payment-page-body"><div class="payment-layout">
      <div class="payment-receipt">
        <div class="payment-section-title">Чек</div>
        ${paymentReceiptHtml()}
      </div>
      <div class="payment-box" id="payment-workspace">
        <div class="payment-total-row">
          <div><div class="payment-total-label">К оплате</div><div class="payment-total-big">${fullMoney(total)}</div></div>
          <button class="btn btn-outline payment-split-trigger" onclick="openSplitPayment()">Разделить</button>
        </div>
        <div class="field"><label>Сумма</label><div class="payment-amount-display" id="paymentCashGiven" role="button" tabindex="0" onclick="openPaymentKeypad(${total})" onkeydown="if(event.key==='Enter'||event.key===' ')openPaymentKeypad(${total})">${fullMoney(total)}</div></div>
        <div class="payment-keypad" id="paymentKeypad" hidden>
          <button type="button" class="payment-key" onclick="paymentKeyPress('1',${total})">1</button><button type="button" class="payment-key" onclick="paymentKeyPress('2',${total})">2</button><button type="button" class="payment-key" onclick="paymentKeyPress('3',${total})">3</button>
          <button type="button" class="payment-key" onclick="paymentKeyPress('4',${total})">4</button><button type="button" class="payment-key" onclick="paymentKeyPress('5',${total})">5</button><button type="button" class="payment-key" onclick="paymentKeyPress('6',${total})">6</button>
          <button type="button" class="payment-key" onclick="paymentKeyPress('7',${total})">7</button><button type="button" class="payment-key" onclick="paymentKeyPress('8',${total})">8</button><button type="button" class="payment-key" onclick="paymentKeyPress('9',${total})">9</button>
          <button type="button" class="payment-key key-action" onclick="paymentKeyPress(',',${total})">,</button><button type="button" class="payment-key key-zero" onclick="paymentKeyPress('0',${total})">0</button>
          <button type="button" class="payment-key key-action" onclick="paymentKeyPress('backspace',${total})">⌫</button>
        </div>
        <div class="payment-quick" id="paymentQuick">${cashQuickValues(total).map(v=>`<button type="button" onclick="setPaymentCash(${v},${total})">${fullMoney(v)}</button>`).join('')}</div>
        <div class="field payment-change-field"><label>Сдача</label><div id="paymentChange" class="payment-change">0 ${state.currency}</div></div>
        <div class="payment-actions">
          <button class="btn btn-cash" onclick="confirmPaymentScreen('cash')">Оплатить</button>
          <button class="btn btn-card" onclick="confirmPaymentScreen('card')">Оплата картой</button>
        </div>
      </div>
    </div></div>`;
  document.body.appendChild(page);
  state._paymentCashGiven=Number(total)||0;
  setTimeout(()=>renderPaymentAmount(total),0);
}
function closePaymentPage(){
  if(hasPaidSplitPayment()){flash('Сначала завершите раздельную оплату');return false;}
  state.paymentPage='';state._splitPayments=[];state._splitCount=0;state._splitPaymentTotalCents=null;document.getElementById('payment-page-root')?.remove();closeModal();return true;
}
function paymentGivenValue(){return Number(String(state._paymentCashGiven??'').replace(',','.'))||0;}
function renderPaymentAmount(total){
  const el=document.getElementById('paymentCashGiven');
  if(el) el.textContent=fullMoney(paymentGivenValue());
  updatePaymentChange(total);
}
function openPaymentKeypad(total){
  state._paymentCashGiven='';
  const kp=document.getElementById('paymentKeypad');
  const quick=document.getElementById('paymentQuick');
  if(kp) kp.hidden=false;
  if(quick) quick.hidden=true;
  renderPaymentAmount(total);
}
function closePaymentKeypad(){
  const kp=document.getElementById('paymentKeypad');
  const quick=document.getElementById('paymentQuick');
  if(kp) kp.hidden=true;
  if(quick) quick.hidden=false;
}
function paymentKeyPress(key,total){
  let raw=String(state._paymentCashGiven??'');
  if(key==='backspace'){raw=raw.slice(0,-1);}
  else if(key===','){if(!raw.includes(','))raw+=',';}
  else {if(raw==='0')raw='';const parts=raw.split(',');if(parts[1]?.length>=2)return;raw+=key;}
  const num=Number(raw.replace(',','.'));
  if(!Number.isFinite(num)) return;
  state._paymentCashGiven=raw;
  renderPaymentAmount(total);
}
function setPaymentCash(v,total){state._paymentCashGiven=Number(v)||0;renderPaymentAmount(total);}
function updatePaymentChange(total){const out=document.getElementById('paymentChange');if(!out)return;const given=paymentGivenValue();out.textContent=fullMoney(Math.max(0,given-total));}
function openSplitPayment(loyaltyValidated=false){
  if(hasPaidSplitPayment()){renderSplitPayment();return true;}
  if(hasSelectedLoyaltyReward()&&!loyaltyValidated)return beginPaymentWithLoyaltyGuard(()=>openSplitPayment(true));
  if(loyaltyValidated)renderPaymentScreen();
  const total=cartTotal();
  if(!Array.isArray(state._splitPayments)||!state._splitPayments.length){
    state._splitPayments=buildSplitPayments(2,total);
    state._splitCount=2;
    state._splitPaymentTotalCents=Math.round(total*100);
  }else{
    normalizeSplitPayments(total);
  }
  renderSplitPayment();
  return true;
}
function normalizePaymentParts(payments){
  if(!Array.isArray(payments)||!payments.length)throw new Error('Платежи не указаны');
  return payments.map(part=>{
    const method=String(part?.method||'');
    if(method!=='cash'&&method!=='card')throw new Error('Неизвестный способ оплаты');
    const rawAmount=Number(part?.amount);
    if(!Number.isFinite(rawAmount)||rawAmount<0)throw new Error('Некорректная сумма оплаты');
    const amount=Math.round(rawAmount*100)/100;
    let cashGiven=part?.cashGiven==null?null:Number(part.cashGiven);
    let change=part?.change==null?null:Number(part.change);
    if(cashGiven!==null&&(!Number.isFinite(cashGiven)||cashGiven<0))throw new Error('Некорректная внесённая сумма');
    if(change!==null&&(!Number.isFinite(change)||change<0))throw new Error('Некорректная сдача');
    if(method==='cash'&&cashGiven!==null&&cashGiven+0.0001<amount)throw new Error('Недостаточно внесённой суммы');
    if(method==='card'&&(cashGiven!==null||change!==null))throw new Error('Некорректные данные оплаты картой');
    cashGiven=cashGiven===null?null:Math.round(cashGiven*100)/100;
    change=change===null?null:Math.round(change*100)/100;
    if(method==='cash'&&cashGiven!==null&&change!==null&&Math.abs(change-(cashGiven-amount))>0.001)throw new Error('Некорректная сдача');
    return {method,amount,paid:part?.paid===true,cashGiven,change};
  });
}
function validateSplitPaymentDraft(draft,expectedTotal){
  if(draft==null)return null;
  try{
    if(!draft||typeof draft!=='object'||Array.isArray(draft)||draft.version!==1)throw new Error('Некорректная версия');
    if(!Number.isInteger(draft.totalCents)||draft.totalCents<0)throw new Error('Некорректная сумма');
    const parts=normalizePaymentParts(draft.parts);
    if(parts.length<2||parts.length>10||!parts.some(part=>part.paid))throw new Error('Некорректные части');
    const partsCents=parts.reduce((sum,part)=>sum+Math.round(part.amount*100),0);
    if(partsCents!==draft.totalCents||draft.totalCents!==Math.round(Number(expectedTotal)*100))throw new Error('Сумма заказа изменилась');
    return {version:1,totalCents:draft.totalCents,parts,updatedAt:Number(draft.updatedAt)||Date.now()};
  }catch(_e){return null}
}
function hasPaidSplitPayment(payments=state._splitPayments){return Array.isArray(payments)&&payments.some(part=>part?.paid===true)}
function splitPaymentDraftSnapshot(payments=state._splitPayments){
  if(!hasPaidSplitPayment(payments))return null;
  const parts=normalizePaymentParts(payments);
  const totalCents=Number.isInteger(state._splitPaymentTotalCents)?state._splitPaymentTotalCents:Math.round(cartTotal()*100);
  if(parts.reduce((sum,part)=>sum+Math.round(part.amount*100),0)!==totalCents)throw new Error('Сумма частей оплаты не совпадает с чеком');
  return {version:1,totalCents,parts,updatedAt:Date.now()};
}
function returnFromSplitPayment(){
  if(hasPaidSplitPayment()){flash('Сначала завершите раздельную оплату');return false;}
  state._splitPayments=[];state._splitCount=0;state._splitPaymentTotalCents=null;renderPaymentScreen();return true;
}
function buildSplitPayments(count,total){
  count=Math.max(2,Math.min(10,count));
  const cents=Math.round(total*100), base=Math.floor(cents/count), rem=cents-base*count;
  return Array.from({length:count},(_,i)=>({method:'cash',amount:(base+(i<rem?1:0))/100,paid:false,cashGiven:null,change:null}));
}
function splitPaidTotal(){
  return (state._splitPayments||[]).filter(p=>p.paid).reduce((s,p)=>s+Number(p.amount||0),0);
}
function splitUnpaidTotal(){
  return (state._splitPayments||[]).filter(p=>!p.paid).reduce((s,p)=>s+Number(p.amount||0),0);
}
function distributeCentsEvenly(items,totalCents){
  if(!items.length)return;
  const base=Math.floor(totalCents/items.length), rem=totalCents-base*items.length;
  items.forEach((p,i)=>p.amount=(base+(i<rem?1:0))/100);
}
function normalizeSplitPayments(total){
  const ps=state._splitPayments||[];
  if(!ps.length)return;
  const paid=ps.filter(p=>p.paid), unpaid=ps.filter(p=>!p.paid);
  const paidCents=Math.round(paid.reduce((s,p)=>s+Number(p.amount||0),0)*100);
  const remainingCents=Math.max(0,Math.round(total*100)-paidCents);
  distributeCentsEvenly(unpaid,remainingCents);
  state._splitCount=ps.length;
}
function adjustSplitCount(delta){
  const total=cartTotal();
  let ps=Array.isArray(state._splitPayments)?state._splitPayments:buildSplitPayments(2,total);
  const next=Math.max(2,Math.min(10,ps.length+delta));
  if(next===ps.length){return;}
  const paid=ps.filter(p=>p.paid);
  const unpaid=ps.filter(p=>!p.paid);
  if(next<paid.length){
    flash('Нельзя уменьшить количество платежей: уже есть оплаченные части');
    return;
  }
  if(next>ps.length){
    const add=next-ps.length;
    for(let i=0;i<add;i++) unpaid.push({method:'cash',amount:0,paid:false,cashGiven:null,change:null});
  }else{
    const keepUnpaidCount=next-paid.length;
    while(unpaid.length>keepUnpaidCount) unpaid.pop();
  }
  const paidCents=Math.round(paid.reduce((s,p)=>s+Number(p.amount||0),0)*100);
  const remainingCents=Math.max(0,Math.round(total*100)-paidCents);
  distributeCentsEvenly(unpaid,remainingCents);
  state._splitPayments=[...paid,...unpaid];
  state._splitCount=next;
  renderSplitPayment();
}
function splitPaymentRemaining(){return Math.max(0,cartTotal()-splitPaidTotal());}
function parsePaymentNumber(value){
  const normalized=String(value ?? '').trim().replace(/\s/g,'').replace(',', '.');
  if(normalized==='' || normalized==='.' || normalized==='-') return null;
  if(!/^\d+(?:\.\d{0,2})?$/.test(normalized)) return null;
  const n=Number(normalized);
  return Number.isFinite(n) ? n : null;
}
function updateSplitAmountLive(index,value){
  const ps=state._splitPayments||[];
  const p=ps[index];
  if(!p||p.paid)return;

  // Do not write an empty/intermediate iPad input into state. While the
  // employee replaces the old value, the text field is allowed to be empty.
  const parsed=parsePaymentNumber(value);
  if(parsed===null)return;

  const totalCents=Math.max(0,Math.round(cartTotal()*100));
  const paidCents=ps.filter(x=>x.paid).reduce((sum,x)=>sum+Math.round(Number(x.amount||0)*100),0);
  const previousUnpaid=ps.filter((x,i)=>i<index&&!x.paid);
  const previousCents=previousUnpaid.reduce((sum,x)=>sum+Math.round(Number(x.amount||0)*100),0);
  const maxRequested=Math.max(0,totalCents-paidCents-previousCents);
  const requested=Math.min(Math.round(parsed*100),maxRequested);

  p.amount=requested/100;
  p.cashGiven=null;
  p.change=null;

  // Keep already entered/paid parts fixed. The edited payment becomes the
  // anchor and all later unpaid payments absorb the remaining amount.
  const following=ps.filter((x,i)=>i>index&&!x.paid);
  const remaining=Math.max(0,totalCents-paidCents-previousCents-requested);
  if(following.length){
    distributeCentsEvenly(following,remaining);
  }else{
    // Editing the last unpaid payment: keep earlier unpaid parts and make
    // the edited payment the exact remainder. If the requested amount was
    // below the possible remainder, redistribute the difference to the
    // other unpaid parts so the receipt total always stays exact.
    const others=ps.filter((x,i)=>i!==index&&!x.paid);
    const otherTarget=Math.max(0,totalCents-paidCents-requested);
    distributeCentsEvenly(others,otherTarget);
  }

  syncSplitPaymentUI();
}
function openSplitAmountKeypad(index){
  const p=state._splitPayments?.[index];
  if(!p||p.paid)return;
  state._activeSplitKeypad=index;
  state._splitAmountInput='';
  document.querySelectorAll('.split-keypad').forEach(el=>el.hidden=true);
  const kp=document.getElementById(`splitKeypad-${index}`);
  if(kp)kp.hidden=false;
  const display=document.querySelector(`#split-payment-${index} .split-amount-display`);
  if(display)display.textContent='0,00';
}
function splitKeyPress(index,key){
  const p=state._splitPayments?.[index];
  if(!p||p.paid||state._activeSplitKeypad!==index)return;
  let raw=String(state._splitAmountInput??'');
  if(key==='backspace') raw=raw.slice(0,-1);
  else if(key===','){ if(!raw.includes(',')) raw=raw||'0'; if(!raw.endsWith(',')) raw+=','; }
  else { if(raw==='0')raw=''; const parts=raw.split(','); if(parts[1]?.length>=2)return; raw+=key; }
  state._splitAmountInput=raw;
  if(parsePaymentNumber(raw)!==null) updateSplitAmountLive(index,raw);
  const display=document.querySelector(`#split-payment-${index} .split-amount-display`);
  if(display)display.textContent=raw||'0,00';
  syncSplitPaymentUI();
}
function closeSplitAmountKeypad(){
  document.querySelectorAll('.split-keypad').forEach(el=>el.hidden=true);
  state._activeSplitKeypad=null;
  state._splitAmountInput='';
  syncSplitPaymentUI();
}
function normalizeSplitInput(index){ closeSplitAmountKeypad(); }
function syncSplitPaymentUI(){
  const ps=state._splitPayments||[];
  ps.forEach((p,i)=>{
    const row=document.getElementById(`split-payment-${i}`);
    const input=row?.querySelector('.split-amount-display');
    if(input) input.textContent=Number(p.amount||0).toFixed(2).replace('.',',');
    const btn=row?.querySelector('.split-pay-btn');
    if(btn){
      const amount=Math.round(Number(p.amount||0)*100)/100;
      btn.disabled=!!p.paid||amount<=0;
      btn.textContent=p.paid?'Оплачено':'Оплатить '+fullMoney(amount);
    }
  });
  const paid=splitPaidTotal(), remaining=Math.max(0,cartTotal()-paid);
  const els=document.querySelectorAll('.split-summary-value');
  if(els[0])els[0].textContent=fullMoney(paid);
  if(els[1])els[1].textContent=fullMoney(remaining);
}
function renderSplitPayment(){
  const ps=state._splitPayments||[]; const total=cartTotal();
  const rows=ps.map((p,i)=>`<div id="split-payment-${i}" class="split-payment ${p.paid?'split-locked':''}">
    <div class="split-payment-top"><div class="split-payment-label">Платёж ${i+1}</div><div class="split-payment-status">${p.paid?'Оплачено':''}</div></div>
    <div class="split-payment-controls">
      <div class="split-methods">
        <button type="button" class="split-method-btn ${p.method==='cash'?'selected':''}" ${p.paid?'disabled':''} onclick="setSplitMethod(${i},'cash')">Наличные</button>
        <button type="button" class="split-method-btn ${p.method==='card'?'selected':''}" ${p.paid?'disabled':''} onclick="setSplitMethod(${i},'card')">Карта</button>
      </div>
      <div class="split-amount-display" role="button" tabindex="0" ${p.paid?'aria-disabled="true"':''} onclick="openSplitAmountKeypad(${i})" onkeydown="if(event.key==='Enter'||event.key===' ')openSplitAmountKeypad(${i})">${Number(p.amount||0).toFixed(2).replace('.',',')}</div>
    </div>
    <div class="split-keypad" id="splitKeypad-${i}" hidden>
      <button type="button" class="payment-key" onclick="splitKeyPress(${i},'1')">1</button><button type="button" class="payment-key" onclick="splitKeyPress(${i},'2')">2</button><button type="button" class="payment-key" onclick="splitKeyPress(${i},'3')">3</button>
      <button type="button" class="payment-key" onclick="splitKeyPress(${i},'4')">4</button><button type="button" class="payment-key" onclick="splitKeyPress(${i},'5')">5</button><button type="button" class="payment-key" onclick="splitKeyPress(${i},'6')">6</button>
      <button type="button" class="payment-key" onclick="splitKeyPress(${i},'7')">7</button><button type="button" class="payment-key" onclick="splitKeyPress(${i},'8')">8</button><button type="button" class="payment-key" onclick="splitKeyPress(${i},'9')">9</button>
      <button type="button" class="payment-key key-action" onclick="splitKeyPress(${i},',')">,</button><button type="button" class="payment-key key-zero" onclick="splitKeyPress(${i},'0')">0</button><button type="button" class="payment-key key-action" onclick="splitKeyPress(${i},'backspace')">⌫</button>
    </div>
    <div class="split-payment-actions"><button class="split-pay-btn btn ${p.method==='cash'?'btn-cash':'btn-card'}" ${p.paid||Number(p.amount||0)<=0?'disabled':''} onclick="paySplitPart(${i})">${p.paid?'Оплачено':'Оплатить '+fullMoney(p.amount)}</button></div>
  </div>`).join('');
  const paidTotal=splitPaidTotal();
  const remaining=Math.max(0,total-paidTotal);
  const workspace=document.getElementById('payment-workspace');
  if(!workspace){renderPaymentScreen();setTimeout(renderSplitPayment,0);return;}
  state.paymentPage='split';
  workspace.innerHTML=`
    <div class="payment-split-head">
      <div class="payment-split-title-row">
        <button class="btn btn-secondary payment-split-back" onclick="returnFromSplitPayment()">← Назад</button>
        <span class="payment-split-title">Разделить оплату</span>
      </div>
      <div class="split-count"><button onclick="adjustSplitCount(-1)">−</button><span>${ps.length}</span><button onclick="adjustSplitCount(1)">+</button></div>
    </div>
    <div class="split-payment-scroll">${rows}</div>
    <div class="split-summary"><span>Оплачено</span><span class="split-summary-value">${fullMoney(paidTotal)}</span></div>
    <div class="split-summary split-summary-remaining"><span>Осталось</span><span class="split-summary-value">${fullMoney(remaining)}</span></div>
    ${ps.length&&ps.every(p=>p.paid)?`<button class="btn btn-primary split-finish" onclick="finalizePayment(state._splitPayments)">Завершить оплату</button>`:''}`;
}
function setSplitMethod(index,method){
  const p=state._splitPayments?.[index];
  if(!p||p.paid)return;
  p.method=method;
  p.cashGiven=null;p.change=null;
  renderSplitPayment();
}
function paySplitPart(index){
  if(!canFulfillCart()) return;
  const p=state._splitPayments?.[index];
  if(!p || p.paid) return;
  const amount=Math.round(Number(p.amount||0)*100)/100;
  if(amount<=0){flash('Введите сумму платежа');return;}
  if(p.method==='cash'){
    // В раздельной оплате для наличных всегда открываем отдельное окно
    // с внесённой суммой, номиналами и расчётом сдачи.
    openSplitCashPayment(index);
    return;
  }
  if(p.method==='card'){
    openCardPartConfirmation(amount, ()=>completeSplitPayment(index));
    return;
  }
}
function splitCashDenominations(amount){
  const denominations=[5,10,20,50,100,200,500];
  // Показываем номиналы, которыми удобно оплатить данный платёж,
  // плюс точную сумму платежа.
  const values=[amount,...denominations.filter(v=>v>=amount)];
  return [...new Set(values.map(v=>Math.round(Number(v)*100)/100))];
}
function openSplitCashPayment(index){
  const p=state._splitPayments?.[index];
  if(!p || p.paid || p.method!=='cash') return;
  const amount=Math.round(Number(p.amount||0)*100)/100;
  if(amount<=0){flash('Введите сумму платежа');return;}

  const given=p.cashGiven!=null ? Number(p.cashGiven) : amount;
  const buttons=splitCashDenominations(amount).map(v=>`<button type="button" onclick="setSplitCashGiven(${v},${amount})">${fullMoney(v)}</button>`).join('');

  showModal(`<div class="modal-title">Оплата наличными</div>
    <div class="center-note payment-part-caption">Платёж ${index+1}</div>
    <div class="payment-part-amount">${fullMoney(amount)}</div>
    <div class="field"><label>Внесено наличными</label><input class="payment-input" type="number" id="splitCashGiven" min="0" step="0.01" inputmode="decimal" value="${given.toFixed(2)}" oninput="updateSplitCashChange(${amount})"></div>
    <div class="payment-quick">${buttons}</div>
    <div class="field payment-change-field"><label>Сдача</label><div id="splitCashChange" class="payment-change payment-change-large">${fullMoney(Math.max(0,given-amount))}</div></div>
    <div class="modal-actions">
      <button type="button" class="btn btn-secondary" onclick="closeModal();renderSplitPayment();">Отмена</button>
      <button type="button" class="btn btn-cash" onclick="confirmSplitCashPayment(${index},${amount})">Оплатить ${fullMoney(amount)}</button>
    </div>`, true);
  const modal=document.querySelector('#modal-root .modal');
  if(modal) modal.classList.add('payment-modal');
  setTimeout(()=>{
    updateSplitCashChange(amount);
    const input=document.getElementById('splitCashGiven');
    if(input){input.focus();input.select();}
  },50);
}
function splitCashQuickValues(total){ return splitCashDenominations(total); }
function setSplitCashGiven(v,total){
  const el=document.getElementById('splitCashGiven'); if(!el)return;
  el.value=Number(v).toFixed(2); updateSplitCashChange(total);
}
function updateSplitCashChange(total){
  const el=document.getElementById('splitCashGiven'), out=document.getElementById('splitCashChange');
  if(!el||!out)return;
  const given=Number(el.value)||0;
  out.textContent=fullMoney(Math.max(0,given-total));
}
function confirmSplitCashPayment(index,total){
  const p=state._splitPayments?.[index]; if(!p||p.paid)return;
  const given=Math.round((Number(document.getElementById('splitCashGiven')?.value)||0)*100)/100;
  if(given<total){flash('Недостаточно внесённой суммы');return;}
  p.cashGiven=given;
  p.change=Math.round((given-total)*100)/100;
  closeModal();
  completeSplitPayment(index);
}
async function completeSplitPayment(index){
  const p=state._splitPayments?.[index]; if(!p||p.paid)return false;
  if(criticalOperationBusy){flash('Сохранение операции ещё не завершено');return false;}
  const nextPayments=storageSnapshot(state._splitPayments);nextPayments[index].paid=true;
  let draft;
  try{draft=splitPaymentDraftSnapshot(nextPayments)}catch(e){flash(e?.message||'Некорректные данные оплаты');return false}
  criticalOperationBusy=true;
  try{await commitCriticalStorage('split-payment-progress',{currentOrderSession:currentOrderSessionSnapshot(draft)})}
  catch(e){flash('Часть оплаты не сохранена: '+(e?.message||e));return false}
  finally{criticalOperationBusy=false}
  state._splitPayments=nextPayments;state._splitCount=nextPayments.length;state._splitPaymentTotalCents=draft.totalCents;
  renderSplitPayment();
  if(nextPayments.every(x=>x.paid)) setTimeout(()=>finalizePayment(nextPayments),80);
  return true;
}
function openCardPartConfirmation(amount,onSuccess){
  showModal(`<div class="modal-title">Оплата картой</div>
    <div class="center-note payment-card-instruction">Проведите оплату на терминале</div>
    <div class="payment-card-amount">${fullMoney(amount)}</div>
    <div class="modal-actions">
      <button class="btn btn-secondary" onclick="closeModal();renderSplitPayment();">Отмена</button>
      <button class="btn btn-card" onclick="window.__cardPaymentConfirm&&window.__cardPaymentConfirm()">Оплата прошла</button>
    </div>`);
  window.__cardPaymentConfirm=()=>{window.__cardPaymentConfirm=null;closeModal();onSuccess();};
}
function hasSelectedLoyaltyReward(){return Object.values(state.loyaltyRedemptions||{}).some(v=>Math.max(0,Math.trunc(Number(v)||0))>0)}
async function revalidateSelectedLoyaltyReward(){
 if(!hasSelectedLoyaltyReward()||!state.customer?.id)return true;
 try{const data=await loyaltyApi('/api/customers/'+encodeURIComponent(state.customer.id)+'/loyalty');const byId=new Map((data.programs||[]).map(p=>[String(p.id),p]));for(const [programId,value] of Object.entries(state.loyaltyRedemptions||{})){const requested=Math.max(0,Math.trunc(Number(value)||0));if(requested>Number(byId.get(String(programId))?.rewards||0))return false}state.loyaltyPrograms=data.programs||[];return true}catch(e){return null}
}
let loyaltyPaymentGuardBusy=false;
function cancelOfflinePayment(){window.__offlinePaymentAction=null;closeModal()}
function continuePaymentWithoutLoyalty(){const action=window.__offlinePaymentAction;window.__offlinePaymentAction=null;state.loyaltyRedemptions={};closeModal();render();return typeof action==='function'?action():false}
async function beginPaymentWithLoyaltyGuard(action){
 if(!hasSelectedLoyaltyReward())return action();
 if(loyaltyPaymentGuardBusy)return false;
 loyaltyPaymentGuardBusy=true;
 let valid;
 try{valid=await revalidateSelectedLoyaltyReward()}
 finally{loyaltyPaymentGuardBusy=false}
 if(valid===true)return action();
 if(valid===false){flash('Подарок уже недоступен. Обновите клиента и повторите оплату');return}
 showModal(`<div class="modal-title">Нет связи с программой лояльности</div><div class="center-note payment-offline-note">Продажу и оплату можно продолжить без интернета. Подарок сейчас подтвердить нельзя, поэтому он не будет списан и скидка по нему не применится.</div><div class="modal-actions"><button class="btn btn-secondary" onclick="cancelOfflinePayment()">Вернуться</button><button class="btn btn-primary" onclick="continuePaymentWithoutLoyalty()">Продолжить без подарка</button></div>`,true);window.__offlinePaymentAction=action;
 return false;
}
function confirmPaymentScreen(method,loyaltyValidated=false){
  if(!requireDeliveryTariff())return;
  if(!canFulfillCart()) return;
  if(hasSelectedLoyaltyReward()&&!loyaltyValidated){beginPaymentWithLoyaltyGuard(()=>confirmPaymentScreen(method,true));return;}
  const total=cartTotal();
  if(method==='cash'){
    const given=Math.round(paymentGivenValue()*100)/100;
    if(given+0.0001<total){flash('Недостаточно внесённой суммы');return;}
    const change=Math.round((given-total)*100)/100;
    finalizePayment([{method:'cash',amount:total,cashGiven:given,change:change,paid:true}]);
  }else{
    openCardPartConfirmation(total, ()=>finalizePayment([{method:'card',amount:total,cashGiven:null,change:null,paid:true}]));
  }
}
async function finalizePayment(payments){
  if(criticalOperationBusy){flash('Сохранение операции ещё не завершено');return;}
  if(!requireDeliveryTariff())return;
  const shift=currentShift(); if(!shift){flash('Смена не открыта');return;}
  const total=cartTotal();
  let clean;
  try{clean=normalizePaymentParts(payments).map(({method,amount,cashGiven,change})=>({method,amount,cashGiven,change}))}
  catch(e){flash('Оплата не завершена: '+(e?.message||e));return}
  const paidTotal=Math.round(clean.reduce((s,p)=>s+p.amount,0)*100)/100;
  if(Math.abs(paidTotal-total)>0.001){flash('Сумма платежей не совпадает с суммой чека');return;}
  const cashAmount=clean.filter(p=>p.method==='cash').reduce((s,p)=>s+p.amount,0);
  const deliveryFee=state.orderType==='Доставка'?Number(state.deliveryFee||0):0;
  if(deliveryFee>0){const t=shiftTotals(shift.id),drawerCash=cashDrawerBalance(shift,t);if(!Number.isFinite(drawerCash)||drawerCash<0){flash('Некорректные данные кассовой смены');return;}const available=drawerCash+cashAmount;if(deliveryFee>available+0.0001){flash('Недостаточно наличных в кассе для списания доставки');return;}}
  if(!state.cart.length){flash('Заказ пуст');return;}
  let stockConsumption;
  try{ stockConsumption=checkedStockConsumption(state.cart); }
  catch(e){ flash(e.message); return; }
  const method=clean.length===1?clean[0].method:'split';
  const kitchenPrinted=!!window.__currentOrderKitchenPrinted;
  const receiptSequence=(state.orders.filter(o=>o.shiftId===shift.id).length+1);
  const shiftEmployee=state.employees.find(e=>e.id===shift.employeeId);
  const rewardAllocation=loyaltyRewardAllocation();
  const loyaltyReceipt=loyaltyReceiptSnapshot(rewardAllocation);
  const productDiscountTotal=Math.round(state.cart.reduce((sum,item)=>sum+discountValue(item),0)*100)/100;
  const subtotalBeforeDiscounts=Math.round(state.cart.reduce((sum,item)=>sum+Number(item.price||0)*Number(item.qty||0),0)*100)/100;
  for(const [programId,value] of Object.entries(state.loyaltyRedemptions||{})){
    const requested=Math.max(0,Math.trunc(Number(value)||0)),allocated=(rewardAllocation.allocations[programId]||[]).reduce((sum,item)=>sum+Number(item.quantity||0),0);
    if(requested!==allocated){flash('Недостаточно подходящих товаров для выбранного подарка');return}
  }
  const order={id:uid(),loyaltyRedemptions:storageSnapshot(state.loyaltyRedemptions||{}),loyaltyRewardAllocations:rewardAllocation.allocations,loyaltyDiscount:loyaltyReceipt.discount,loyaltyProgramsApplied:storageSnapshot(loyaltyReceipt.programs),productDiscountTotal,subtotalBeforeDiscounts,shiftId:shift.id,receiptNumber:receiptSequence,receiptDisplayNumber:`#${receiptSequence}`,employeeId:shift.employeeId||'',employeeName:shiftEmployee?.name||shiftEmployee?.fullName||'Сотрудник',registerName:'POS 1',method,total,payments:clean,cashGiven:method==='cash'?clean[0].cashGiven:null,change:method==='cash'?clean[0].change:null,orderLabel:state.orderLabel||'',orderType:state.orderType,deliveryFee:deliveryFee,customer:storageSnapshot(state.customer),source:state.currentOrderSource||'',webOrderId:state.currentWebOrderId||'',webOrderStatus:state.currentWebOrderStatus||'',items:state.cart.map(i=>{const d=state.discounts.find(x=>x.id===i.discountId),product=getProduct(i.productId);return {...i,category:product?.category||i.category||'',discountName:d?.name||'',discountType:d?.type||'',discountValue:Number(d?.value)||0,cost:product?(product.type==='simple'?product.cost:compositeCost(product)):0};}),timestamp:Date.now(),kitchenPrinted,printedItems:storageSnapshot(window.__currentOrderPrintedItems||[])};
  order.stockConsumption=stockConsumption;
  if(order.customer?.id)order.loyaltySync={status:'pending',at:Date.now(),attempts:0};
  const nextProducts=storageSnapshot(state.products),nextOrders=storageSnapshot(state.orders),nextShifts=storageSnapshot(state.shifts);
  for(const item of stockConsumption.items){
    const p=nextProducts.find(product=>product.id===item.productId);
    p.stock=roundStockQty(Math.max(0,Number(p.stock)-item.qty));
  }
  nextOrders.push(order);
  const nextShift=nextShifts.find(item=>item.id===shift.id);
  if(deliveryFee>0){if(!Array.isArray(nextShift.cashMovements))nextShift.cashMovements=[];nextShift.cashMovements.push({id:uid(),type:'withdrawal',subtype:'delivery',amount:deliveryFee,timestamp:Date.now(),note:'🚗 Доставка'});}
  criticalOperationBusy=true;
  try{await commitCriticalStorage('payment',{products:nextProducts,orders:nextOrders,shifts:nextShifts,currentOrderSession:emptyCurrentOrderSession()})}
  catch(e){flash('Оплата не завершена: '+(e?.message||e));return}
  finally{criticalOperationBusy=false}
  state.products=nextProducts;state.orders=nextOrders;state.shifts=nextShifts;
  void publishAvailability(order.webOrderId?[order.webOrderId]:[]);
  resetCurrentOrderState();state.paymentPage='receipt';state._splitPayments=[];state._splitCount=0;state._splitPaymentTotalCents=null;
  closeModal();showPaymentReceipt(order);void publishPaidOrderLoyalty(order);setTimeout(()=>{if(typeof window.printCompletedOrder==='function')window.printCompletedOrder(order);else if(state.printer?.autoPrint)sendOrderToPrint(order);window.__currentOrderKitchenPrinted=false},250);
}
function openCashModal(){openPaymentModal();}
function openCardModal(){openPaymentModal();}
function confirmPayment(method,total){if(method==='cash'||method==='card')confirmPaymentScreen(method);}

function receiptItemDiscount(i){
  if(!i || !i.discountName) return 0;
  const base=Number(i.price||0)*Number(i.qty||0);
  return i.discountType==='percent' ? base*Math.max(0,Math.min(100,Number(i.discountValue)||0))/100 : Math.min(base,Math.max(0,Number(i.discountValue)||0)*Number(i.qty||0));
}
function receiptItemTotal(i){ return Math.max(0,Number(i.price||0)*Number(i.qty||0)-receiptItemDiscount(i)); }
function receiptProductDiscountTotal(order){return Math.round((Number.isFinite(Number(order?.productDiscountTotal))?Number(order.productDiscountTotal):(order?.items||[]).reduce((sum,item)=>sum+receiptItemDiscount(item),0))*100)/100}
function receiptLoyaltyPrograms(order){return Array.isArray(order?.loyaltyProgramsApplied)?order.loyaltyProgramsApplied.filter(program=>Number(program?.discount)>0):[]}
function receiptBodyHtml(order){
  const lines = order.items.map(i=>{
    const discount=receiptItemDiscount(i), total=receiptItemTotal(i);
    return `
    <div class="receipt-line"><span>${escapeHtml(i.name)} × ${i.qty}</span><span>${money(total)}</span></div>
    ${discount>0 ? `<div class="receipt-line payment-line-discount"><span>↳ Скидка: ${escapeHtml(i.discountName)}${i.discountType==='percent'?` (${Number(i.discountValue)}%)`:''}</span><span>−${money(discount)}</span></div>` : ''}
    ${(i.selectedModifiers||[]).length?`<div class="receipt-line-detail">↳ ${(i.selectedModifiers||[]).map(m=>escapeHtml(m.name)+(Number(m.priceDelta)?' ('+(Number(m.priceDelta)>0?'+':'')+money(m.priceDelta)+')':'')).join(' · ')}</div>`:''}
    ${i.comment ? `<div class="receipt-line-detail"><strong>Комментарий:</strong> ${escapeHtml(i.comment)}</div>` : ''}`;
  }).join('');
  return `
    ${order.returnedAt ? `<div class="receipt-return-banner">Возврат · ${fmtDate(order.returnedAt)}</div>` : ''}
    ${order.orderLabel ? `<div class="receipt-order-label">${escapeHtml(order.orderLabel)}</div>` : ''}
    <div class="receipt-order-meta">${fmtDate(order.timestamp)} · ${order.method==='cash'?'Наличные':order.method==='card'?'Карта':'Наличные + карта'} · ${escapeHtml(order.orderType||'На месте')}</div>
    ${order.customer && (order.customer.name||order.customer.phone||order.customer.address) ? `<div class="receipt-customer">${escapeHtml(order.customer.name||'')} ${escapeHtml(order.customer.phone||'')} ${escapeHtml(order.customer.address||'')}</div>`:''}
    ${lines}
    ${receiptProductDiscountTotal(order)>0?`<div class="receipt-line receipt-discount-summary"><span>Скидки на товары</span><span>−${money(receiptProductDiscountTotal(order))}</span></div>`:''}
    ${receiptLoyaltyPrograms(order).map(program=>`<div class="receipt-line receipt-loyalty-summary"><span>Лояльность · ${escapeHtml(program.name||'Программа')}</span><span>−${money(program.discount)}</span></div>`).join('')}
    ${Number(order.deliveryFee||0)>0 ? `<div class="receipt-line"><span>Доставка</span><span>${fullMoney(order.deliveryFee)}</span></div>` : ''}
    ${(Array.isArray(order.payments) && order.payments.length) ? `
      <div class="receipt-payment-heading">Платежи</div>
      ${order.payments.map((p,i)=>`<div class="receipt-line"><span>Платёж ${i+1} · ${p.method==='cash'?'Наличные':'Карта'}</span><span>${fullMoney(p.amount)}</span></div>`).join('')}
      ${order.payments.filter(p=>p.method==='cash' && p.cashGiven!=null).map(p=>`<div class="receipt-line receipt-line-compact"><span>Наличные · внесено</span><span>${fullMoney(p.cashGiven)}</span></div><div class="receipt-line receipt-line-compact"><span>Сдача</span><span>${fullMoney(p.change||0)}</span></div>`).join('')}
    ` : order.method==='cash' ? `
      <div class="receipt-payment-heading">Платежи</div>
      <div class="receipt-line"><span>Наличные</span><span>${fullMoney(order.total)}</span></div>
      <div class="receipt-line"><span>Внесено</span><span>${fullMoney(order.cashGiven)}</span></div>
      <div class="receipt-line"><span>Сдача</span><span>${fullMoney(order.change)}</span></div>
    ` : `
      <div class="receipt-payment-heading">Платежи</div>
      <div class="receipt-line"><span>Карта</span><span>${fullMoney(order.total)}</span></div>
    `}
    <div class="receipt-total"><span>Итого</span><span>${fullMoney(order.total)}</span></div>
  `;
}
async function sendOrderToPrint(order){
  const p=state.printer||{};
  if(!p.enabled || !p.ip) return;
  const handler=window.webkit?.messageHandlers?.printer;
  if(!handler){ flash('Сетевая печать доступна только в приложении на iPad'); return; }
  handler.postMessage({action:'print',order:{...order,currency:order?.currency||state.currency,__networkPrinterIp:p.ip,__networkPrinterPort:Number(p.port)||9100}});
}

function finishPaymentFlow(){
  state.paymentPage='';
  document.getElementById('payment-page-root')?.remove();
  closeModal();
  render();
}
function printPaymentReceipt(orderId){
  printReceipt(orderId);
  finishPaymentFlow();
}
function showPaymentReceipt(order){
  showModal(`
    <div class="modal-title">Чек оплачен</div>
    <div id="receipt-body">${receiptBodyHtml(order)}</div>
    <div class="receipt-modal-actions receipt-payment-complete-actions">
      <button class="btn receipt-action receipt-action-print receipt-detail-action" onclick="printPaymentReceipt('${escapeAttr(order.id)}')">Печать</button>
      <button class="btn btn-cash receipt-action receipt-detail-action" onclick="finishPaymentFlow()">Готово</button>
    </div>
  `);
}
