(function(global){
  'use strict';
  const original=global.renderReceiptsScreen;
  if(typeof original!=='function'||!global.MPosCore?.Receipts)return;
  const limit=50;
  let offset=0,rows=null,total=0,generation=0,pending=false,error=false;
  function appState(){return typeof state!=='undefined'?state:global.state}
  function redraw(){if(typeof global.render==='function'&&appState()?.tab==='receipts')global.render()}
  async function load(){
    if(pending)return;
    const token=generation;pending=true;error=false;
    try{
      const result=await global.MPosCore.Receipts.page(offset,limit);
      if(token!==generation)return;
      rows=result.rows;total=result.total;
      if(offset>=total&&offset>0){offset=Math.max(0,Math.floor(Math.max(0,total-1)/limit)*limit);rows=null;}
    }catch(_){if(token===generation)error=true;}
    finally{if(token===generation){pending=false;redraw();}}
  }
  function move(direction){
    if(pending)return;
    offset=Math.max(0,Math.min(Math.max(0,Math.floor(Math.max(0,total-1)/limit)*limit),offset+direction*limit));
    generation++;rows=null;load();redraw();
  }
  global.MPosCore.ReceiptsHistory=Object.freeze({
    next:()=>move(1),previous:()=>move(-1),retry(){error=false;load();redraw();},
    invalidate(){generation++;pending=false;rows=null;offset=0;error=false;redraw();}
  });
  global.renderReceiptsScreen=function(){
    const current=appState();
    if(current?.tab!=='receipts')return original();
    if(rows===null&&!pending&&!error)void load();
    const archive=current.orders;
    let html;
    try{current.orders=error?archive:(rows||[]);html=original();}finally{current.orders=archive;}
    const controls=error?
      '<div class="modal-actions"><span>Не удалось загрузить историю</span><button class="btn btn-secondary" onclick="MPosCore.ReceiptsHistory.retry()">Повторить</button></div>':
      `<div class="modal-actions"><button class="btn btn-secondary" onclick="MPosCore.ReceiptsHistory.previous()" ${pending||offset===0?'disabled':''}>Назад</button><span>${pending?'Загрузка…':total?`${offset+1}–${Math.min(offset+limit,total)} из ${total}`:'0 чеков'}</span><button class="btn btn-secondary" onclick="MPosCore.ReceiptsHistory.next()" ${pending||offset+limit>=total?'disabled':''}>Далее</button></div>`;
    return html.replace('</aside>',controls+'</aside>');
  };
})(window);
