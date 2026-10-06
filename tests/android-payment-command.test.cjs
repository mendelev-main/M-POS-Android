const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.join(__dirname,'../app/src/main/assets/pos');
const source=name=>fs.readFileSync(path.join(root,name),'utf8');
function host({archiveSize=0}={}){
 const events=[],commands=[],timers=new Map(),initialized=new Set();let timerId=0,sequence=0;
 const products=[{id:'p1',type:'simple',stock:5.125,price:12.35,cost:1,category:'Напитки'}];
 const shifts=[{id:'s1',status:'open',employeeId:'e1',openingCash:100,cashMovements:[]}];
 const legacy=new Map([['products',products],['shifts',shifts],['orders',Array.from({length:archiveSize},(_,i)=>({id:'old'+i,shiftId:'old'}))]]);
 const context={state:{products,shifts,orders:legacy.get('orders'),employees:[{id:'e1',name:'Кассир'}],cart:[{productId:'p1',qty:1,price:12.35}],
  discounts:[],customer:{name:''},loyaltyRedemptions:{},orderType:'На месте',orderLabel:'',deliveryFee:0,printer:{}},
  criticalOperationBusy:false,criticalStorageRecoveryPending:false,
  PrilavokCore:{Storage:{async get(key,fallback){return legacy.has(key)?structuredClone(legacy.get(key)):fallback;},async set(key,v){legacy.set(key,v)},remove:key=>legacy.delete(key),describe:()=>({})}},
  commitCriticalStorage:async()=>{events.push('legacy-commit')},
  setTimeout(fn,delay){timers.set(++timerId,{fn,delay});return timerId;},clearTimeout:id=>timers.delete(id),console:{error(){}},
  uid:()=> 'o'+(++sequence),flash:message=>events.push('flash:'+message),markStorageBroken:()=>events.push('storage-error'),
  requireDeliveryTariff:()=>true,currentShift:()=>context.state.shifts[0],cartTotal:()=>12.35,
  checkedStockConsumption:()=>({version:1,items:[{productId:'p1',qty:0.125}]}),
  loyaltyRewardAllocation:()=>({allocations:{}}),loyaltyReceiptSnapshot:()=>({discount:0,programs:[]}),discountValue:()=>0,
  storageSnapshot:value=>JSON.parse(JSON.stringify(value)),getProduct:id=>context.state.products.find(p=>p.id===id),
  roundStockQty:value=>Math.round((value+Number.EPSILON)*1000)/1000,emptyCurrentOrderSession:()=>({items:[],updatedAt:1000}),
  publishAvailability:()=>events.push('availability'),resetCurrentOrderState(){events.push('reset');context.state.cart=[]},
  closeModal(){},showPaymentReceipt:()=>events.push('receipt'),publishPaidOrderLoyalty:()=>events.push('loyalty'),printCompletedOrder:()=>events.push('print')};
 context.window=context;
 context.webkit={messageHandlers:{storage:{postMessage(command){commands.push(command);
  if(command.action==='paymentCommit'){events.push('native-command');return true;}
  const result={requestId:command.requestId,ok:true,authoritative:true};
  if(command.action.endsWith('Status'))result.initialized=initialized.has(command.key);
  if(command.action.endsWith('Initialize'))initialized.add(command.key);
  context.__nativeStorageResult(result);return true;
 }}}};
 vm.createContext(context);vm.runInContext(source('native-storage-shadow.js'),context);
 vm.runInContext(source('Web/js/features/payment.js'),context);context.showPaymentReceipt=()=>events.push('receipt');vm.runInContext(source('native-payment-command.js'),context);
 return {context,events,commands,timers,reply(ok=true){const command=commands.filter(c=>c.action==='paymentCommit').at(-1);events.push('native-ack');context.__nativeStorageResult({requestId:command.requestId,ok,authoritative:true});}};
}
const parts=[{method:'cash',amount:5,cashGiven:10,change:5},{method:'card',amount:7.35,cashGiven:null,change:null}];
async function flushUntil(predicate){for(let i=0;i<200&&!predicate();i++)await Promise.resolve();assert.ok(predicate());}

test('actual finalization sends one receipt without archive and external effects wait for native acknowledgement',async()=>{
 const h=host({archiveSize:500});const saving=h.context.finalizePayment(parts);
 await flushUntil(()=>h.events.includes('native-command'));
 const command=JSON.parse(h.commands.find(c=>c.action==='paymentCommit').payload);
 assert.equal(command.expectedOrderCount,500);assert.equal(command.order.receiptNumber,1);
 assert.equal(command.products[0].stock,5);assert.equal(command.order.payments.length,2);
 assert.equal(Object.hasOwn(command,'orders'),false);assert.equal(Object.hasOwn(command,'writes'),false);
 assert.equal(h.events.includes('legacy-commit'),false);assert.equal(h.events.includes('availability'),false);assert.equal(h.events.includes('reset'),false);
 assert.equal(h.context.state.cart.length,1);h.reply();await saving;
 assert.equal(h.context.state.orders.length,501);assert.equal(h.context.state.cart.length,0);
 for(const event of ['availability','reset','receipt','loyalty'])assert.ok(h.events.indexOf(event)>h.events.indexOf('native-ack'));
 [...h.timers.values()].find(t=>t.delay===250).fn();assert.ok(h.events.includes('print'));
});
test('native transaction failure leaves cart stock and history unchanged with no external effects',async()=>{
 const h=host(),saving=h.context.finalizePayment(parts);await flushUntil(()=>h.events.includes('native-command'));h.reply(false);await saving;
 assert.equal(h.context.state.orders.length,0);assert.equal(h.context.state.products[0].stock,5.125);assert.equal(h.context.state.cart.length,1);
 assert.equal(h.events.includes('availability'),false);assert.equal(h.events.includes('loyalty'),false);assert.equal(h.events.includes('print'),false);
 assert.equal(h.events.includes('legacy-commit'),false);
});
test('uncertain acknowledgement retries identical command once and then blocks subsequent critical saves',async()=>{
 const h=host(),saving=h.context.finalizePayment(parts);await flushUntil(()=>h.commands.some(c=>c.action==='paymentCommit'));
 const timeout=()=>{const [id,timer]=[...h.timers.entries()].find(([,t])=>t.delay===15000);h.timers.delete(id);timer.fn();};
 timeout();await flushUntil(()=>h.commands.filter(c=>c.action==='paymentCommit').length===2);
 const requests=h.commands.filter(c=>c.action==='paymentCommit');assert.equal(requests[0].payload,requests[1].payload);
 timeout();await saving;assert.equal(h.context.criticalStorageRecoveryPending,true);
 await h.context.finalizePayment(parts);assert.equal(h.commands.filter(c=>c.action==='paymentCommit').length,2);
 assert.equal(h.events.includes('availability'),false);
});
test('nonpayment journal operations retain prior provider and pending recovery blocks native payment',async()=>{
 const h=host();await h.context.commitCriticalStorage('return',{orders:[]});assert.equal(h.events.includes('legacy-commit'),true);
 h.context.criticalStorageRecoveryPending=true;
 await h.context.finalizePayment(parts);assert.equal(h.commands.some(c=>c.action==='paymentCommit'),false);
});
