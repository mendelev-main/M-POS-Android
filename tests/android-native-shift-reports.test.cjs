const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.join(__dirname,'../app/src/main/assets/pos'),source=n=>fs.readFileSync(path.join(root,n),'utf8');
function host({opened=false}={}){
 const commands=[],telegram=[],printer=[],modals=[],events=[],initialized=new Set(),legacy=new Map();let timer=0;
 const state={shifts:[{id:'s1',status:opened?'open':'closed',openedAt:100,closedAt:opened?undefined:3000,openingCash:100,countedCash:100,employeeName:'Кассир',cashMovements:[]}],orders:[],employees:[],currency:'BYN',company:{establishmentName:'Тестовая касса'}};
 for(const [key,value] of Object.entries(state))legacy.set(key,value);
 const local=new Map([['printers',JSON.stringify([{id:'printer1',ip:'192.0.2.11',copies:2,enabled:true,printReceipts:true}])]]);
 const ctx={state,criticalOperationBusy:false,criticalStorageRecoveryPending:false,
  PrilavokCore:{Storage:{async get(k,f){return legacy.has(k)?structuredClone(legacy.get(k)):f},async set(k,v){legacy.set(k,v)},remove:k=>legacy.delete(k),describe:()=>({})}},
  localStorage:{getItem:k=>local.get(k)??null,setItem:(k,v)=>local.set(k,v),removeItem:k=>local.delete(k)},
  commitCriticalStorage:async()=>{},setTimeout:()=>++timer,clearTimeout(){},console:{error(){}},
  document:{getElementById:()=>({value:'100'})},storageSnapshot:v=>JSON.parse(JSON.stringify(v)),
  telegramConfigFromState:()=>({enabled:true,notifyShiftClosed:true,botToken:'synthetic-token',chatId:'synthetic-chat'}),
  showModal:html=>modals.push(html),closeModal:()=>events.push('close'),flash:msg=>events.push('flash:'+msg),render:()=>events.push('render'),markStorageBroken(){},
  fmtDate:String,money:v=>String(v),escapeHtml:s=>s,escapeAttr:s=>s,print:()=>events.push('window-print')};
 ctx.window=ctx;
 ctx.webkit={messageHandlers:{
  storage:{postMessage(c){commands.push(c);if(c.action==='shiftReportRead'||c.action==='shiftLifecycleCommit')return true;
   const r={requestId:c.requestId,ok:true,authoritative:true};if(c.action.endsWith('Status'))r.initialized=initialized.has(c.key);if(c.action.endsWith('Initialize'))initialized.add(c.key);ctx.__nativeStorageResult(r);return true}},
  telegram:{postMessage:p=>telegram.push(structuredClone(p))},printer:{postMessage:p=>printer.push(structuredClone(p))}
 }};
 vm.createContext(ctx);vm.runInContext(source('native-storage-shadow.js'),ctx);vm.runInContext(source('Web/js/features/shifts.js'),ctx);vm.runInContext(source('native-shift-accounting.js'),ctx);vm.runInContext(source('native-shift-lifecycle-command.js'),ctx);vm.runInContext(source('network-printer.js'),ctx);vm.runInContext(source('native-shift-reports.js'),ctx);
 const model=(id='s1',cash=100)=>({report:{id,employeeName:'Native employee',employeePhone:'',openedAt:100,closedAt:3000,openingCash:cash,countedCash:cash,expectedCash:cash,difference:0,cash:0,card:0,total:0,count:0,deposits:0,withdrawals:0,netMovements:0,currency:'BYN',establishmentName:'Тестовая касса',cashMovements:[],orders:[]},summary:{expectedCash:cash}});
 return {ctx,commands,telegram,printer,modals,events,model,
  replyRead(value=model(),ok=true,id){const c=id?commands.find(c=>c.requestId===id):commands.filter(c=>c.action==='shiftReportRead').at(-1);ctx.__nativeStorageResult({requestId:c.requestId,ok,authoritative:true,...value})},
  replyCommit(){const c=commands.find(c=>c.action==='shiftLifecycleCommit');ctx.__nativeStorageResult({requestId:c.requestId,ok:true,authoritative:true})}};
}
async function flushUntil(p){for(let i=0;i<300&&!p();i++)await Promise.resolve();assert.ok(p())}
test('Telegram and LAN receipt coalesce a native snapshot and discard caller financial values',async()=>{
 const h=host(),print=h.ctx.printShiftCloseReceipt({id:'s1',expectedCash:999,total:999}),send=h.ctx.sendTelegramShiftClosed({id:'s1'});
 await flushUntil(()=>h.commands.some(c=>c.action==='shiftReportRead'));
 const req=h.commands.filter(c=>c.action==='shiftReportRead');assert.equal(req.length,1);
 assert.deepEqual(Object.keys(JSON.parse(req[0].payload)).sort(),['closedOnly','currency','establishmentName','shiftId']);assert.equal(h.telegram.length,0);assert.equal(h.printer.length,0);
 h.replyRead(h.model('s1',80));assert.equal(await print,true);await send;
 assert.equal(h.telegram[0].action,'sendShiftCloseReport');assert.equal(h.telegram[0].report.expectedCash,80);assert.equal(h.printer.length,2);
 for(const job of h.printer){assert.equal(job.order.expectedCash,80);assert.equal(job.order.__printDocumentType,'shift-close');assert.equal(job.order.__networkPrinterIp,'192.0.2.11')}
});
test('actual closure waits for durable commit, then native report, then image and printer',async()=>{
 const h=host({opened:true}),closing=h.ctx.submitCloseShift();await flushUntil(()=>h.commands.some(c=>c.action==='shiftLifecycleCommit'));
 assert.equal(h.commands.some(c=>c.action==='shiftReportRead'),false);assert.equal(h.telegram.length,0);h.replyCommit();assert.equal(await closing,true);
 await flushUntil(()=>h.commands.some(c=>c.action==='shiftReportRead'));assert.equal(h.commands.filter(c=>c.action==='shiftReportRead').length,1);assert.equal(h.telegram.length,0);assert.equal(h.printer.length,0);
 h.replyRead();await flushUntil(()=>h.telegram.length===1&&h.printer.length===2);assert.equal(h.ctx.state.shifts[0].status,'closed');
});
test('manual PDF printing and report dialog use native values despite stale WebView totals',async()=>{
 const h=host();h.ctx.state.orders=[{id:'stale',shiftId:'s1',total:999,method:'cash'}];
 const printing=h.ctx.printShiftReport('s1');await flushUntil(()=>h.commands.some(c=>c.action==='shiftReportRead'));h.replyRead(h.model('s1',70));await printing;
 assert.equal(h.printer[0].action,'printShiftReport');assert.equal(h.printer[0].report.expectedCash,70);
 const showing=h.ctx.viewShiftModal('s1');await flushUntil(()=>h.commands.filter(c=>c.action==='shiftReportRead').length===2);h.replyRead({...h.model('s1',70),report:{...h.model('s1',70).report,total:12,cash:8,card:4,count:2,difference:-3}});await showing;
 assert.match(h.modals.at(-1),/>12</);assert.match(h.modals.at(-1),/>-3</);assert.doesNotMatch(h.modals.at(-1),/>999</);
});
test('report read failure never emits stale image, receipt or PDF and keeps closure persisted',async()=>{
 const h=host();const print=h.ctx.printShiftCloseReceipt({id:'s1'}),send=h.ctx.sendTelegramShiftClosed({id:'s1'});
 await flushUntil(()=>h.commands.some(c=>c.action==='shiftReportRead'));h.replyRead({},false);assert.equal(await print,false);await send;
 assert.equal(h.telegram.length,0);assert.equal(h.printer.length,0);assert.equal(h.ctx.state.shifts[0].status,'closed');assert.ok(h.events.some(e=>e.startsWith('flash:')));
});
test('closing or replacing a loading modal prevents late native response from reopening it',async()=>{
 const h=host();const first=h.ctx.viewShiftModal('s1');await flushUntil(()=>h.commands.some(c=>c.action==='shiftReportRead'));h.ctx.closeModal();h.replyRead();await first;assert.equal(h.modals.length,1);
 const second=h.ctx.viewShiftModal('s1');await flushUntil(()=>h.commands.filter(c=>c.action==='shiftReportRead').length===2);h.ctx.showModal('Another modal');h.replyRead();await second;assert.equal(h.modals.at(-1),'Another modal');
});
test('fresh manual reads after a completed output and no Telegram request when notification disabled',async()=>{
 const h=host();const first=h.ctx.printShiftReport('s1');await flushUntil(()=>h.commands.some(c=>c.action==='shiftReportRead'));h.replyRead(h.model('s1',60));await first;
 const second=h.ctx.printShiftReport('s1');await flushUntil(()=>h.commands.filter(c=>c.action==='shiftReportRead').length===2);h.replyRead(h.model('s1',40));await second;assert.equal(h.printer[1].report.expectedCash,40);
 h.ctx.telegramConfigFromState=()=>({enabled:false});await h.ctx.sendTelegramShiftClosed({id:'s1'});assert.equal(h.commands.filter(c=>c.action==='shiftReportRead').length,2);
});
test('report adapter is loaded after the printer runtime so its native guard cannot be overwritten',()=>{
 const html=source('pos.html');assert.ok(html.indexOf('src="native-shift-reports.js"')>html.indexOf('src="network-printer.js"'));
});
