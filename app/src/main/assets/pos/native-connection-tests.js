/* Bounded, correlated feedback for explicit user connection tests. No automatic retries. */
(function(global){
  'use strict';
  const handlers=global.webkit?.messageHandlers;
  if(!handlers)return;
  const pending=new Map(),active=new Map();let sequence=0,printerTicket=null;
  const enabled=()=>global.MPosNativeConnectionTestsEnabled!==false;
  const feedback=()=>global.MPosCore?.ConnectionTestFeedback?.capture()||(message=>global.flash?.(message));
  const id=kind=>'connection-test-'+kind+'-'+Date.now()+'-'+(++sequence);
  function ticket(kind,timeoutMessage,milliseconds){
    const requestId=id(kind);let finish;
    const promise=new Promise(resolve=>{finish=result=>{const entry=pending.get(requestId);if(!entry)return;pending.delete(requestId);clearTimeout(entry.timer);resolve(result)}});
    const timer=setTimeout(()=>finish({ok:false,message:timeoutMessage}),milliseconds);
    pending.set(requestId,{kind,finish,timer});return{requestId,promise,finish};
  }
  function once(kind,run,notify){
    if(active.has(kind)){notify('Дождитесь завершения предыдущей проверки');return Promise.resolve(false)}
    const promise=run();active.set(kind,promise);
    promise.finally(()=>{if(active.get(kind)===promise)active.delete(kind)}).catch(()=>{});return promise;
  }
  const previousResult=global.__nativeConnectionTestResult;
  global.__nativeConnectionTestResult=result=>{
    const entry=pending.get(result?.requestId);
    if(entry?.kind==='telegram')entry.finish({ok:result.ok===true,message:result.ok===true?'Telegram подключён':String(result.message||'Не удалось подключиться к Telegram')});
    else if(typeof previousResult==='function')previousResult(result);
  };
  const originalTelegram=global.testTelegramConnection;
  global.testTelegramConnection=function(...args){
    if(!enabled())return originalTelegram?.apply(this,args);
    const notify=feedback();return once('telegram',async()=>{
      if(!global.currentShiftEmployeeIsAdmin()){notify('Сетевые конфигурации доступны только администратору');return false}
      const read=name=>String(document.getElementById(name)?.value||'').trim();
      const botToken=read('telegram-token'),chatId=read('telegram-chat-id'),threadId=read('telegram-thread-id');
      if(!botToken||!chatId){notify(!botToken?'Введите токен бота':'Введите ID рабочей группы');return false}
      const item=ticket('telegram','Telegram не ответил вовремя. Проверьте группу перед повтором',30000);
      try{if(global.webkit.messageHandlers.telegram.postMessage({action:'test',requestId:item.requestId,botToken,chatId,threadId})===false)throw Error('bridge unavailable')}
      catch(_error){item.finish({ok:false,message:'Не удалось передать проверку Telegram'})}
      const result=await item.promise;notify(result.message);return result.ok;
    },notify);
  };
  const originalWeb=global.testWebOrder;
  global.testWebOrder=function(...args){
    if(!enabled())return originalWeb?.apply(this,args);
    const notify=feedback();return once('web',async()=>{
      if(!global.currentShiftEmployeeIsAdmin()){notify('Сетевые конфигурации доступны только администратору');return false}
      const config=global.networkConfigFromState(),base=String(config.backendUrl||'').replace(/\/+$/,'');
      if(!/^https:\/\//i.test(base)||!config.deviceKey){notify('Проверьте адрес backend и сохраните настройки подключения');return false}
      const controller=new AbortController();let timer;
      const timeout=new Promise(resolve=>{timer=setTimeout(()=>{controller.abort();resolve({ok:false,message:'Сервер не ответил вовремя. Проверьте WEB-заказы перед повтором'})},15000)});
      const request=(async()=>{
        try{
          const response=await global.fetch(base+'/api/orders/test',{method:'POST',headers:{'Content-Type':'application/json','X-Device-Key':config.deviceKey},body:'{}',cache:'no-store',signal:controller.signal});
          return response.ok?{ok:true,message:'Тестовый заказ отправлен'}:{ok:false,message:'Не удалось создать тестовый заказ: HTTP '+response.status};
        }catch(_error){return{ok:false,message:controller.signal.aborted?'Сервер не ответил вовремя. Проверьте WEB-заказы перед повтором':'Не удалось создать тестовый заказ. Проверьте подключение к backend'}}
      })();
      const result=await Promise.race([request,timeout]);clearTimeout(timer);notify(result.message);return result.ok;
    },notify);
  };
  // Keep reviewed printer configuration and native disk acknowledgement ordering.
  const printer=handlers.printer,originalPrint=global.testPrinterFromPage,previousPrinterEvent=global.__nativePrinterEvent;
  if(printer&&typeof originalPrint==='function'){
    global.webkit.messageHandlers={...handlers,printer:{postMessage(payload){
      if(printerTicket&&payload?.order?.__networkTest){
        printerTicket.posted=true;const requestId=printerTicket.requestId;
        try{if(printer.postMessage({...payload,requestId})===false)throw Error('bridge unavailable');return true}
        catch(_error){printerTicket.finish({ok:false,message:'Не удалось передать пробную печать'});return false}
      }
      return printer.postMessage(payload);
    }}};
    global.__nativePrinterEvent=event=>{
      const entry=pending.get(event?.requestId);
      if(entry?.kind==='printer'){
        if(event.type==='printed')entry.finish({ok:true,message:'Пробная печать отправлена. Проверьте бумажный чек'});
        else if(event.type==='printError'||event.type==='printAdmission'&&event.ok!==true)entry.finish({ok:false,message:String(event.message||'Не удалось принять пробную печать')});
      }
      if(String(event?.requestId||'').startsWith('connection-test-printer-'))return;
      if(typeof previousPrinterEvent==='function')previousPrinterEvent(event);
    };
    global.testPrinterFromPage=function(...args){
      if(!enabled())return originalPrint.apply(this,args);
      const notify=feedback();return once('printer',async()=>{
        const item=ticket('printer','Статус пробной печати неизвестен. Проверьте принтер перед повтором',30000);printerTicket=item;
        try{
          const accepted=await originalPrint.apply(this,args);
          if(!item.posted){item.finish({ok:false,message:''});return accepted===true}
          const result=await item.promise;notify(result.message);return result.ok;
        }catch(error){item.finish({ok:false,message:''});throw error}
        finally{if(printerTicket===item)printerTicket=null}
      },notify);
    };
  }
})(window);
