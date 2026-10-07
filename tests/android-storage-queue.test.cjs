const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.join(__dirname,'../app/src/main/assets/pos');
const adapter=fs.readFileSync(path.join(root,'native-storage-shadow.js'),'utf8');
const cutover=fs.readFileSync(path.join(root,'native-catalog-cutover.js'),'utf8');

function host({localFails=false,nativeFails=false}={}){
 const calls=[],data=new Map(),timers=new Map();let timerId=0;
 const legacy={
  async get(key,fallback){return data.has(key)?data.get(key):fallback;},
  async set(key,value){calls.push('local');if(localFails)throw new Error('local failure');data.set(key,value);},
  remove:key=>data.delete(key),describe:()=>({mode:'fixture',sourceOfTruth:'local-pos'})
 };
 const context={PrilavokCore:{Storage:legacy},
  webkit:{messageHandlers:{storage:{postMessage(payload){calls.push(payload);if(nativeFails)throw new Error('native failure');}}}},
  setTimeout(fn){timers.set(++timerId,fn);return timerId;},clearTimeout:id=>timers.delete(id),console:{error(){}}
 };
 context.window=context;vm.createContext(context);vm.runInContext(adapter,context);vm.runInContext(cutover,context);
 return {context,calls,data};
}

test('native failure cannot turn successful local storage write into failed supplier persistence',async()=>{
 const h=host({nativeFails:true});await h.context.MPosCore.Storage.set('suppliers',[{id:'supplier-1'}]);
 assert.equal(h.calls[0],'local');assert.equal(h.calls[1].action,'put');
 assert.equal(h.data.get('suppliers')[0].id,'supplier-1');
 assert.equal(h.context.MPosCore.Storage.describe().nativeShadowAuthoritative,false);
});

test('failed local persistence is never mirrored as a committed operation',async()=>{
 const h=host({localFails:true});
 await assert.rejects(h.context.MPosCore.Storage.set('suppliers',[]),/local failure/);
 assert.deepEqual(h.calls,['local']);assert.equal(h.data.has('suppliers'),false);
});

test('comparison cannot report healthy parity while pending shadow changes exist',async()=>{
 const h=host();const comparison=h.context.MPosCore.CatalogCutover.compare();
 const request=h.calls.at(-1);
 h.context.__nativeStorageResult({requestId:request.requestId,ok:true,matches:true,shadowCaughtUp:false});
 const result=await comparison;assert.equal(result.ok,false);assert.equal(result.matches,false);
 assert.equal(h.context.MPosCore.CatalogCutover.activeSource(),'room');
 assert.equal(h.context.MPosCore.CatalogCutover.setMode('room'),'room');
 assert.throws(()=>h.context.MPosCore.CatalogCutover.setMode('legacy'),/explicit code rollback/);
});

test('configured price request is correlated read-only and freezes inputs without storage initialization',async()=>{const h=host();const input={version:1,catalogPrice:10,modifiers:[{priceDelta:2,qty:3}]};const work=h.context.MPosCore.ConfiguredPrices.calculate(input);const request=h.calls.at(-1);assert.equal(request.action,'configuredPriceRead');input.modifiers[0].priceDelta=9;assert.equal(JSON.parse(request.payload).modifiers[0].priceDelta,2);assert.equal(h.calls.length,1);h.context.__nativeStorageResult({requestId:request.requestId,ok:true,authoritative:true,price:12,basePrice:10,manualPrice:false});assert.equal((await work).price,12);assert.equal(h.data.size,0);});
test('native stock preflight distinguishes business refusal from failed transport and sends only frozen cart input',async()=>{const h=host();const input={version:1,items:[{productId:'p',qty:2}]};const work=h.context.MPosCore.StockPreflight.check(input);const request=h.calls.at(-1);assert.equal(request.action,'stockPreflightRead');input.items[0].qty=3;assert.equal(JSON.parse(request.payload).items[0].qty,2);h.context.__nativeStorageResult({requestId:request.requestId,ok:true,authoritative:true,allowed:false,reason:'shortage'});assert.equal((await work).allowed,false);assert.equal(h.data.size,0);const failing=h.context.MPosCore.StockPreflight.check({version:1,items:[]});const second=h.calls.at(-1);h.context.__nativeStorageResult({requestId:second.requestId,ok:false});await assert.rejects(failing,/operation failed/);});
test('quantity read uses correlated immutable cart input without persisting or reserving stock',async()=>{const h=host();const input={version:1,items:[{productId:'p',qty:1}],targetIndex:0,matchingIndices:[0],delta:1};const work=h.context.MPosCore.CartQuantity.check(input);const request=h.calls.at(-1);assert.equal(request.action,'cartQuantityRead');input.delta=2;assert.equal(JSON.parse(request.payload).delta,1);h.context.__nativeStorageResult({requestId:request.requestId,ok:true,authoritative:true,allowed:true,remove:false,quantity:2});assert.equal((await work).quantity,2);assert.equal(h.data.size,0);});
test('cart totals read freezes quote input and requires authoritative correlated reply',async()=>{const h=host(),input={version:1,items:[{price:10,qty:2}],discounts:[],programs:[],redemptions:{}};const p=h.context.MPosCore.CartTotals.calculate(input),request=h.calls.at(-1);assert.equal(request.action,'cartTotalsRead');input.items[0].qty=7;assert.equal(JSON.parse(request.payload).items[0].qty,2);h.context.__nativeStorageResult({requestId:request.requestId,ok:true,authoritative:true,pricing:{total:20}});assert.equal((await p).pricing.total,20);assert.equal(h.data.size,0);const bad=h.context.MPosCore.CartTotals.calculate(input),second=h.calls.at(-1);h.context.__nativeStorageResult({requestId:second.requestId,ok:true,authoritative:false});await assert.rejects(bad);});

test('split count read freezes rows, requires authority and never stores a draft',async()=>{const h=host(),input={version:1,total:10.01,delta:1,parts:[{amount:5.01,paid:true},{amount:5,paid:false}]};const p=h.context.MPosCore.SplitCount.calculate(input),request=h.calls.at(-1);assert.equal(request.action,'splitCountRead');input.parts[0].amount=99;assert.equal(JSON.parse(request.payload).parts[0].amount,5.01);h.context.__nativeStorageResult({requestId:request.requestId,ok:true,authoritative:true,allowed:true,changed:true});assert.equal((await p).allowed,true);assert.equal(h.data.size,0);const bad=h.context.MPosCore.SplitCount.calculate(input),second=h.calls.at(-1);h.context.__nativeStorageResult({requestId:second.requestId,ok:false});await assert.rejects(bad);});

test('split amount read is correlated and immutable without storage writes',async()=>{const h=host(),input={version:1,total:10,index:1,raw:'3,00',parts:[{amount:5,paid:true},{amount:5,paid:false}]};const p=h.context.MPosCore.SplitAmountRead.calculate(input),request=h.calls.at(-1);assert.equal(request.action,'splitAmountRead');input.raw='8';assert.equal(JSON.parse(request.payload).raw,'3,00');h.context.__nativeStorageResult({requestId:request.requestId,ok:true,authoritative:true,changed:true});assert.equal((await p).changed,true);assert.equal(h.data.size,0);const bad=h.context.MPosCore.SplitAmountRead.calculate(input),second=h.calls.at(-1);h.context.__nativeStorageResult({requestId:second.requestId,ok:true,authoritative:false});await assert.rejects(bad);});

test('split recovery pure read is frozen and requires native authority',async()=>{const h=host(),input={version:1,operation:'normalize',total:10,parts:[{amount:5,paid:true},{amount:5,paid:false}]};const p=h.context.MPosCore.SplitRecoveryRead.calculate(input),request=h.calls.at(-1);assert.equal(request.action,'splitRecoveryRead');input.parts[0].paid=false;assert.equal(JSON.parse(request.payload).parts[0].paid,true);h.context.__nativeStorageResult({requestId:request.requestId,ok:true,authoritative:true,changed:true});assert.equal((await p).changed,true);assert.equal(h.data.size,0);});
test('session read waits for recovery preparation but preparation failure cannot discard its payload',async()=>{for(const fail of [false,true]){const h=host(),session={items:[],paymentDraft:{version:1,parts:[{paid:true}]}};let seen,resolve;h.context.MPosCore.SplitRecovery={prepare:async value=>{seen=value;if(fail)throw Error('synthetic validation failure');await new Promise(r=>resolve=r);}};const p=h.context.MPosCore.Storage.get('currentOrderSession',null);await Promise.resolve();const status=h.calls.at(-1);h.context.__nativeStorageResult({requestId:status.requestId,ok:true,initialized:true});for(let i=0;i<8&&h.calls.length<2;i++)await Promise.resolve();const read=h.calls.at(-1);assert.equal(read.action,'recoveryRead');h.context.__nativeStorageResult({requestId:read.requestId,ok:true,authoritative:true,found:true,payload:JSON.stringify(session)});for(let i=0;i<8&&!seen;i++)await Promise.resolve();assert.equal(seen.paymentDraft.parts[0].paid,true);if(!fail)resolve();assert.deepEqual(JSON.parse(JSON.stringify(await p)),session);assert.equal(h.data.size,0);}});

test('delivery command freezes state and requires an authoritative correlated read without writes',async()=>{const h=host(),input={version:1,operation:'select',amount:2,state:{orderType:'Доставка',fee:0,selected:false,rates:[{amount:2}]}};const p=h.context.MPosCore.DeliveryRead.calculate(input),request=h.calls.at(-1);assert.equal(request.action,'deliveryRead');input.amount=5;assert.equal(JSON.parse(request.payload).amount,2);h.context.__nativeStorageResult({requestId:request.requestId,ok:true,authoritative:true,changed:true,fee:2,selected:true});assert.equal((await p).fee,2);assert.equal(h.data.size,0);});

test('local order settings command freezes fields and requires authoritative reply without a storage write',async()=>{const h=host(),input={version:1,operation:'save',fields:{label:' A ',name:' N ',phone:' P ',address:' X '}};const p=h.context.MPosCore.OrderContextRead.calculate(input),request=h.calls.at(-1);assert.equal(request.action,'orderContextRead');input.fields.phone='different';assert.equal(JSON.parse(request.payload).fields.phone,' P ');h.context.__nativeStorageResult({requestId:request.requestId,ok:true,authoritative:true,orderLabel:'A',name:'N',phone:'P',address:'X'});assert.equal((await p).phone,'P');assert.equal(h.data.size,0);const bad=h.context.MPosCore.OrderContextRead.calculate(input),second=h.calls.at(-1);h.context.__nativeStorageResult({requestId:second.requestId,ok:true,authoritative:false});await assert.rejects(bad);});

test('parked transaction freezes candidate and requires a correlated authoritative commit response',async()=>{const h=host(),input={version:1,operation:'delete-parked',id:'p',expected:[{id:'p'}],writes:{parked:[]}};const p=h.context.MPosCore.ParkedOrders.commit(input),request=h.calls.at(-1);assert.equal(request.action,'parkedCommit');input.expected[0].id='changed';assert.equal(JSON.parse(request.payload).expected[0].id,'p');h.context.__nativeStorageResult({requestId:request.requestId,ok:true,authoritative:true});await p;assert.equal(h.data.size,0);});
test('catalog editor policy is a correlated read and freezes form input without writing',async()=>{const h=host(),input={version:1,operation:'product',editingId:'p',name:'Milk',type:'simple',components:[]};const p=h.context.MPosCore.CatalogEdit.calculate(input),request=h.calls.at(-1);assert.equal(request.action,'catalogEditRead');input.name='Changed';assert.equal(JSON.parse(request.payload).name,'Milk');h.context.__nativeStorageResult({requestId:request.requestId,ok:true,authoritative:true,allowed:false,message:'blocked'});assert.equal((await p).allowed,false);assert.equal(h.data.size,0);});
test('recipe editor read freezes inputs, returns business refusal and requires native authority',async()=>{const h=host(),input={version:1,operation:'recipe',draft:{id:'p',type:'composite',components:[{productId:'x',qty:1}]}};const p=h.context.MPosCore.RecipeEdit.calculate(input),request=h.calls.at(-1);assert.equal(request.action,'recipeEditRead');input.draft.components[0].qty=2;assert.equal(JSON.parse(request.payload).draft.components[0].qty,1);h.context.__nativeStorageResult({requestId:request.requestId,ok:true,authoritative:true,allowed:false,message:'cycle'});assert.equal((await p).allowed,false);assert.equal(h.data.size,0);});
test('navigation business read freezes input and cannot write compatible documents',async()=>{const h=host(),input={version:1,operation:'addTile',tiles:[],type:'product',id:'p'};const p=h.context.MPosCore.NavigationRead.calculate(input),request=h.calls.at(-1);assert.equal(request.action,'navigationRead');input.id='new';assert.equal(JSON.parse(request.payload).id,'p');h.context.__nativeStorageResult({requestId:request.requestId,ok:true,authoritative:true,allowed:true,changed:true,tiles:[]});await p;assert.equal(h.data.size,0);});
test('employee commit freezes expected data and requires authoritative correlated acknowledgement',async()=>{const h=host(),input={version:1,operation:'save',id:'e',expected:[{id:'e'}],next:[{id:'e'}]};const p=h.context.MPosCore.EmployeeCommands.commit(input),request=h.calls.at(-1);assert.equal(request.action,'employeeCommit');input.expected[0].id='changed';assert.equal(JSON.parse(request.payload).expected[0].id,'e');h.context.__nativeStorageResult({requestId:request.requestId,ok:true,authoritative:true});await p;assert.equal(h.data.size,0);const bad=h.context.MPosCore.EmployeeCommands.commit(input),second=h.calls.at(-1);h.context.__nativeStorageResult({requestId:second.requestId,ok:true,authoritative:false});await assert.rejects(bad);});
