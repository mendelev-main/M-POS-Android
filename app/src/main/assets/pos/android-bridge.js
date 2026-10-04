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
    storage:handler('storage')
  });
  global.__MPOS_PLATFORM__='android';
  global.__MPOS_VERSION__='0.1.0';
  function stampVersion(){const node=document.querySelector('.settings-version');if(node&&node.textContent.includes('__MPOS_VERSION__'))node.textContent=node.textContent.replace('__MPOS_VERSION__',global.__MPOS_VERSION__)}
  document.addEventListener('DOMContentLoaded',()=>{stampVersion();new MutationObserver(stampVersion).observe(document.body,{childList:true,subtree:true})});
})(window);

