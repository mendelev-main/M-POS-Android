(function(global){
  'use strict';
  function paid(order,method){
    return Array.isArray(order.payments)?order.payments.filter(p=>p.method===method).reduce((sum,p)=>sum+Number(p.amount||0),0):(order.method===method?Number(order.total||0):0);
  }
  global.shiftTotals=function(shiftId){
    const all=state.orders,sales=all.filter(o=>o.shiftId===shiftId);
    const returned=all.filter(o=>o.returnedAt&&(o.returnedShiftId||o.shiftId)===shiftId);
    const shift=state.shifts.find(s=>s.id===shiftId),movements=Array.isArray(shift?.cashMovements)?shift.cashMovements:[];
    const refundMoves=movements.filter(m=>m.type==='withdrawal'&&m.subtype==='refund'),used=new Set();
    let cashRefunds=0,cardRefunds=0,refunds=0,matched=0;
    for(const order of returned){
      const total=Number(order.total||0),refund=Number(order.returnAmount||order.total||0),factor=total>0?refund/total:1;
      const cash=paid(order,'cash')*factor;cashRefunds+=cash;cardRefunds+=paid(order,'card')*factor;refunds+=refund;
      const i=refundMoves.findIndex((m,index)=>!used.has(index)&&Number(m.timestamp)===Number(order.returnedAt)&&Math.abs(Number(m.amount)-cash)<=0.001);
      if(i>=0){used.add(i);matched+=Number(refundMoves[i].amount)}
    }
    const cash=sales.reduce((sum,o)=>sum+paid(o,'cash'),0)-cashRefunds,card=sales.reduce((sum,o)=>sum+paid(o,'card'),0)-cardRefunds;
    const deposits=movements.filter(m=>m.type==='deposit').reduce((sum,m)=>sum+Number(m.amount||0),0);
    const withdrawals=movements.filter(m=>m.type==='withdrawal').reduce((sum,m)=>sum+Number(m.amount||0),0);
    const activeOrders=sales.filter(o=>!o.returnedAt||(o.returnedShiftId||o.shiftId)!==shiftId);
    return {cash,card,count:activeOrders.length,total:cash+card,refunds,cashRefunds,refundCashMovements:matched,deposits,withdrawals,netMovements:deposits-withdrawals,movements,orders:sales,activeOrders};
  };
function MPosRenderShiftScreen(shift){
  const history = state.shifts.filter(s=>s.status==='closed').slice().sort((a,b)=>b.closedAt-a.closedAt).slice(0,20);
  let openBlock;
  if(shift){
    const t = shiftTotals(shift.id);
    const allShiftOrders=shiftOrders(shift.id);
    const shiftNumber=Math.max(1,state.shifts.findIndex(s=>s.id===shift.id)+1);
    const grossSales=allShiftOrders.reduce((sum,o)=>sum+Number(o.total||0),0);
    const returnsTotal=t.refunds;
    const discountsTotal=allShiftOrders.reduce((sum,o)=>sum+(o.items||[]).reduce((itemSum,item)=>itemSum+receiptItemDiscount(item),0),0);
    const cashRefunds=t.cashRefunds;
    const expected = cashDrawerBalance(shift,t);
    const netRevenue=grossSales-returnsTotal;
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
  global.renderShiftScreen=MPosRenderShiftScreen;
})(window);
