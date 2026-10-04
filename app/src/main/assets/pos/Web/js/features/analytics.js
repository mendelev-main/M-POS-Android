/* Analytics runtime. Compatibility globals are intentional during gradual migration. */
function analyticsRange(){
  const now=new Date();
  const todayStart=new Date(now.getFullYear(),now.getMonth(),now.getDate(),0,0,0,0);
  const from=state.analyticsFrom ? new Date(state.analyticsFrom+'T00:00:00') : todayStart;
  const to=state.analyticsTo ? new Date(state.analyticsTo+'T23:59:59.999') : new Date(now.getFullYear(),now.getMonth(),now.getDate(),23,59,59,999);
  return {from:from.getTime(),to:to.getTime()};
}
function setAnalyticsDate(which,value){
  if(!value) return;
  state.loyaltyAnalyticsKey='';
  if(which==='from') state.analyticsFrom=value;
  if(which==='to') state.analyticsTo=value;
  const from=state.analyticsFrom||value;
  const to=state.analyticsTo||value;
  if(from>to){
    flash('Дата начала не может быть позже даты окончания');
    return;
  }
  render();
}
function setAnalyticsPreset(days){
  state.loyaltyAnalyticsKey='';
  const now=new Date();
  const to=new Date(now.getFullYear(),now.getMonth(),now.getDate());
  const from=new Date(to); from.setDate(from.getDate()-Math.max(0,days-1));
  state.analyticsFrom=localDateString(from);
  state.analyticsTo=localDateString(to);
  render();
}
function analyticsPresetIsActive(days,from,to){
  const now=new Date();
  const end=localDateString(new Date(now.getFullYear(),now.getMonth(),now.getDate()));
  const startDate=new Date(now.getFullYear(),now.getMonth(),now.getDate());
  startDate.setDate(startDate.getDate()-Math.max(0,days-1));
  const start=localDateString(startDate);
  return from===start && to===end;
}
function openAnalyticsPeriodModal(){
  const today=localDateString(new Date());
  const from=state.analyticsFrom||today;
  const to=state.analyticsTo||today;
  showModal(`
    <div class="modal-title">Выбор периода</div>
    <div class="modal-sub">Выберите даты начала и окончания периода аналитики.</div>
    <div class="analytics-period-modal-grid">
      <div class="field"><label>Дата начала</label><input id="analytics-period-from" type="date" value="${from}"></div>
      <div class="field"><label>Дата окончания</label><input id="analytics-period-to" type="date" value="${to}"></div>
    </div>
    <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button class="btn btn-primary" onclick="applyAnalyticsPeriod()">Применить</button></div>
  `, false);
}
function applyAnalyticsPeriod(){
  const from=document.getElementById('analytics-period-from')?.value||'';
  const to=document.getElementById('analytics-period-to')?.value||'';
  if(!from||!to){ flash('Выберите обе даты'); return; }
  if(from>to){ flash('Дата начала не может быть позже даты окончания'); return; }
  state.analyticsFrom=from;
  state.analyticsTo=to;
  closeModal();
  render();
}
function analyticsOrders(){
  const r=analyticsRange();
  return state.orders.filter(o=>Number(o.timestamp||0)>=r.from && Number(o.timestamp||0)<=r.to);
}
function analyticsData(){
  const orders=analyticsOrders().filter(o=>!o.returnedAt);
  const revenue=orders.reduce((s,o)=>s+Number(o.total||0),0);
  const cash=orders.reduce((s,o)=>{
    if(Array.isArray(o.payments)) return s+o.payments.filter(p=>p.method==='cash').reduce((a,p)=>a+Number(p.amount||0),0);
    return s+(o.method==='cash'?Number(o.total||0):0);
  },0);
  const card=orders.reduce((s,o)=>{
    if(Array.isArray(o.payments)) return s+o.payments.filter(p=>p.method==='card').reduce((a,p)=>a+Number(p.amount||0),0);
    return s+(o.method==='card'?Number(o.total||0):0);
  },0);
  let cost=0;
  const employeeMap={}, categoryMap={}, productMap={};
  for(const o of orders){
    const sh=state.shifts.find(s=>s.id===o.shiftId);
    const employee=sh?.employeeName||'Без сотрудника';
    employeeMap[employee]=(employeeMap[employee]||0)+Number(o.total||0);
    for(const i of (o.items||[])){
      const qty=Number(i.qty)||0, price=Number(i.price)||0;
      const line=price*qty;
      const itemCost=Number(i.cost)||0;
      cost += itemCost*qty;
      const p=getProduct(i.productId);
      const cat=p?.category||i.category||'Без категории';
      const name=i.name||i.productName||p?.name||'Товар';
      if(!categoryMap[cat]) categoryMap[cat]={revenue:0,qty:0};
      categoryMap[cat].revenue+=line; categoryMap[cat].qty+=qty;
      if(!productMap[name]) productMap[name]={revenue:0,qty:0};
      productMap[name].revenue+=line; productMap[name].qty+=qty;
    }
  }
  return {orders,revenue,cash,card,cost,profit:revenue-cost,avg:orders.length?revenue/orders.length:0,
    employees:Object.entries(employeeMap).map(([name,value])=>({name,value})).sort((a,b)=>b.value-a.value),
    categories:Object.entries(categoryMap).map(([name,v])=>({name,...v})).sort((a,b)=>b.qty-a.qty),
    products:Object.entries(productMap).map(([name,v])=>({name,...v})).sort((a,b)=>b.qty-a.qty)};
}
function renderHbars(items, showValues=true){
  if(!items.length) return '<div class="center-note">Нет продаж за выбранный период.</div>';
  const max=Math.max(...items.map(x=>x.value),1);
  return `<div class="hbar-list">${items.map(x=>`<div class="hbar-row"><div class="hbar-name" title="${escapeAttr(x.name)}">${escapeHtml(x.name)}</div><div class="hbar-track"><div class="hbar-fill" style="width:${Math.max(2,x.value/max*100)}%"></div></div>${showValues?`<div class="hbar-value">${money(x.value)}</div>`:'<div class="hbar-value hbar-value-hidden">0</div>'}</div>`).join('')}</div>`;
}
function renderAnalyticsBars(items,{format='money',percentTotal=0,percentOnly=false}={}){
  if(!items.length) return '<div class="center-note">Нет продаж за выбранный период.</div>';
  const max=Math.max(...items.map(x=>Number(x.value)||0),1);
  const listClass=percentTotal>0?'hbar-list payment-method-bars':'hbar-list';
  return `<div class="${listClass}">${items.map(x=>{const value=Number(x.value)||0,percentValue=percentTotal>0?(value/percentTotal*100).toLocaleString('ru-RU',{maximumFractionDigits:1})+'%':'0%',label=percentOnly?percentValue:(format==='qty'?stockQtyText(value)+' шт':money(value)+(percentTotal>0?' · '+percentValue:''));return `<div class="hbar-row"><div class="hbar-name" title="${escapeAttr(x.name)}">${escapeHtml(x.name)}</div><div class="hbar-track"><div class="hbar-fill" style="width:${Math.max(2,value/max*100)}%"></div></div><div class="hbar-value">${label}</div></div>`;}).join('')}</div>`;
}
function unlockAnalyticsSensitive(){
  state.analyticsSensitiveUnlocked=true;
  render();
}
function checkAnalyticsPassword(){
  state.analyticsSensitiveUnlocked=true;
  closeModal();
  render();
}
function inventoryCostValue(){
  return state.products.reduce((sum,p)=>{
    if(p.type!=='simple' || p.noStockTracking) return sum;
    const stock=Math.max(0,Number(p.stock)||0);
    const cost=Math.max(0,Number(p.cost)||0);
    return sum + stock*cost;
  },0);
}

async function loadLoyaltyAnalytics(from,to){const key=from+'|'+to;if(state.loyaltyAnalyticsKey===key)return;state.loyaltyAnalyticsKey=key;try{state.loyaltyAnalytics=await loyaltyApi('/api/analytics/loyalty?from='+encodeURIComponent(from)+'&to='+encodeURIComponent(to));if(state.tab==='analytics')render()}catch(e){state.loyaltyAnalytics={error:true};if(state.tab==='analytics')render()}}
function renderAnalyticsScreen(){
  const d=analyticsData();
  const today=localDateString(new Date());
  const from=state.analyticsFrom||today;
  const to=state.analyticsTo||today;
  const topProducts=d.products.slice(0,10);
  const admin=currentShiftEmployeeIsAdmin();
  const paymentTotal=d.cash+d.card;
  if(admin) loadLoyaltyAnalytics(from,to);
  const la=state.loyaltyAnalytics;
  const loyaltyKpis=admin?`<div class="analytics-card analytics-section-gap"><div class="analytics-title">Клиенты и лояльность</div><div class="analytics-sub">Центральные данные POS + онлайн-заказы за выбранный период</div>${la?.error?'<div class="center-note">Нет связи с сервером. Продажи POS продолжают работать офлайн.</div>':!la||state.loyaltyAnalyticsKey!==(from+'|'+to)?'<div class="center-note">Загрузка…</div>':`<div class="analytics-kpis analytics-loyalty-kpis"><div class="analytics-kpi"><div class="k-label">Клиентов</div><div class="k-value">${la.customers||0}</div></div><div class="analytics-kpi"><div class="k-label">Новых</div><div class="k-value">${la.newCustomers||0}</div></div><div class="analytics-kpi"><div class="k-label">Повторных</div><div class="k-value">${la.repeatCustomers||0}</div></div><div class="analytics-kpi"><div class="k-label">Средний чек клиента</div><div class="k-value">${money(la.averageCustomerCheck||0)}</div></div><div class="analytics-kpi"><div class="k-label">Активных в лояльности</div><div class="k-value">${la.activeLoyaltyUsers||0}</div></div><div class="analytics-kpi"><div class="k-label">Подарков начислено</div><div class="k-value">${la.rewardsGranted||0}</div></div><div class="analytics-kpi"><div class="k-label">Подарков использовано</div><div class="k-value">${la.rewardsRedeemed||0}</div></div></div>`}</div>`:'';
  const adminKpis=admin?`<div class="analytics-kpis analytics-section-gap">
      <div class="analytics-kpi"><div class="k-label">Выручка</div><div class="k-value">${money(d.revenue)}</div></div>
      <div class="analytics-kpi"><div class="k-label">Заказов</div><div class="k-value">${d.orders.length}</div></div>
      <div class="analytics-kpi"><div class="k-label">Средний чек</div><div class="k-value">${money(d.avg)}</div></div>
      <div class="analytics-kpi"><div class="k-label">Валовая прибыль</div><div class="k-value">${money(d.profit)}</div></div>
      <div class="analytics-kpi"><div class="k-label">Стоимость остатков товаров</div><div class="k-value">${money(inventoryCostValue())}</div></div>
    </div>`:'';
  return `<div class="screen content-screen ${state.tab==='analytics'?'active':''}">
    <div class="card analytics-section-gap">
      <div class="analytics-filters">
        <div class="analytics-period-head"><div><div class="analytics-period-title">Период аналитики</div><div class="analytics-period-sub">Выберите готовый период или задайте даты вручную.</div></div></div>
        <div class="analytics-period-layout">
          <div class="analytics-date-fields">
            <div class="analytics-filter"><label>От</label><input aria-label="Дата начала" type="date" value="${from}" onchange="setAnalyticsDate('from',this.value)"></div>
            <div class="analytics-filter"><label>До</label><input aria-label="Дата окончания" type="date" value="${to}" onchange="setAnalyticsDate('to',this.value)"></div>
          </div>
          <div class="analytics-presets-wrap"><div class="analytics-presets-label">Быстрый период</div><div class="analytics-presets">
            <button class="btn ${analyticsPresetIsActive(1,from,to)?'analytics-preset-active':''}" onclick="setAnalyticsPreset(1)">Сегодня</button>
            <button class="btn ${analyticsPresetIsActive(7,from,to)?'analytics-preset-active':''}" onclick="setAnalyticsPreset(7)">7 дней</button>
            <button class="btn ${analyticsPresetIsActive(30,from,to)?'analytics-preset-active':''}" onclick="setAnalyticsPreset(30)">30 дней</button>
          </div></div>
        </div>
      </div>
    </div>
    ${adminKpis}
    ${loyaltyKpis}
    <div class="analytics-grid-2 analytics-section-gap">
      <div class="analytics-card"><div class="analytics-title">Продажи сотрудников</div><div class="analytics-sub">${admin?'Выручка по сотруднику за выбранный период':'Продажи по сотруднику за выбранный период'}</div>${admin?renderHbars(d.employees,true):renderHbars(d.employees,false)}</div>
      <div class="analytics-card"><div class="analytics-title">Способы оплаты</div><div class="analytics-sub">Распределение оплат</div>${renderAnalyticsBars([{name:'Наличные',value:d.cash},{name:'Карта',value:d.card}],{format:'money',percentTotal:paymentTotal,percentOnly:!admin})}</div>
    </div>
    <div class="analytics-grid-2">
      <div class="analytics-card"><div class="analytics-title">Продажи по категориям</div><div class="analytics-sub">Количество проданных товаров</div>${renderAnalyticsBars(d.categories.map(x=>({name:x.name,value:x.qty})),{format:'qty'})}</div>
      <div class="analytics-card"><div class="analytics-title">Топ товаров</div><div class="analytics-sub">По количеству продаж за выбранный период</div>${renderAnalyticsBars(topProducts.map(x=>({name:x.name,value:x.qty})),{format:'qty'})}</div>
    </div>
  </div>`;
}
