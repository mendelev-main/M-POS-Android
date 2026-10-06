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
