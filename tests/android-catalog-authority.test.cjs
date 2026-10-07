const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.join(__dirname,'../app/src/main/assets/pos');
const adapter=fs.readFileSync(path.join(root,'native-storage-shadow.js'),'utf8');
const cutover=fs.readFileSync(path.join(root,'native-catalog-cutover.js'),'utf8');
const html=fs.readFileSync(path.join(root,'pos.html'),'utf8');
const journalFunctions=html.slice(html.indexOf('function storageSnapshot('),html.indexOf('async function loadAll('));
const journalConstants=html.match(/^const CRITICAL_STORAGE_JOURNAL_KEY.*$/m)[0]+'\n'+html.match(/^const CRITICAL_STORAGE_KEYS.*$/m)[0];
const backup=fs.readFileSync(path.join(root,'Web/js/features/backup.js'),'utf8');
const clone=value=>JSON.parse(JSON.stringify(value));
const products=name=>[{id:'p1',name,price:12.35,stock:5.75,category:'Напитки',components:[{productId:'ingredient',qty:0.25}],modifierGroups:[],custom:{preserve:true}}];

function host({data=new Map(),room={initialized:false,found:false,payload:null},fail=new Set(),cacheFails=false,holdWrite=false,holdKey='products'}={}){
 room.workspace??={};
 const calls=[],timers=new Map();let timerId=0,held,sequence=0;
 const legacy={
  async get(key,fallback){calls.push('legacy-get:'+key);return data.has(key)?clone(data.get(key)):fallback;},
  async set(key,value){calls.push('legacy-set:'+key);if(cacheFails&&['products','layout','posNavigation','employees','shifts','orders','parked','currentOrderSession','criticalStorageJournal'].includes(key))throw new Error('cache disk failure');data.set(key,clone(value));},
  remove(key){calls.push('legacy-remove:'+key);if(cacheFails)throw new Error('cache remove failure');data.delete(key);},
  describe:()=>({mode:'localStorage',sourceOfTruth:'local-pos'})
 };
 const context={PrilavokCore:{Storage:legacy},state:{},criticalStorageRecoveryPending:false,
  uid:()=>String(++sequence),markStorageBroken(error){calls.push('storage-error');},
  setTimeout(fn,delay){timers.set(++timerId,{fn,delay});return timerId;},clearTimeout:id=>timers.delete(id),
  console:{error(){}},localStorage:{get length(){return data.size;},key:i=>'prilavok_'+[...data.keys()][i],getItem:key=>JSON.stringify(data.get(key.slice(9)))},
  document:{documentElement:{dataset:{}}},normalizePosNavigation:clone,emptyCurrentOrderSession:()=>({items:[]}),
  validateSplitPaymentDraft:()=>null,cartTotal:()=>0,render(){calls.push('render');},hasPaidSplitPayment:()=>false,
  __restorePrinterSettings(){calls.push('restore-printers');},
 };
 context.window=context;
 context.webkit={messageHandlers:{storage:{postMessage(command){
  calls.push('native:'+command.action);
  let result={ok:true,authoritative:true,source:'room-catalog'};
  const rejected=fail.has(command.action)||fail.has(command.action+':'+command.key+':'+command.payload);
  if(rejected)result={ok:false,message:'synthetic native failure'};
  else switch(command.action){
   case 'catalogStatus':result.initialized=room.initialized;break;
   case 'catalogInitialize':if(!room.initialized){room.initialized=true;room.found=typeof command.payload==='string';room.payload=room.found?command.payload:null;}break;
   case 'catalogWrite':room.found=true;room.payload=command.payload;break;
   case 'catalogRead':result.found=room.found;result.payload=room.payload;break;
   case 'catalogRemove':room.found=false;room.payload=null;break;
   case 'catalogParity':result={ok:true,matches:true,shadowCaughtUp:true};break;
  }
  if((command.action.startsWith('workspace')||command.action.startsWith('employee')||command.action.startsWith('shift')||command.action.startsWith('order')||command.action.startsWith('parked')||command.action.startsWith('recovery')||command.action.startsWith('supply')||command.action.startsWith('inventory'))&&!rejected){
   const entry=room.workspace[command.key]??={initialized:false,found:false,payload:null};
   switch(command.action.replace(/^(employee|shift|order|parked|recovery|supply|inventory)/,'workspace')){
    case 'workspaceStatus':result.initialized=entry.initialized;break;
    case 'workspaceInitialize':if(!entry.initialized){entry.initialized=true;entry.found=typeof command.payload==='string';entry.payload=entry.found?command.payload:null;}break;
    case 'workspaceWrite':entry.found=true;entry.payload=command.payload;break;
    case 'workspaceRead':result.found=entry.found;result.payload=entry.payload;break;
    case 'workspaceRemove':entry.found=false;entry.payload=null;break;
   }
  }
  const reply=()=>context.__nativeStorageResult({...result,requestId:command.requestId});
  if(holdWrite&&(command.action==='catalogWrite'||command.action==='workspaceWrite'||command.action==='orderWrite'||command.action==='parkedWrite'||command.action==='recoveryWrite')&&(command.key||'products')===holdKey)held=reply;else reply();
  return true;
 }}}};
 vm.createContext(context);vm.runInContext(adapter,context);vm.runInContext(cutover,context);
 vm.runInContext(journalConstants+'\n'+journalFunctions,context);vm.runInContext(backup,context);
 return {context,data,room,calls,timers,fail,release:()=>held?.(),get held(){return !!held;}};
}
async function flushUntil(predicate){for(let i=0;i<100&&!predicate();i++)await Promise.resolve();assert.ok(predicate());}

function fullBackup(rows){
 const doc={version:13,products:rows,employees:[],shifts:[],orders:[],receivingDraft:null,inventoryDraft:null,
  layout:{categoryOrder:['Напитки'],tiles:[{type:'product',id:'p1'}]},printerSettings:{printers:[],posNotifications:{}},
  currentOrderSession:{items:[]},theme:'light',demandOverload:false,operationalRevision:0};
 for(const key of ['parked','receivings','suppliers','purchaseOrders','discounts','hallTables','bookings','inventoryHistory','deliveryRates','operationalOutbox','webEvents'])doc[key]=[];
 for(const key of ['inventoryConfig','company','posNavigation','printer','telegram','network','webOrderAcceptances'])doc[key]={};
 return doc;
}

test('cold startup imports products once and preserves all JSON fields',async()=>{
 const rows=products('Кофе ☕'),h=host({data:new Map([['products',rows]])});
 assert.deepEqual(clone(await h.context.MPosCore.Storage.get('products',[])),rows);
 assert.equal(h.calls.filter(x=>x==='native:catalogInitialize').length,1);
 assert.equal(h.room.payload,JSON.stringify(rows));
 assert.equal(h.context.MPosCore.CatalogCutover.activeSource(),'room');
});

test('existing native authority survives restart and never imports stale compatibility cache',async()=>{
 const room={initialized:true,found:true,payload:JSON.stringify(products('Native'))};
 const h=host({room,data:new Map([['products',products('Stale cache')]])});
 assert.equal((await h.context.MPosCore.Storage.get('products',[]))[0].name,'Native');
 assert.equal(h.calls.includes('legacy-get:products'),false);
 assert.equal(h.calls.includes('native:catalogInitialize'),false);
});

test('concurrent first reads share initialization and synchronous native callbacks are not lost',async()=>{
 const h=host({data:new Map([['products',products('Seed')]])});
 const values=await Promise.all([h.context.MPosCore.Storage.get('products',[]),h.context.MPosCore.Catalog.getNativeSnapshot()]);
 assert.equal(values[0][0].name,'Seed');assert.equal(values[1].products[0].name,'Seed');
 assert.equal(h.calls.filter(x=>x==='native:catalogInitialize').length,1);
});

test('native write acknowledgement precedes compatibility cache and captures original payload',async()=>{
 const h=host({holdWrite:true});const rows=products('Captured');
 const saving=h.context.MPosCore.Storage.set('products',rows);rows[0].name='Mutated';
 await flushUntil(()=>h.held);
 assert.equal(h.calls.includes('legacy-set:products'),false);
 h.release();await saving;
 assert.equal(JSON.parse(h.room.payload)[0].name,'Captured');
 assert.equal(h.data.get('products')[0].name,'Captured');
});

test('native write failure rejects save without changing compatibility cache',async()=>{
 const original=products('Original');const h=host({data:new Map([['products',original]])});
 await h.context.MPosCore.Catalog.initialize();h.fail.add('catalogWrite');
 await assert.rejects(h.context.MPosCore.Storage.set('products',products('Rejected')),/synthetic native failure/);
 assert.equal(JSON.parse(h.room.payload)[0].name,'Original');assert.equal(h.data.get('products')[0].name,'Original');
});

test('secondary cache failure cannot undo acknowledged native commit or restart',async()=>{
 const h=host({cacheFails:true});await h.context.MPosCore.Storage.set('products',products('Durable'));
 assert.equal(h.context.MPosCore.Storage.describe().catalogCacheFailures,1);
 const restarted=host({room:h.room,data:h.data});
 assert.equal((await restarted.context.MPosCore.Storage.get('products',[]))[0].name,'Durable');
});

test('failed initialization can retry and read failure invokes original error callback rather than stale cache',async()=>{
 const h=host({data:new Map([['products',products('Seed')]]),fail:new Set(['catalogInitialize'])});
 await assert.rejects(h.context.MPosCore.Catalog.initialize(),/synthetic native failure/);
 h.fail.clear();await h.context.MPosCore.Catalog.initialize();h.fail.add('catalogRead');
 let errors=0;assert.equal(await h.context.MPosCore.Storage.get('products','fallback',()=>errors++),'fallback');assert.equal(errors,1);
});

test('absence null and removal preserve fallback semantics without resurrecting a legacy cache',async()=>{
 const h=host();assert.equal(await h.context.MPosCore.Storage.get('products','missing'),'missing');
 await h.context.MPosCore.Storage.set('products',null);assert.equal(await h.context.MPosCore.Storage.get('products','missing'),null);
 await h.context.MPosCore.Storage.remove('products');assert.equal(await h.context.MPosCore.Storage.get('products','missing'),'missing');
 const restart=host({room:h.room,data:new Map([['products',products('Stale')]])});
 assert.equal(await restart.context.MPosCore.Storage.get('products','missing'),'missing');
});

test('startup shadow mirroring excludes authoritative products and suppliers keys',async()=>{
 const h=host({data:new Map([['products',products('Old')],['suppliers',[]]])});
 const startup=[...h.timers.values()].find(timer=>timer.delay===0);startup.fn();
 assert.equal(h.calls.filter(x=>x==='native:put').length,0);
 assert.equal(h.room.initialized,false);
});

test('existing payment journal retains failed native product write and recovers before later business writes',async()=>{
 const h=host();await h.context.MPosCore.Catalog.initialize();h.fail.add('catalogWrite');
 const next=products('Stock after sale');
 await assert.rejects(h.context.commitCriticalStorage('payment',{products:next,orders:[{id:'paid'}]}),/восстановления/);
 assert.equal(h.data.has('orders'),false);assert.equal(h.data.get('criticalStorageJournal').type,'payment');
 const restarted=host({room:h.room,data:h.data});
 assert.equal(await restarted.context.recoverCriticalStorageJournal(),true);
 assert.equal(JSON.parse(h.room.payload)[0].name,'Stock after sale');assert.equal(JSON.parse(h.room.workspace.orders.payload)[0].id,'paid');
 assert.equal(h.data.get('criticalStorageJournal'),null);
});

test('lost acknowledgement is reported as uncertain and existing journal can replay committed native data',async()=>{
 const h=host({holdWrite:true});await h.context.MPosCore.Catalog.initialize();
 const saving=h.context.commitCriticalStorage('payment',{products:products('Committed before timeout'),orders:[{id:'paid'}]});
 await flushUntil(()=>h.held);
 const timeout=[...h.timers.values()].find(timer=>timer.delay===15000);timeout.fn();
 await assert.rejects(saving,/commit status is uncertain/);h.release();
 assert.equal(h.data.has('orders'),false);
 const restarted=host({room:h.room,data:h.data});assert.equal(await restarted.context.recoverCriticalStorageJournal(),true);
 assert.equal(JSON.parse(h.room.workspace.orders.payload)[0].id,'paid');assert.equal(h.data.get('criticalStorageJournal'),null);
});

test('actual v13 validator and restore replace authoritative catalog through existing journal',async()=>{
 const h=host({data:new Map([['products',products('Old')]])});
 const rows=products('Imported v13');await h.context.applyBackupData(fullBackup(rows));
 assert.equal(JSON.parse(h.room.payload)[0].name,'Imported v13');
 assert.equal(h.context.state.products[0].name,'Imported v13');assert.equal(h.data.get('criticalStorageJournal'),null);
 assert.ok(h.calls.indexOf('native:catalogWrite')<h.calls.indexOf('restore-printers'));
 const restarted=host({room:h.room,data:h.data});assert.equal((await restarted.context.MPosCore.Storage.get('products',[]))[0].name,'Imported v13');
});

test('layout and navigation migrate independently and survive stale caches on restart',async()=>{
 const layout={categoryOrder:['Кофе','Еда'],categoryColors:{'Кофе':'#123456'},categoryOnlineOrder:{'Кофе':false},tiles:[{type:'product',id:'p1'}],custom:null};
 const navigation={folders:[{id:'folder',name:'Кофе ☕'}],extension:{zero:0,enabled:false}};
 const h=host({data:new Map([['layout',layout],['posNavigation',navigation]])});
 assert.deepEqual(clone(await h.context.MPosCore.Storage.get('layout',{})),layout);
 assert.deepEqual(clone(await h.context.MPosCore.Storage.get('posNavigation',{})),navigation);
 h.data.set('layout',{stale:true});h.data.set('posNavigation',{stale:true});
 const restarted=host({room:h.room,data:h.data});
 assert.deepEqual(clone(await restarted.context.MPosCore.Storage.get('layout',{})),layout);
 assert.deepEqual(clone(await restarted.context.MPosCore.Storage.get('posNavigation',{})),navigation);
 assert.equal(restarted.calls.includes('legacy-get:layout'),false);
 assert.equal(restarted.context.MPosCore.Storage.describe().workspaceSourceOfTruth,'room');
});

test('workspace initialization acknowledgements precede cache and capture submitted snapshot',async()=>{
 const h=host({holdWrite:true,holdKey:'layout'});const layout={categoryOrder:['Captured']};
 const saving=h.context.MPosCore.Storage.set('layout',layout);layout.categoryOrder.push('Mutated');
 await flushUntil(()=>h.held);assert.equal(h.data.has('layout'),false);h.release();await saving;
 assert.deepEqual(JSON.parse(h.room.workspace.layout.payload),{categoryOrder:['Captured']});
 assert.deepEqual(h.data.get('layout'),{categoryOrder:['Captured']});
});

test('workspace native failure retains old data and cache failure does not undo durable write',async()=>{
 const h=host({data:new Map([['layout',{old:true}]])});await h.context.MPosCore.Storage.get('layout',{});
 h.fail.add('workspaceWrite');await assert.rejects(h.context.MPosCore.Storage.set('layout',{rejected:true}),/synthetic native failure/);
 assert.deepEqual(JSON.parse(h.room.workspace.layout.payload),{old:true});assert.deepEqual(h.data.get('layout'),{old:true});
 const failedCache=host({room:h.room,data:h.data,cacheFails:true});await failedCache.context.MPosCore.Storage.set('layout',{durable:true});
 assert.deepEqual(JSON.parse(h.room.workspace.layout.payload),{durable:true});
 assert.equal(failedCache.context.MPosCore.Storage.describe().nativeCacheFailuresByKey.layout,1);
 assert.equal(failedCache.context.MPosCore.Storage.describe().catalogCacheFailures,0);
});

test('workspace absence null and removal remain distinct from stale legacy cache',async()=>{
 const h=host();assert.equal(await h.context.MPosCore.Storage.get('layout','missing'),'missing');
 await h.context.MPosCore.Storage.set('layout',null);assert.equal(await h.context.MPosCore.Storage.get('layout','missing'),null);
 await h.context.MPosCore.Storage.remove('layout');
 const restarted=host({room:h.room,data:new Map([['layout',{stale:true}]])});
 assert.equal(await restarted.context.MPosCore.Storage.get('layout','missing'),'missing');
});

test('v13 restore persists products layout and navigation in native storage and preserves other domains',async()=>{
 const h=host();const backup=fullBackup(products('Backup'));
 backup.layout.categoryColors={'Напитки':'#123456'};backup.posNavigation={folders:[{id:'folder',name:'Folder'}]};
 await h.context.applyBackupData(backup);
 assert.deepEqual(JSON.parse(h.room.workspace.layout.payload).categoryColors,backup.layout.categoryColors);
 assert.deepEqual(JSON.parse(h.room.workspace.posNavigation.payload),backup.posNavigation);
 assert.deepEqual(JSON.parse(h.room.workspace.orders.payload),[]);assert.equal(h.data.get('criticalStorageJournal'),null);
 const restarted=host({room:h.room,data:h.data});
 assert.deepEqual(clone(await restarted.context.MPosCore.Storage.get('posNavigation',{})),backup.posNavigation);
});

test('failed workspace stage of actual backup journal replays after restart',async()=>{
 const h=host({fail:new Set(['workspaceWrite'])});const backup=fullBackup(products('Backup'));
 await assert.rejects(h.context.applyBackupData(backup),/восстановления/);
 assert.equal(h.data.get('criticalStorageJournal').type,'backup-import');
 const restarted=host({room:h.room,data:h.data});assert.equal(await restarted.context.recoverCriticalStorageJournal(),true);
 assert.deepEqual(JSON.parse(h.room.workspace.layout.payload).categoryOrder,['Напитки']);
 assert.equal(h.data.get('criticalStorageJournal'),null);
});


test('employees migrate once including roles and credentials, native failure preserves committed data',async()=>{
 const employees=[{id:'e1',name:'Кассир',role:'employee',phone:'fixture',pinHash:'synthetic-hash',custom:{permissions:['sell']}}];
 const h=host({data:new Map([['employees',employees]])});
 assert.deepEqual(clone(await h.context.MPosCore.Storage.get('employees',[])),employees);
 assert.equal(h.room.workspace.employees.payload,JSON.stringify(employees));
 h.data.set('employees',[{id:'stale'}]);
 assert.deepEqual(clone(await h.context.MPosCore.Storage.get('employees',[])),employees);
 h.fail.add('employeeWrite');
 await assert.rejects(h.context.MPosCore.Storage.set('employees',[]));
 assert.deepEqual(clone(await h.context.MPosCore.Storage.get('employees',[])),employees);
});

test('backup v13 employee write failure leaves replayable journal and restores native employees',async()=>{
 const h=host(),doc=fullBackup(products('Backup'));
 doc.employees=[{id:'e1',name:'Администратор',role:'admin',custom:{preserved:true}}];
 h.fail.add('employeeWrite');
 await assert.rejects(vm.runInContext('applyBackupData('+JSON.stringify(doc)+')',h.context));
 assert.ok(h.data.has('criticalStorageJournal'));
 h.fail.delete('employeeWrite');
 await vm.runInContext('recoverCriticalStorageJournal()',h.context);
 assert.deepEqual(JSON.parse(h.room.workspace.employees.payload),doc.employees);
 assert.equal(h.data.get('criticalStorageJournal'),null);
});


test('shifts preserve cash movements and backup replay after a native write failure',async()=>{
 const h=host(),doc=fullBackup(products('Backup'));
 doc.shifts=[{id:'s1',status:'closed',openingCash:12.35,countedCash:25.75,cashMovements:[{id:'m1',amount:0.25,type:'expense',note:'fixture'}],report:{extra:true}}];
 h.fail.add('shiftWrite');
 await assert.rejects(h.context.applyBackupData(doc));
 h.fail.delete('shiftWrite');
 assert.equal(await h.context.recoverCriticalStorageJournal(),true);
 assert.deepEqual(JSON.parse(h.room.workspace.shifts.payload),doc.shifts);
 const restarted=host({room:h.room,data:new Map([['shifts',[]]])});
 assert.deepEqual(clone(await restarted.context.MPosCore.Storage.get('shifts',[])),doc.shifts);
});

const paidReceipt=()=>({id:'o1',shiftId:'s1',receiptNumber:42,employeeId:'e1',method:'split',total:12.35,
 items:[{productId:'p1',name:'Кофе',qty:1,price:12.35,custom:true}],
 payments:[{method:'cash',amount:5,cashGiven:10,change:5},{method:'card',amount:7.35}],
 stockConsumption:{version:1,items:[{productId:'p1',qty:1}]},loyaltySync:{status:'pending'},custom:{preserved:true}});

test('paid receipt waits for native commit before updating cache and preserves split payments',async()=>{
 const h=host({holdWrite:true,holdKey:'orders'}),receipt=paidReceipt();
 const saving=h.context.MPosCore.Storage.set('orders',[receipt]);receipt.total=999;
 await flushUntil(()=>h.held);
 assert.equal(h.calls.includes('legacy-set:orders'),false);
 h.release();await saving;
 assert.equal(JSON.parse(h.room.workspace.orders.payload)[0].total,12.35);
 assert.equal(h.calls.includes('legacy-set:orders'),false);assert.equal(h.calls.includes('legacy-remove:orders'),true);
 assert.deepEqual(JSON.parse(h.room.workspace.orders.payload)[0].payments,paidReceipt().payments);
});

test('failed native receipt persistence leaves payment journal and replays all business documents',async()=>{
 const h=host(),receipt=paidReceipt();h.fail.add('orderWrite');
 const writes={products:products('After sale'),orders:[receipt],shifts:[{id:'s1',status:'open'}]};
 await assert.rejects(h.context.commitCriticalStorage('payment',writes));
 assert.equal(h.data.get('criticalStorageJournal').type,'payment');
 assert.equal(h.data.has('orders'),false);assert.equal(h.data.has('shifts'),false);
 const restarted=host({room:h.room,data:h.data});
 assert.equal(await restarted.context.recoverCriticalStorageJournal(),true);
 assert.deepEqual(JSON.parse(h.room.workspace.orders.payload),[receipt]);
 assert.deepEqual(JSON.parse(h.room.workspace.shifts.payload),writes.shifts);
 assert.equal(h.data.get('criticalStorageJournal'),null);
});

test('actual v13 backup restores returned receipts without losing original sale and payment fields',async()=>{
 const h=host({cacheFails:true}),doc=fullBackup(products('Backup'));
 doc.orders=[{...paidReceipt(),returnedAt:2000,returnedShiftId:'s2',returnAmount:12.35,loyaltyReversal:{status:'pending'}}];
 await h.context.applyBackupData(doc);
 assert.deepEqual(JSON.parse(h.room.workspace.orders.payload),doc.orders);
 const restarted=host({room:h.room,data:new Map([['orders',[]]])});
 assert.deepEqual(clone(await restarted.context.MPosCore.Storage.get('orders',[])),doc.orders);
 assert.ok(h.context.MPosCore.Storage.describe().nativeCacheFailuresByKey.orders>0);
});

test('actual full return commits native receipt, stock and shift; repeated return cannot refund again',async()=>{
 const h=host();
 Object.assign(h.context,{criticalOperationBusy:false,currentShift:()=>h.context.state.shifts[0],
  shiftTotals:()=>({}),cashDrawerBalance:()=>100,roundStockQty:v=>Math.round(v*1000)/1000,
  flash:message=>h.calls.push('flash:'+message),publishAvailability:()=>h.calls.push('availability'),
  closeModal(){},showModal(){},render(){},fullMoney:String,escapeAttr:String});
 h.context.state={products:[{id:'p1',type:'simple',stock:4}],orders:[paidReceipt()],shifts:[{id:'s1',status:'open',cashMovements:[]}]};
 vm.runInContext(fs.readFileSync(path.join(root,'Web/js/features/receipts.js'),'utf8'),h.context);
 await h.context.processFullReturn('o1');
 const returned=JSON.parse(h.room.workspace.orders.payload)[0];
 assert.equal(returned.total,12.35);assert.equal(returned.returnAmount,12.35);assert.ok(returned.returnedAt>0);
 assert.equal(JSON.parse(h.room.payload)[0].stock,5);
 assert.equal(JSON.parse(h.room.workspace.shifts.payload)[0].cashMovements[0].amount,5);
 const saves=h.calls.filter(v=>v==='native:orderWrite').length;
 await h.context.processFullReturn('o1');
 assert.equal(h.calls.filter(v=>v==='native:orderWrite').length,saves);
 assert.ok(h.calls.includes('flash:Этот чек уже возвращён'));
});

const parkedReceipt=()=>({id:'parked1',items:[{productId:'p1',name:'Кофе',qty:1,price:12.35,modifiers:[{fixture:true}]}],
 total:14.35,subtotal:12.35,orderLabel:'Доставка 1',orderType:'Доставка',deliveryFee:2,deliveryTariffSelected:true,
 customer:{name:'Клиент',phone:'fixture',address:'fixture'},comment:'Не звонить',source:'web',webOrderId:'web1',webOrderStatus:'ready',
 kitchenPrinted:true,printedItems:[{fixture:true}],custom:{preserved:true}});
function setupParked(h){
 Object.assign(h.context,{criticalOperationBusy:false,flash:message=>h.calls.push('flash:'+message),closeModal(){},render(){}});
 vm.runInContext(fs.readFileSync(path.join(root,'Web/js/features/parked-orders.js'),'utf8'),h.context);
 h.context.state={cart:[],parked:[parkedReceipt()]};
}

test('actual parked resume preserves delivery/customer/printing data and removes persisted parked record',async()=>{
 const h=host();setupParked(h);
 await h.context.MPosCore.Storage.set('parked',h.context.state.parked);
 assert.equal(await h.context.resumeParked('parked1'),true);
 assert.deepEqual(JSON.parse(h.room.workspace.parked.payload),[]);
 const session=h.data.get('currentOrderSession');
 assert.deepEqual(session.items,parkedReceipt().items);assert.deepEqual(session.customer,parkedReceipt().customer);
 assert.equal(session.deliveryFee,2);assert.equal(session.webOrderId,'web1');
 assert.equal(session.kitchenPrinted,true);assert.deepEqual(session.printedItems,parkedReceipt().printedItems);
 assert.equal(h.context.state.cart[0].modifiers[0].fixture,true);
});

test('failed native removal during actual parked resume retains UI order and recovery journal',async()=>{
 const h=host();setupParked(h);
 await h.context.MPosCore.Storage.set('parked',h.context.state.parked);h.fail.add('parkedWrite');
 assert.equal(await h.context.resumeParked('parked1'),false);
 assert.equal(h.context.state.parked.length,1);assert.equal(h.context.state.cart.length,0);
 assert.equal(h.data.get('criticalStorageJournal').type,'resume-parked');
 assert.deepEqual(JSON.parse(h.room.workspace.parked.payload),[parkedReceipt()]);
 const restarted=host({room:h.room,data:h.data});
 assert.equal(await restarted.context.recoverCriticalStorageJournal(),true);
 assert.deepEqual(JSON.parse(h.room.workspace.parked.payload),[]);
 assert.deepEqual(h.data.get('currentOrderSession').items,parkedReceipt().items);
 assert.equal(h.data.get('criticalStorageJournal'),null);
});

test('v13 parked import and native restart retain exact unprojected fields despite cache failure',async()=>{
 const h=host({cacheFails:true}),doc=fullBackup(products('Backup'));doc.parked=[parkedReceipt()];
 await h.context.applyBackupData(doc);
 const restarted=host({room:h.room,data:new Map([['parked',[]]])});
 assert.deepEqual(clone(await restarted.context.MPosCore.Storage.get('parked',[])),doc.parked);
 assert.ok(h.context.MPosCore.Storage.describe().nativeCacheFailuresByKey.parked>0);
});

test('actual parking cannot clear cart before native commit; delayed save preserves snapshot',async()=>{
 const h=host({holdWrite:true,holdKey:'parked'});setupParked(h);
 const source=parkedReceipt();h.context.state={...source,cart:clone(source.items),parked:[],employees:[],orderComment:source.comment};
 Object.assign(h.context,{currentShift:()=>null,getProduct:()=>({category:'Напитки'}),cartTotal:()=>14.35,cartSubtotal:()=>12.35,
  kitchenPrintDelta:()=>[],resetCurrentOrderState:()=>{h.context.state.cart=[];}});
 const saving=h.context.parkOrderNow();await flushUntil(()=>h.held);
 assert.equal(h.context.state.cart.length,1);assert.equal(h.context.state.parked.length,0);
 assert.equal(h.data.has('currentOrderSession'),false);
 h.release();assert.equal(await saving,true);
 assert.equal(h.context.state.cart.length,0);assert.equal(h.context.state.parked.length,1);
 const saved=JSON.parse(h.room.workspace.parked.payload)[0];
 assert.deepEqual(saved.items[0].modifiers,source.items[0].modifiers);assert.equal(saved.deliveryFee,2);
});

test('native journal persists before payment writes; failed journal creation prevents every business write',async()=>{
 const h=host({fail:new Set(['recoveryWrite'])});
 await assert.rejects(h.context.commitCriticalStorage('payment',{products:products('Sale'),orders:[paidReceipt()]}),/начать безопасное/);
 assert.equal(h.calls.includes('native:catalogWrite'),false);assert.equal(h.calls.includes('native:orderWrite'),false);
 assert.equal(h.data.has('products'),false);assert.equal(h.data.has('orders'),false);
});

test('failed native journal read blocks recovery and new payments rather than treating it as empty',async()=>{
 const h=host({fail:new Set(['recoveryRead'])});
 assert.equal(await h.context.recoverCriticalStorageJournal(),false);
 assert.equal(h.context.criticalStorageRecoveryPending,true);
 await assert.rejects(h.context.commitCriticalStorage('payment',{orders:[paidReceipt()]}),/Незавершённая операция/);
 assert.equal(h.calls.includes('native:orderWrite'),false);
});

test('failed journal-clear commit replays committed payment from native journal without stale cache',async()=>{
 const h=host(),writes={products:products('Committed'),orders:[paidReceipt()]};
 h.fail.add('recoveryWrite:criticalStorageJournal:null');
 await assert.rejects(h.context.commitCriticalStorage('payment',writes),/восстановления/);
 assert.equal(JSON.parse(h.room.workspace.criticalStorageJournal.payload).type,'payment');
 const restarted=host({room:h.room,data:new Map()});
 assert.equal(await restarted.context.recoverCriticalStorageJournal(),true);
 assert.deepEqual(JSON.parse(h.room.workspace.orders.payload),writes.orders);
 assert.equal(h.room.workspace.criticalStorageJournal.payload,'null');
});

test('current cart and native journal remain recoverable despite all compatibility cache writes failing',async()=>{
 const h=host({cacheFails:true});const session={items:paidReceipt().items,customer:{name:'Клиент'},kitchenPrinted:true,printedItems:[{fixture:true}],splitPayment:{fixture:true}};
 await h.context.MPosCore.Storage.set('currentOrderSession',session);
 const writes={orders:[paidReceipt()],currentOrderSession:{items:[]}};h.fail.add('orderWrite');
 await assert.rejects(h.context.commitCriticalStorage('payment',writes));
 const restarted=host({room:h.room,data:new Map()});
 assert.deepEqual(clone(await restarted.context.MPosCore.Storage.get('currentOrderSession',null)),session);
 assert.equal(await restarted.context.recoverCriticalStorageJournal(),true);
 assert.deepEqual(JSON.parse(h.room.workspace.currentOrderSession.payload),{items:[]});
 assert.equal(h.room.workspace.criticalStorageJournal.payload,'null');
});
