(function(global){
  'use strict';

  const bridge=global.webkit?.messageHandlers?.settings;
  if(!bridge)return;

  let sequence=0;
  function post(action,payload){
    const requestId='settings-'+(++sequence);
    bridge.postMessage({action,requestId,...(payload||{})});
    return requestId;
  }

  function snapshot(){
    try{
      const value=global.__printerSettingsSnapshot?.();
      if(!value||!Array.isArray(value.printers)||!value.posNotifications)return null;
      return value;
    }catch(error){
      console.error('[NativeSettings] snapshot failed',error);
      return null;
    }
  }

  function mirror(){
    const settings=snapshot();
    if(settings)post('replacePlatformSettings',{settings});
  }

  function wrap(name){
    const original=global[name];
    if(typeof original!=='function')return;
    global[name]=function(...args){
      const result=original.apply(this,args);
      if(result&&typeof result.then==='function'){
        return result.then(value=>{if(value!==false)mirror();return value});
      }
      if(result!==false)mirror();
      return result;
    };
  }

  ['savePrinterFromPage','deletePrinter','saveNotificationSettings','__restorePrinterSettings'].forEach(wrap);
  global.__syncNativePlatformSettings=mirror;
  global.__nativeSettingsResult=result=>{global.__lastNativeSettingsResult=result||null};

  setTimeout(mirror,0);
})(window);
