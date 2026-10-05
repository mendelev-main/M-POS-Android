const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.join(__dirname,'../app/src/main/assets/pos');
const source=fs.readFileSync(path.join(root,'Web/js/features/availability.js'),'utf8');
const adapter=fs.readFileSync(path.join(root,'native-availability.js'),'utf8');

function host({settings=new Map(),fetchResult=async()=>({ok:true})}={}){
 const requests=[],listeners={},writes=[];
 const data={products:[{id:'p1',stock:9}],network:{backendUrl:'https://backend.invalid',deviceKey:'test-key'},orders:[],webAvailabilityRevision:0};
 const context={
  document:{hidden:false,addEventListener(name,callback){(listeners[name]??=[]).push(callback);}},
  localStorage:{getItem:key=>settings.get(key)??null,setItem:(key,value)=>settings.set(key,value)},
  state:{loaded:true},storageBroken:false,availableStock:p=>p.stock,
  PrilavokCore:{Storage:{async get(key){return data[key];},async set(key,value){data[key]=value;writes.push(key);}}},
  fetch:async(url,options)=>{requests.push({url,options});return fetchResult();},
  addEventListener(name,callback){(listeners[name]??=[]).push(callback);},
  AbortController,setTimeout,clearTimeout,console,
 };
 context.window=context;
 vm.createContext(context);vm.runInContext(source,context);vm.runInContext(adapter,context);
 return {context,requests,listeners,writes,data,settings,fire:name=>(listeners[name]||[]).forEach(fn=>fn())};
}

test('startup, restored connectivity and foreground never start availability requests',async()=>{
 const h=host();h.context.startAvailabilityRecovery();h.context.startAvailabilityRecovery();
 h.fire('online');h.context.onAvailabilityAppState(true);h.fire('visibilitychange');
 await Promise.resolve();
 assert.equal(h.requests.length,0);
 assert.equal(h.listeners.visibilitychange.length,1);
 assert.equal(h.listeners.online,undefined);
});

test('failed payment snapshot waits for next payment and sends latest durable stock',async()=>{
 let succeeds=false;
 const h=host({fetchResult:async()=>({ok:succeeds})});
 assert.equal(await h.context.publishAvailability(['web-1']),false);
 assert.equal(h.requests.length,1);
 h.data.products[0].stock=8;
 assert.equal(await h.context.publishAvailability(),false);
 h.fire('online');h.context.onAvailabilityAppState(true);
 assert.equal(h.requests.length,1);
 succeeds=true;
 assert.equal(await h.context.publishAvailability([]),true);
 assert.equal(h.requests.length,2);
 const body=JSON.parse(h.requests[1].options.body);
 assert.equal(body.items[0].quantity,8);
 assert.ok(body.settledWebOrderIds.includes('web-1'));
 assert.deepEqual(h.writes,['webAvailabilityRevision','webAvailabilityRevision']);
});

test('failed or interrupted attempt remains blocked after process restart',async()=>{
 const settings=new Map();
 const first=host({settings,fetchResult:async()=>{throw new Error('offline');}});
 await first.context.publishAvailability([]);
 const second=host({settings});
 second.context.startAvailabilityRecovery();second.context.onAvailabilityAppState(true);
 assert.equal(await second.context.publishAvailability(),false);
 assert.equal(second.requests.length,0);
 assert.equal(await second.context.publishAvailability([]),true);
 assert.equal(second.requests.length,1);
});

test('stock mutation queued during a failing request does not create an immediate retry',async()=>{
 let complete;
 const h=host({fetchResult:()=>new Promise(resolve=>{complete=resolve;})});
 const payment=h.context.publishAvailability([]);
 while(!complete)await Promise.resolve();
 h.context.publishAvailability();
 complete({ok:false});
 assert.equal(await payment,false);
 assert.equal(h.requests.length,1);
});

test('successful stock-change publication remains supported and background aborts only network',async()=>{
 const h=host();
 assert.equal(await h.context.publishAvailability(),true);
 assert.equal(h.requests.length,1);
 h.context.onAvailabilityAppState(false);
 assert.equal(h.context._availabilityAppActive,false);
 assert.equal(h.data.products[0].stock,9);
});

test('explicit-array compatibility trigger occurs only in the post-commit payment path',()=>{
 const payment=fs.readFileSync(path.join(root,'Web/js/features/payment.js'),'utf8');
 const trigger=payment.indexOf('void publishAvailability(order.webOrderId?[order.webOrderId]:[])');
 assert.ok(trigger>payment.indexOf("await commitCriticalStorage('payment'"));
 assert.ok(trigger>payment.indexOf('state.products=nextProducts;state.orders=nextOrders;state.shifts=nextShifts;'));
 const files=[path.join(root,'pos.html'),...fs.readdirSync(path.join(root,'Web/js/features')).filter(f=>f.endsWith('.js')&&f!=='availability.js').map(f=>path.join(root,'Web/js/features',f))];
 for(const file of files){
  const text=fs.readFileSync(file,'utf8');
  for(const match of text.matchAll(/publishAvailability\(([^\n;]*)\)/g)){
   if(match[1].trim())assert.equal(path.basename(file),'payment.js','Unexpected argument-bearing trigger: '+file);
  }
 }
});
