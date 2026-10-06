(function(global){
  'use strict';
  if(!global.MPosCore?.ShiftReports)return;
  const pending=new Map(),originalPrint=global.printShiftCloseReceipt,originalShow=global.showModal,originalClose=global.closeModal;
  let viewGeneration=0;
  if(typeof originalShow==='function')global.showModal=function(...args){viewGeneration++;return originalShow(...args)};
  if(typeof originalClose==='function')global.closeModal=function(...args){viewGeneration++;return originalClose(...args)};
  function presentation(){return {currency:state.currency,establishmentName:state.company?.establishmentName||''}}
  async function model(shiftId,closedOnly=false){
    const context=presentation(),key=JSON.stringify([shiftId,closedOnly,context]);
    let request=pending.get(key);
    if(!request){
      request=global.MPosCore.ShiftReports.read(shiftId,context,closedOnly);
      pending.set(key,request);
      request.then(()=>pending.delete(key),()=>pending.delete(key));
    }
    // Consumers receive separate copies so a formatter cannot mutate another output's snapshot.
    return JSON.parse(JSON.stringify(await request));
  }
  function failure(){global.flash?.('Не удалось получить сохранённый отчёт смены')}
  global.sendTelegramShiftClosed=async function(shift){
    const config=telegramConfigFromState();
    if(!config.enabled||!config.notifyShiftClosed||!config.botToken||!config.chatId)return;
    if(!global.webkit?.messageHandlers?.telegram)return;
    try{
      const {report}=await model(shift.id,true);
      global.webkit.messageHandlers.telegram.postMessage({...config,action:'sendShiftCloseReport',report});
    }catch(_){failure()}
  };
  global.printShiftCloseReceipt=async function(input){
    try{
      const {report}=await model(input?.id,true);
      if(typeof originalPrint!=='function'){global.flash?.('Не удалось отправить отчёт смены на чековый принтер');return false}
      return await originalPrint(report);
    }catch(_){failure();return false}
  };
  global.printShiftReport=async function(shiftId){
    try{
      const {report}=await model(shiftId);
      if(global.webkit?.messageHandlers?.printer){global.webkit.messageHandlers.printer.postMessage({action:'printShiftReport',report});return}
      global.print();
    }catch(_){failure()}
  };
  global.viewShiftModal=async function(shiftId){
    const generation=++viewGeneration;
    if(typeof originalShow!=='function')return;
    originalShow('<div class="modal-title">Отчёт по смене</div><div class="center-note">Загрузка отчёта…</div>',true);
    try{
      const {report}=await model(shiftId);
      if(generation!==viewGeneration)return;
      const shift=report,t={...report,movements:report.cashMovements},diff=report.difference;
      originalShow(`
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
    }catch(_){
      if(generation!==viewGeneration)return;
      global.closeModal?.();failure();
    }
  };
})(window);
