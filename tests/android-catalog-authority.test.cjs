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
  async set(key,value){calls.push('legacy-set:'+key);if(cacheFails&&['products','layout','posNavigation'].includes(key))throw new Error('cache disk failure');data.set(key,clone(value));},
  remove(key){calls.push('legacy-remove:'+key);data.delete(key);},
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
  if(fail.has(command.action))result={ok:false,message:'synthetic native failure'};
  else switch(command.action){
   case 'catalogStatus':result.initialized=room.initialized;break;
   case 'catalogInitialize':if(!room.initialized){room.initialized=true;room.found=typeof command.payload==='string';room.payload=room.found?command.payload:null;}break;
   case 'catalogWrite':room.found=true;room.payload=command.payload;break;
   case 'catalogRead':result.found=room.found;result.payload=room.payload;break;
   case 'catalogRemove':room.found=false;room.payload=null;break;
   case 'catalogParity':result={ok:true,matches:true,shadowCaughtUp:true};break;
  }
  if(command.action.startsWith('workspace')&&!fail.has(command.action)){
   const entry=room.workspace[command.key]??={initialized:false,found:false,payload:null};
   switch(command.action){
    case 'workspaceStatus':result.initialized=entry.initialized;break;
    case 'workspaceInitialize':if(!entry.initialized){entry.initialized=true;entry.found=typeof command.payload==='string';entry.payload=entry.found?command.payload:null;}break;
    case 'workspaceWrite':entry.found=true;entry.payload=command.payload;break;
    case 'workspaceRead':result.found=entry.found;result.payload=entry.payload;break;
    case 'workspaceRemove':entry.found=false;entry.payload=null;break;
   }
  }
  const reply=()=>context.__nativeStorageResult({...result,requestId:command.requestId});
  if(holdWrite&&(command.action==='catalogWrite'||command.action==='workspaceWrite')&&(command.key||'products')===holdKey)held=reply;else reply();
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

test('startup shadow mirroring excludes the authoritative products key',async()=>{
 const h=host({data:new Map([['products',products('Old')],['employees',[]]])});
 const startup=[...h.timers.values()].find(timer=>timer.delay===0);startup.fn();
 assert.equal(h.calls.filter(x=>x==='native:put').length,1);
 assert.equal(h.room.initialized,false);
});

test('existing payment journal retains failed native product write and recovers before later business writes',async()=>{
 const h=host();await h.context.MPosCore.Catalog.initialize();h.fail.add('catalogWrite');
 const next=products('Stock after sale');
 await assert.rejects(h.context.commitCriticalStorage('payment',{products:next,orders:[{id:'paid'}]}),/восстановления/);
 assert.equal(h.data.has('orders'),false);assert.equal(h.data.get('criticalStorageJournal').type,'payment');
 const restarted=host({room:h.room,data:h.data});
 assert.equal(await restarted.context.recoverCriticalStorageJournal(),true);
 assert.equal(JSON.parse(h.room.payload)[0].name,'Stock after sale');assert.equal(h.data.get('orders')[0].id,'paid');
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
 assert.equal(h.data.get('orders')[0].id,'paid');assert.equal(h.data.get('criticalStorageJournal'),null);
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
 assert.deepEqual(h.data.get('orders'),[]);assert.equal(h.data.get('criticalStorageJournal'),null);
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
