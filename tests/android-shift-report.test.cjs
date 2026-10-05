const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.resolve(__dirname,'..');
const source=fs.readFileSync(path.join(root,'app/src/main/assets/pos/Web/js/features/shifts.js'),'utf8');

function host({storageFails=false,telegramFails=false,notifications=true}={}){
 const calls=[],messages=[];
 const state={currency:'Br',company:{establishmentName:'Тестовая касса'},employees:[],orders:[],shifts:[{id:'shift-1',status:'open',employeeName:'Анна',openingCash:50,openedAt:1,cashMovements:[]}]};
 const context={state,criticalOperationBusy:false,document:{getElementById:()=>({value:'50'})},
  telegramConfigFromState:()=>({enabled:true,notifyShiftClosed:notifications,botToken:'fixture',chatId:'fixture-chat',threadId:'42'}),
  storageSnapshot:value=>JSON.parse(JSON.stringify(value)),
  commitCriticalStorage:async(type,writes)=>{calls.push({type,writes});if(storageFails)throw new Error('disk full');},
  flash:()=>{},closeModal:()=>{},render:()=>{},console:{error:()=>{}},
  webkit:{messageHandlers:{telegram:{postMessage(payload){calls.push({type:'telegram'});if(telegramFails)throw new Error('offline');messages.push(payload);}}}},
  printShiftCloseReceipt:report=>calls.push({type:'printer',report})
 };
 context.window=context;vm.createContext(context);vm.runInContext(source,context);
 return {context,state,calls,messages};
}

test('shift photo command follows durable close and preserves complete report contract',async()=>{
 const h=host();await h.context.submitCloseShift();
 assert.deepEqual(h.calls.map(c=>c.type),['close-shift','telegram','printer']);
 assert.equal(h.state.shifts[0].status,'closed');
 const message=h.messages[0];
 assert.equal(message.action,'sendShiftCloseReport');
 assert.equal(message.threadId,'42');
 assert.equal(message.report.expectedCash,50);
 assert.equal(message.report.countedCash,50);
 assert.equal(message.report.difference,0);
 assert.equal(message.report.establishmentName,'Тестовая касса');
 assert.ok(Array.isArray(message.report.orders));assert.ok(Array.isArray(message.report.cashMovements));
});

test('failed local close does not send Telegram or print and leaves shift open',async()=>{
 const h=host({storageFails:true});await h.context.submitCloseShift();
 assert.deepEqual(h.calls.map(c=>c.type),['close-shift']);
 assert.equal(h.state.shifts[0].status,'open');assert.equal(h.messages.length,0);
});

test('Telegram failure cannot undo closed shift or prevent receipt printing',async()=>{
 const h=host({telegramFails:true});await h.context.submitCloseShift();
 assert.equal(h.state.shifts[0].status,'closed');
 assert.deepEqual(h.calls.map(c=>c.type),['close-shift','telegram','printer']);
});

test('disabled closing notification remains disabled',async()=>{
 const h=host({notifications:false});await h.context.submitCloseShift();
 assert.equal(h.messages.length,0);assert.equal(h.state.shifts[0].status,'closed');
});

test('native shift action uses photo transport and dedicated iPad-compatible callback',()=>{
 const client=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/telegram/TelegramClient.kt'),'utf8');
 const activity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/MainActivity.kt'),'utf8');
 assert.match(client,/MPosShiftReceiptImage\.render\(report\)/);assert.match(client,/\/sendPhoto/);
 assert.doesNotMatch(client,/shiftText/);assert.match(activity,/onTelegramShiftClosedResult/);
});
