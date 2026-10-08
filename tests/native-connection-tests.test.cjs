const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/pos/native-connection-tests.js','utf8');
function host(){
 const events=[],sent=[],timers=new Map(),requests=[];let next=0,admin=true,completePrint;
 const fields={'telegram-token':{value:'synthetic-token'},'telegram-chat-id':{value:'synthetic-chat'},'telegram-thread-id':{value:'42'}};
 const h={document:{getElementById:id=>fields[id]},setTimeout:(fn,ms)=>{timers.set(++next,{fn,ms});return next},clearTimeout:id=>timers.delete(id),AbortController,
 currentShiftEmployeeIsAdmin:()=>admin,networkConfigFromState:()=>({backendUrl:'https://backend.example/',deviceKey:'synthetic-key'}),flash:m=>events.push(m),
 webkit:{messageHandlers:{telegram:{postMessage:p=>{sent.push(p);return true}},printer:{postMessage:p=>{sent.push(p);return true}}}},
 fetch:(url,options)=>new Promise((resolve,reject)=>requests.push({url,options,resolve,reject})),
 testPrinterFromPage:async()=>{await new Promise(r=>completePrint=r);h.webkit.messageHandlers.printer.postMessage({action:'print',order:{__networkTest:true,__networkPrinterIp:'192.0.2.10'}})},__nativePrinterEvent:()=>events.push('legacy-event')};
 h.window=h;vm.createContext(h);vm.runInContext(source,h);
 return{h,events,sent,timers,requests,fields,admin:value=>admin=value,printSaved:()=>completePrint(),timeout:ms=>{const [id,t]=[...timers.entries()].find(([,t])=>t.ms===ms);timers.delete(id);t.fn()}};
}
const flush=async()=>{for(let i=0;i<8;i++)await Promise.resolve()};
test('WEB test waits for HTTP result, coalesces repeated clicks and returns explicit success',async()=>{
 const x=host(),p=x.h.testWebOrder();assert.equal(await x.h.testWebOrder(),false);assert.equal(x.requests.length,1);assert.deepEqual(x.events,['Дождитесь завершения предыдущей проверки']);
 const r=x.requests[0];assert.equal(r.url,'https://backend.example/api/orders/test');assert.equal(r.options.method,'POST');assert.equal(r.options.headers['X-Device-Key'],'synthetic-key');r.resolve({ok:true,status:200});assert.equal(await p,true);assert.deepEqual(x.events,['Дождитесь завершения предыдущей проверки','Тестовый заказ отправлен']);assert.equal(x.timers.size,0);
});
test('WEB HTTP rejection and offline failure are visible without leaking server payload',async()=>{
 for(const mode of ['http','offline']){const x=host(),p=x.h.testWebOrder();if(mode==='http')x.requests[0].resolve({ok:false,status:401,json:()=>({error:'sensitive-data'})});else x.requests[0].reject(Error('sensitive-data'));assert.equal(await p,false);assert.match(x.events[0],mode==='http'?/HTTP 401/:/backend/);assert.doesNotMatch(x.events[0],/sensitive/)}
});
test('WEB timeout aborts request, warns about uncertain order and ignores a late response',async()=>{
 const x=host(),p=x.h.testWebOrder();x.timeout(15000);assert.equal(await p,false);assert.equal(x.requests[0].options.signal.aborted,true);assert.match(x.events[0],/перед повтором/);x.requests[0].resolve({ok:true,status:200});await flush();assert.equal(x.events.length,1);assert.equal(x.requests.length,1);
});
test('Telegram result is request correlated; duplicate, forged and late replies cannot settle another test',async()=>{
 const x=host(),p=x.h.testTelegramConnection();assert.equal(await x.h.testTelegramConnection(),false);assert.equal(x.sent.length,1);const id=x.sent[0].requestId;assert.equal(x.sent[0].threadId,'42');
 x.h.__nativeConnectionTestResult({requestId:'other',ok:true});assert.deepEqual(x.events,['Дождитесь завершения предыдущей проверки']);x.h.__nativeConnectionTestResult({requestId:id,ok:true});assert.equal(await p,true);assert.deepEqual(x.events,['Дождитесь завершения предыдущей проверки','Telegram подключён']);
 const second=x.h.testTelegramConnection();x.h.__nativeConnectionTestResult({requestId:id,ok:false,message:'old'});assert.equal(x.events.length,2);x.h.__nativeConnectionTestResult({requestId:x.sent[1].requestId,ok:false,message:'Telegram HTTP 403'});assert.equal(await second,false);assert.equal(x.events.at(-1),'Telegram HTTP 403');assert.equal(x.timers.size,0);
});
test('Telegram timeout and bridge rejection finish once and never retry',async()=>{
 const x=host(),p=x.h.testTelegramConnection();x.timeout(30000);assert.equal(await p,false);x.h.__nativeConnectionTestResult({requestId:x.sent[0].requestId,ok:true});assert.equal(x.events.length,1);assert.equal(x.sent.length,1);
 x.h.webkit.messageHandlers.telegram.postMessage=()=>false;assert.equal(await x.h.testTelegramConnection(),false);assert.match(x.events.at(-1),/передать/);assert.equal(x.timers.size,0);
});
test('permission and missing credentials fail locally without sending',async()=>{
 const x=host();x.admin(false);assert.equal(await x.h.testTelegramConnection(),false);assert.equal(await x.h.testWebOrder(),false);assert.equal(x.sent.length,0);assert.equal(x.requests.length,0);x.admin(true);x.fields['telegram-token'].value='';assert.equal(await x.h.testTelegramConnection(),false);assert.match(x.events.at(-1),/Введите токен/);assert.equal(x.sent.length,0);
});
test('printer test waits for saved settings and terminal transport event, not queue admission',async()=>{
 const x=host(),p=x.h.testPrinterFromPage();assert.equal(await x.h.testPrinterFromPage(),false);assert.equal(x.sent.length,0);x.printSaved();await flush();assert.equal(x.sent.length,1);const id=x.sent[0].requestId;let done=false;p.then(()=>done=true);
 x.h.__nativePrinterEvent({type:'printAdmission',requestId:id,ok:true,count:1});await flush();assert.equal(done,false);x.h.__nativePrinterEvent({type:'printed',requestId:id});assert.equal(await p,true);assert.match(x.events.at(-1),/Проверьте бумажный чек/);assert.equal(x.events.includes('legacy-event'),false);
 x.h.__nativePrinterEvent({type:'printed',requestId:id});assert.equal(x.events.length,2);x.h.__nativePrinterEvent({type:'printed',requestId:'ordinary-print'});assert.equal(x.events.at(-1),'legacy-event');
});
test('printer queue refusal, transport failure and unknown result are visible without replay',async()=>{
 for(const mode of ['queue','transport','timeout']){const x=host(),p=x.h.testPrinterFromPage();x.printSaved();await flush();const requestId=x.sent[0].requestId;
 if(mode==='timeout')x.timeout(30000);else x.h.__nativePrinterEvent(mode==='queue'?{type:'printAdmission',requestId,ok:false}:{type:'printError',requestId,message:'Ошибка печати'});
 assert.equal(await p,false);assert.equal(x.sent.length,1);assert.match(x.events[0],mode==='timeout'?/перед повтором/:/печат/);x.h.__nativePrinterEvent({type:'printed',requestId});assert.equal(x.events.length,1);assert.equal(x.timers.size,0)}
});
test('legacy callback and correlated native test are wired without changing reviewed source',()=>{
 const main=fs.readFileSync('app/src/main/java/com/mendelev/mpos/MainActivity.kt','utf8'),html=fs.readFileSync('app/src/main/assets/pos/pos.html','utf8');assert.match(main,/window\.onTelegramResult&&window\.onTelegramResult/);assert.doesNotMatch(main,/window\.handleTelegramResult/);assert.match(main,/window\.__nativeConnectionTestResult/);assert.ok(html.indexOf('src="native-connection-tests.js"')<html.indexOf('src="native-settings-ui.js"'));assert.ok(html.indexOf('src="native-connection-tests.js"')>html.indexOf('src="native-platform-settings.js"'));
});

test('WEB missing server test route reports contract mismatch rather than offline',async()=>{
 const x=host(),p=x.h.testWebOrder();x.requests[0].resolve({ok:false,status:404});assert.equal(await p,false);assert.match(x.events[0],/маршрут тестового заказа отсутствует/);
});
test('backend health alone is insufficient; unregistered key is actionable without automatic sync',async()=>{
 const x=host();x.h.saveNetworkSettings=()=>true;const p=x.h.testBackendConnection();await flush();assert.equal(x.requests[0].url,'https://backend.example/health');x.requests[0].resolve({ok:true,status:200,json:async()=>({ok:true})});await flush();assert.match(x.requests[1].url,/orders\/events\?deviceKey=/);x.requests[1].resolve({ok:false,status:401});assert.equal(await p,false);assert.match(x.events[0],/ручную синхронизацию/);assert.equal(x.sent.length,0);assert.equal(x.timers.size,0);
});
test('backend accepted key restarts stream only after successful explicit test',async()=>{
 const x=host();x.h.saveNetworkSettings=()=>true;let started=0;x.h.startWebOrderEvents=()=>started++;const p=x.h.testBackendConnection();await flush();x.requests[0].resolve({ok:true,status:200,json:async()=>({ok:true})});await flush();x.requests[1].resolve({ok:true,status:200});assert.equal(await p,true);assert.equal(started,1);
});
