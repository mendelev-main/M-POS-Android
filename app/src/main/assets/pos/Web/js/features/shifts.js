function currentShift(){
  return state.shifts.find(s=>s.status==='open') || null;
}
function currentShiftEmployeeIsAdmin(){
  const shift=currentShift();
  if(!shift) return false;
  return state.employees.find(e=>e.id===shift.employeeId)?.role==='admin';
}
function shiftOrders(shiftId){
  return state.orders.filter(o=>o.shiftId===shiftId);
}
function shiftTotals(shiftId){
  const orders = shiftOrders(shiftId);
  const activeOrders = orders.filter(o=>!o.returnedAt);
  const cash = activeOrders.reduce((s,o)=>{
    if(Array.isArray(o.payments)) return s + o.payments.filter(p=>p.method==='cash').reduce((a,p)=>a+Number(p.amount||0),0);
    return s + (o.method==='cash'?Number(o.total||0):0);
  },0);
  const card = activeOrders.reduce((s,o)=>{
    if(Array.isArray(o.payments)) return s + o.payments.filter(p=>p.method==='card').reduce((a,p)=>a+Number(p.amount||0),0);
    return s + (o.method==='card'?Number(o.total||0):0);
  },0);
  const shift=state.shifts.find(s=>s.id===shiftId);
  const movements=Array.isArray(shift?.cashMovements)?shift.cashMovements:[];
  const deposits=movements.filter(m=>m.type==='deposit').reduce((s,m)=>s+Number(m.amount||0),0);
  const withdrawals=movements.filter(m=>m.type==='withdrawal').reduce((s,m)=>s+Number(m.amount||0),0);
  const refundCashMovements=movements.filter(m=>m.type==='withdrawal'&&m.subtype==='refund').reduce((s,m)=>s+Number(m.amount||0),0);
  const refunds=orders.filter(o=>o.returnedAt).reduce((s,o)=>s+Number(o.returnAmount||o.total||0),0);
  return {cash, card, count: activeOrders.length, total: cash+card, deposits, withdrawals, refunds, refundCashMovements, netMovements: deposits-withdrawals, movements, orders, activeOrders};
}
function cashDrawerBalance(shift,totals=shiftTotals(shift.id)){
  return Number(shift.openingCash||0)+Number(totals.cash||0)+Number(totals.netMovements||0)+Number(totals.refundCashMovements||0);
}
function sendTelegramShiftOpened(shift){
  const t=telegramConfigFromState();
  if(!t.enabled || !t.notifyShiftOpened || !t.botToken || !t.chatId) return;
  const date=new Date(shift.openedAt||Date.now()).toLocaleString('ru-RU',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'});
  const text=`🟢 <b>Смена открыта</b>\n👤 Сотрудник: ${escapeHtml(shift.employeeName||'Сотрудник')}\n🕐 Время: ${date}`;
  if(window.webkit?.messageHandlers?.telegram){
    window.webkit.messageHandlers.telegram.postMessage({...t,action:'send',text});
  }
}
function buildShiftReportPayload(shift){
  const orders=shiftOrders(shift.id).slice().sort((a,b)=>(a.timestamp||0)-(b.timestamp||0));
  const t=shiftTotals(shift.id);
  const expected=cashDrawerBalance(shift,t);
  const counted=Number(shift.countedCash)||0;
  return {
    id:shift.id, employeeName:shift.employeeName||'', employeePhone:shift.employeePhone||'',
    openedAt:shift.openedAt||0, closedAt:shift.closedAt||0,
    openingCash:Number(shift.openingCash)||0, countedCash:counted, expectedCash:expected, difference:counted-expected,
    cash:Number(t.cash)||0, card:Number(t.card)||0, total:Number(t.total)||0, count:Number(t.count)||0,
    deposits:Number(t.deposits)||0, withdrawals:Number(t.withdrawals)||0, netMovements:Number(t.netMovements)||0,
    currency:state.currency,
    establishmentName:state.company?.establishmentName || '',
    cashMovements:t.movements.map(m=>({type:m.type||'',subtype:m.subtype||'',amount:Number(m.amount)||0,timestamp:m.timestamp||0,note:m.note||''})),
    orders:orders.map(o=>({timestamp:o.timestamp||0,total:Number(o.total)||0,method:o.method||'',orderLabel:o.orderLabel||'',orderType:o.orderType||'',items:(o.items||[]).map(i=>({name:i.name||i.productName||'Товар',qty:Number(i.qty)||0,price:Number(i.price)||0}))}))
  };
}
function sendTelegramShiftClosed(shift){
  const t=telegramConfigFromState();
  if(!t.enabled || !t.notifyShiftClosed || !t.botToken || !t.chatId) return;
  if(window.webkit?.messageHandlers?.telegram){
    window.webkit.messageHandlers.telegram.postMessage({...t,action:'sendShiftCloseReport',report:buildShiftReportPayload(shift)});
  }
}
/* ============================= SHIFT SCREEN ============================= */
function renderShiftScreen(shift){
  const history = state.shifts.filter(s=>s.status==='closed').slice().sort((a,b)=>b.closedAt-a.closedAt).slice(0,20);
  let openBlock;
  if(shift){
    const t = shiftTotals(shift.id);
    const allShiftOrders=shiftOrders(shift.id);
    const shiftNumber=Math.max(1,state.shifts.findIndex(s=>s.id===shift.id)+1);
    const grossSales=allShiftOrders.reduce((sum,o)=>sum+Number(o.total||0),0);
    const returnsTotal=allShiftOrders.filter(o=>o.returnedAt).reduce((sum,o)=>sum+Number(o.returnAmount||o.total||0),0);
    const discountsTotal=allShiftOrders.reduce((sum,o)=>sum+(o.items||[]).reduce((itemSum,item)=>itemSum+receiptItemDiscount(item),0),0);
    const cashRefunds=allShiftOrders.filter(o=>o.returnedAt).reduce((sum,o)=>{
      const refund=Number(o.returnAmount||o.total||0);
      const total=Number(o.total||0);
      if(Array.isArray(o.payments) && o.payments.length && total>0){
        const cashPaid=o.payments.filter(p=>p.method==='cash').reduce((a,p)=>a+Number(p.amount||0),0);
        return sum+refund*(cashPaid/total);
      }
      return sum+(o.method==='cash'?refund:0);
    },0);
    const expected = cashDrawerBalance(shift,t);
    const netRevenue=Math.max(0,grossSales-returnsTotal);
    openBlock = `
      <div class="card shift-summary-card">
        <div class="shift-summary-actions">
          <button class="btn btn-success shift-summary-action" onclick="openCashMovementModal('deposit')">Внести наличные</button>
          <button class="btn btn-danger shift-summary-action" onclick="openCashMovementModal('withdrawal')">Изъять наличные</button>
        </div>

        <div class="shift-summary-header">
          <div>
            <div class="shift-summary-number">Кассовая смена №${shiftNumber}</div>
            <div class="shift-summary-meta">Смена открыта: ${fmtDate(shift.openedAt)} · ${escapeHtml(shift.employeeName||'Сотрудник не указан')}</div>
          </div>
        </div>

        <div class="shift-summary-grid">
          <div class="shift-summary-section cash">
            <div class="shift-summary-title">Наличные в кассе</div>
            <div class="shift-summary-row"><span>Наличные на начало смены</span><strong>${money(shift.openingCash)}</strong></div>
            <div class="shift-summary-row"><span>Оплата наличными</span><strong>${money(t.cash+cashRefunds)}</strong></div>
            <div class="shift-summary-row"><span>Возвраты наличными</span><strong>${money(cashRefunds)}</strong></div>
            <div class="shift-summary-row"><span>Сумма внесений</span><strong>${money(t.deposits)}</strong></div>
            <div class="shift-summary-row"><span>Сумма изъятий</span><strong>${money(t.withdrawals)}</strong></div>
            <div class="shift-summary-row total"><span>Ожидаемая сумма наличных</span><strong>${money(expected)}</strong></div>
          </div>

          <div class="shift-summary-section">
            <div class="shift-summary-title">Итоги продаж</div>
            <div class="shift-summary-row"><span>Продажи</span><strong>${money(grossSales)}</strong></div>
            <div class="shift-summary-row"><span>Возвраты</span><strong>${money(returnsTotal)}</strong></div>
            <div class="shift-summary-row"><span>Скидки</span><strong>${money(discountsTotal)}</strong></div>
          </div>

          <div class="shift-summary-section">
            <div class="shift-summary-title">Выручка</div>
            <div class="shift-summary-row total shift-revenue-total"><span>Выручка</span><strong>${money(netRevenue)}</strong></div>
            <div class="shift-summary-row"><span>Наличные</span><strong>${money(t.cash)}</strong></div>
            <div class="shift-summary-row"><span>Карта</span><strong>${money(t.card)}</strong></div>
          </div>
        </div>

        ${t.movements.length ? `<div class="shift-movements-card"><div class="shift-movements-title">Движение средств</div>${t.movements.slice().reverse().map(m=>`<div class="list-row shift-movement-row"><div class="shift-row-main"><div class="list-row-name">${m.type==='deposit'?'Внесение наличных':(m.subtype==='delivery'||m.note==='🚗 Доставка'?'Доставка':(m.subtype==='refund'||m.note==='↩️ Возврат чека'?'Возврат чека':'Изъятие наличных'))}</div><div class="list-row-sub">${fmtDate(m.timestamp)}${m.note?' · '+escapeHtml(m.note):''}</div></div><div class="badge">${m.type==='deposit'?'+':'−'}${money(m.amount)}</div></div>`).join('')}</div>` : ''}
        <div class="shift-summary-footer">
          <button class="btn btn-danger-outline shift-close-button" onclick="openCloseShiftModal()">Закрыть смену</button>
        </div>
      </div>`;  } else {
    openBlock = `
      <div class="card shift-closed-card">
        <div class="shift-closed-title">Смена закрыта</div>
        <div class="center-note shift-closed-note">Откройте смену, чтобы начать принимать заказы.</div>
        <button class="btn btn-primary shift-open-button" onclick="openShiftModal()">Открыть смену</button>
      </div>`;
  }
  return `
  <div class="screen content-screen ${state.tab==='shift'?'active':''}">
    <div class="content-head"><div class="content-title">Кассовая смена</div></div>
    ${openBlock}
    <div class="content-title shift-history-title">История смен</div>
    <div class="card">
      ${history.length ? history.map(s=>{
        const t = shiftTotals(s.id);
        const diff = (s.countedCash||0) - cashDrawerBalance(s,t);
        return `
        <div class="list-row shift-history-row" onclick="viewShiftModal('${escapeAttr(s.id)}')">
          <div class="shift-row-main">
            <div class="list-row-name">${fmtDate(s.openedAt)} — ${fmtDate(s.closedAt)}</div>
            <div class="list-row-sub">${escapeHtml(s.employeeName||'Сотрудник не указан')} · Заказов: ${t.count} · Наличные ${money(t.cash)} · Карта ${money(t.card)}</div>
          </div>
          <div class="badge shift-difference-badge ${Math.abs(diff)<0.01?'is-balanced':'has-difference'}">Расхожд.: ${money(diff)}</div>
        </div>`;
      }).join('') : `<div class="center-note">Ещё нет закрытых смен</div>`}
    </div>
  </div>`;
}
function openShiftModal(){
  const employees = state.employees.slice().sort((a,b)=>a.name.localeCompare(b.name,'ru'));
  const previousClosed = state.shifts.filter(s=>s.status==='closed').slice().sort((a,b)=>(b.closedAt||0)-(a.closedAt||0))[0] || null;
  const previousCash = previousClosed ? Number(previousClosed.countedCash||0) : 0;
  showModal(`
    <div class="modal-title">Открыть смену</div>
    <div class="field"><label>Сотрудник</label>
      ${employees.length ? `<select id="sf-employee" onchange="toggleShiftAdminPassword()"><option value="">Выберите сотрудника</option>${employees.map(e=>`<option value="${escapeAttr(e.id)}">${escapeHtml(employeeShortName(e.name))}${e.role==='admin'?' · АДМИНИСТРАТОР':''}</option>`).join('')}</select>` : `<div class="settings-note">Сначала добавьте сотрудников в Настройки → Сотрудники.</div>`}
    </div>
    <div id="sf-admin-password-wrap" class="field shift-admin-password" hidden>
      <label>Пароль администратора</label>
      <input id="sf-admin-password" type="password" autocomplete="off" placeholder="Введите пароль">
      <div class="settings-note shift-admin-note">Для открытия смены администратором требуется пароль.</div>
    </div>
    <div class="field">
      <label>Наличные при открытии смены</label>
      <div class="shift-opening-cash">${fullMoney(previousCash)}</div>
      <div class="settings-note shift-opening-note">Проверьте Наличные в кассе. Сумма перенесена с прошлой смены</div>
    </div>
    <div class="modal-actions">
      <button class="btn btn-secondary" onclick="closeModal()">Отмена</button>
      <button class="btn btn-primary" onclick="submitOpenShift()" ${employees.length?'':'disabled'}>Открыть</button>
    </div>
  `);
}
function toggleShiftAdminPassword(){
  const select=document.getElementById('sf-employee');
  const wrap=document.getElementById('sf-admin-password-wrap');
  const password=document.getElementById('sf-admin-password');
  if(!select || !wrap) return;
  const employee=state.employees.find(e=>e.id===select.value);
  const isAdmin=employee?.role==='admin';
  wrap.hidden=!isAdmin;
  if(!isAdmin && password) password.value='';
  if(isAdmin) setTimeout(()=>password?.focus(),50);
}
async function submitOpenShift(){
  if(criticalOperationBusy){flash('Сохранение операции ещё не завершено');return false;}
  if(currentShift()){flash('Смена уже открыта');return false;}
  const employeeId=document.getElementById('sf-employee')?.value||'';
  const employee=state.employees.find(e=>e.id===employeeId);
  if(!employee){flash('Выберите сотрудника');return false;}
  if(employee.role==='admin'){
    const password=document.getElementById('sf-admin-password')?.value||'';
    if(password!=='Rom23061998'){flash('Для открытия смены администратором введите верный пароль');return false;}
  }
  const previousClosed = state.shifts.filter(s=>s.status==='closed').slice().sort((a,b)=>(b.closedAt||0)-(a.closedAt||0))[0] || null;
  const openingCash = previousClosed ? Number(previousClosed.countedCash||0) : 0;
  if(!Number.isFinite(openingCash)||openingCash<0){flash('Некорректный остаток предыдущей смены');return false;}
  const newShift={id:uid(), status:'open', openedAt:Date.now(), openingCash, employeeId:employee.id, employeeName:employee.name, employeePhone:employee.phone||'', openingSourceShiftId:previousClosed?.id||''};
  const nextShifts=storageSnapshot(state.shifts);nextShifts.push(newShift);
  criticalOperationBusy=true;
  try{await commitCriticalStorage('open-shift',{shifts:nextShifts})}
  catch(e){flash('Смена не открыта: '+(e?.message||e));return false}
  finally{criticalOperationBusy=false}
  state.shifts=nextShifts;
  closeModal();
  render();
  try{sendTelegramShiftOpened(newShift);maybeSendMonthlyWarehouseReport()}
  catch(e){console.error('Смена открыта, но внешний отчёт не отправлен:',e)}
  return true;
}

function openCashMovementModal(type){
  const shift=currentShift();
  if(!shift){flash('Смена не открыта');return;}
  const isDeposit=type==='deposit';
  showModal(`
    <div class="modal-title">${isDeposit?'Внести наличные':'Изъять наличные'}</div>
    <div class="field"><label>Сумма</label><input type="number" id="cash-movement-amount" inputmode="decimal" min="0.01" step="0.01" placeholder="0,00"></div>
    <div class="field"><label>Комментарий</label><input id="cash-movement-note" placeholder="Необязательно"></div>
    <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button class="btn btn-primary" onclick="submitCashMovement('${type}')">${isDeposit?'Внести':'Изъять'}</button></div>
  `);
  setTimeout(()=>document.getElementById('cash-movement-amount')?.focus(),50);
}
async function submitCashMovement(type){
  if(!['deposit','withdrawal'].includes(type)){flash('Некорректная операция с наличными');return false;}
  if(criticalOperationBusy){flash('Сохранение операции ещё не завершено');return false;}
  const shift=currentShift();
  if(!shift){flash('Смена не открыта');closeModal();return false;}
  const amount=Number(String(document.getElementById('cash-movement-amount')?.value||'').replace(',','.'));
  if(!Number.isFinite(amount)||amount<=0){flash('Введите корректную сумму');return false;}
  const t=shiftTotals(shift.id),available=cashDrawerBalance(shift,t);
  if(!Number.isFinite(available)||available<0){flash('Некорректные данные кассовой смены');return false;}
  if(type==='withdrawal'){
    if(amount>available+0.0001){flash('Недостаточно наличных в кассе');return false;}
  }
  const nextShifts=storageSnapshot(state.shifts),nextShift=nextShifts.find(item=>item.id===shift.id);
  if(!Array.isArray(nextShift.cashMovements))nextShift.cashMovements=[];
  nextShift.cashMovements.push({id:uid(),type,amount,timestamp:Date.now(),note:(document.getElementById('cash-movement-note')?.value||'').trim()});
  criticalOperationBusy=true;
  try{await commitCriticalStorage('cash-movement',{shifts:nextShifts})}
  catch(e){flash('Операция с наличными не сохранена: '+(e?.message||e));return false}
  finally{criticalOperationBusy=false}
  state.shifts=nextShifts;
  closeModal();render();
  flash(type==='deposit'?'Наличные внесены в кассу':'Наличные изъяты из кассы');
  return true;
}

function openCloseShiftModal(){
  const shift = currentShift();
  if(!shift){flash('Смена не открыта');return;}
  const t = shiftTotals(shift.id);
  const expected = cashDrawerBalance(shift,t);
  if(!Number.isFinite(expected)||expected<0){flash('Некорректные данные кассовой смены');return;}
  showModal(`
    <div class="modal-title">Закрыть смену</div>
    <div class="field"><label>Ожидается в кассе (наличные)</label>
      <div class="shift-expected-cash">${fullMoney(expected)}</div>
    </div>
    <div class="field"><label>Фактически пересчитано</label>
      <input type="number" id="sf-counted" inputmode="decimal" min="0" step="0.01" value="${expected}">
    </div>
    <div class="modal-actions">
      <button class="btn btn-secondary" onclick="closeModal()">Отмена</button>
      <button class="btn btn-primary" onclick="submitCloseShift()">Закрыть смену</button>
    </div>
  `);
}
async function submitCloseShift(){
  if(criticalOperationBusy){flash('Сохранение операции ещё не завершено');return false;}
  const shift = currentShift();
  if(!shift){flash('Смена не открыта');closeModal();return false;}
  const expected=cashDrawerBalance(shift);
  if(!Number.isFinite(expected)||expected<0){flash('Некорректные данные кассовой смены');return false;}
  const raw=String(document.getElementById('sf-counted')?.value||'').trim();
  const countedCash=raw===''?NaN:Number(raw.replace(',','.'));
  if(!Number.isFinite(countedCash)||countedCash<0){flash('Введите корректную сумму в кассе');return false;}
  const nextShifts=storageSnapshot(state.shifts),closedShift=nextShifts.find(item=>item.id===shift.id);
  closedShift.status='closed';closedShift.closedAt=Date.now();closedShift.countedCash=countedCash;
  criticalOperationBusy=true;
  try{await commitCriticalStorage('close-shift',{shifts:nextShifts})}
  catch(e){flash('Смена не закрыта: '+(e?.message||e));return false}
  finally{criticalOperationBusy=false}
  state.shifts=nextShifts;
  closeModal();
  render();
  try{sendTelegramShiftClosed(closedShift)}catch(e){console.error('Смена закрыта, но Telegram-отчёт не отправлен:',e)}
  // После успешного сохранения закрытой смены отправляем итоговый отчёт прямо на настроенный чековый принтер.
  if(typeof window.printShiftCloseReceipt==='function'){
    try{window.printShiftCloseReceipt(buildShiftReportPayload(closedShift))}
    catch(e){flash('Смена закрыта, но не удалось отправить отчёт на чековый принтер')}
  }
  else flash('Не удалось отправить отчёт смены на чековый принтер');
  return true;
}
function viewShiftModal(shiftId){
  const shift=state.shifts.find(s=>s.id===shiftId); if(!shift) return;
  const orders=shiftOrders(shift.id).slice().sort((a,b)=>(a.timestamp||0)-(b.timestamp||0));
  const t=shiftTotals(shift.id);
  const expected=cashDrawerBalance(shift,t);
  const counted=Number(shift.countedCash)||0;
  const diff=counted-expected;
  const report=buildShiftReportPayload(shift);
  showModal(`
    <div class="modal-title">Отчёт по смене</div>
    <div class="list-row-sub shift-report-meta">${fmtDate(shift.openedAt)} — ${fmtDate(shift.closedAt)} · ${escapeHtml(shift.employeeName||'Сотрудник не указан')}</div>
    <div class="grid-3">
      <div class="stat-box"><div class="label">Заказов</div><div class="value">${t.count}</div></div>
      <div class="stat-box"><div class="label">Выручка</div><div class="value">${money(t.total)}</div></div>
      <div class="stat-box"><div class="label">Наличные</div><div class="value">${money(t.cash)}</div></div>
    </div>
    <div class="grid-2 shift-report-grid-spaced">
      <div class="stat-box"><div class="label">Карта</div><div class="value">${money(t.card)}</div></div>
      <div class="stat-box"><div class="label">Расхождение</div><div class="value shift-difference-value ${Math.abs(diff)<0.01?'is-balanced':'has-difference'}">${money(diff)}</div></div>
    </div>
    <div class="grid-2 shift-report-grid-spaced">
      <div class="stat-box"><div class="label">Внесено наличных</div><div class="value">${money(t.deposits)}</div></div>
      <div class="stat-box"><div class="label">Изъято наличных</div><div class="value">${money(t.withdrawals)}</div></div>
    </div>
    <div class="shift-report-section-title">Движение наличных</div>
    <div class="shift-report-movements">
      ${t.movements.length ? t.movements.slice().sort((a,b)=>(a.timestamp||0)-(b.timestamp||0)).map(m=>`<div class="list-row shift-report-movement-row"><div class="shift-row-main"><div class="list-row-name">${m.type==='deposit'?'Внесение наличных':(m.subtype==='delivery'||m.note==='🚗 Доставка'?'Доставка':(m.subtype==='refund'||m.note==='↩️ Возврат чека'?'Возврат чека':'Изъятие наличных'))}</div><div class="list-row-sub">${fmtDate(m.timestamp)}${m.note?' · '+escapeHtml(m.note):''}</div></div><div class="badge">${m.type==='deposit'?'+':'−'}${money(m.amount)}</div></div>`).join('') : '<div class="center-note">Движения наличных не было</div>'}
    </div>
    <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Закрыть</button><button class="btn btn-primary" onclick="printShiftReport('${escapeAttr(shift.id)}')">Распечатать отчёт</button></div>
  `, true);
}
function printShiftReport(shiftId){
  const shift=state.shifts.find(s=>s.id===shiftId); if(!shift) return;
  const payload=buildShiftReportPayload(shift);
  if(window.webkit?.messageHandlers?.printer){window.webkit.messageHandlers.printer.postMessage({action:'printShiftReport',report:payload});return;}
  window.print();
}
