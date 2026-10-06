(function(global){
  'use strict';
  function send(channel,payload){
    try{global.MPosNative.postMessage(JSON.stringify({channel,payload:payload||{}}));return true}
    catch(error){global.flash?.('Нативная функция Android временно недоступна');return false}
  }
  const handler=channel=>Object.freeze({postMessage:payload=>send(channel,payload)});
  global.webkit=global.webkit||{};
  global.webkit.messageHandlers=Object.freeze({
    printer:handler('printer'),
    telegram:handler('telegram'),
    photoPicker:handler('photoPicker'),
    backup:handler('backup'),
    settings:handler('settings'),
    storage:handler('storage'),
    network:handler('network'),
    diagnostics:handler('diagnostics'),
    paymentScreen:handler('paymentScreen'),
    shiftScreen:handler('shiftScreen')
  });
  global.__MPOS_PLATFORM__='android';
  global.__MPOS_VERSION__='0.1.0';
  global.MPosCore=global.MPosCore||{};
  global.MPosCore.Diagnostics=Object.freeze({exportReport:()=>send('diagnostics',{action:'export'})});
  function stampVersion(){
    const node=document.querySelector('.settings-version');
    if(!node)return;
    if(node.textContent.includes('__MPOS_VERSION__'))node.textContent=node.textContent.replace('__MPOS_VERSION__',global.__MPOS_VERSION__);
    const screen=node.closest('.content-screen');
    if(!screen||screen.querySelector('[data-mpos-diagnostics]'))return;
    const button=document.createElement('button');
    button.className='btn btn-outline settings-main-action';
    button.setAttribute('data-mpos-diagnostics','');
    button.type='button';
    button.textContent='Сохранить диагностику';
    button.title='Версия приложения и последние технические события. Без чеков и данных клиентов.';
    button.addEventListener('click',()=>global.MPosCore.Diagnostics.exportReport());
    screen.insertBefore(button,screen.querySelector('.settings-grid'));
  }
  document.addEventListener('DOMContentLoaded',()=>{stampVersion();new MutationObserver(stampVersion).observe(document.body,{childList:true,subtree:true})});
})(window);
