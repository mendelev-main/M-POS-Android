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

test('native failure cannot turn successful local storage write into failed payment persistence',async()=>{
 const h=host({nativeFails:true});await h.context.MPosCore.Storage.set('orders',[{id:'paid-1'}]);
 assert.equal(h.calls[0],'local');assert.equal(h.calls[1].action,'put');
 assert.equal(h.data.get('orders')[0].id,'paid-1');
 assert.equal(h.context.MPosCore.Storage.describe().nativeShadowAuthoritative,false);
});

test('failed local persistence is never mirrored as a committed operation',async()=>{
 const h=host({localFails:true});
 await assert.rejects(h.context.MPosCore.Storage.set('orders',[]),/local failure/);
 assert.deepEqual(h.calls,['local']);assert.equal(h.data.has('orders'),false);
});

test('catalog snapshot refuses a consistent but behind native shadow',async()=>{
 const h=host();const snapshot=h.context.MPosCore.Catalog.getNativeSnapshot();
 const request=h.calls.at(-1);
 h.context.__nativeStorageResult({requestId:request.requestId,ok:true,shadowCaughtUp:false,products:[]});
 await assert.rejects(snapshot,/not caught up/);
});

test('comparison cannot report healthy parity while pending shadow changes exist',async()=>{
 const h=host();const comparison=h.context.MPosCore.CatalogCutover.compare();
 const request=h.calls.at(-1);
 h.context.__nativeStorageResult({requestId:request.requestId,ok:true,matches:true,shadowCaughtUp:false});
 const result=await comparison;assert.equal(result.ok,false);assert.equal(result.matches,false);
 assert.equal(h.context.MPosCore.CatalogCutover.activeSource(),'legacy');
 assert.throws(()=>h.context.MPosCore.CatalogCutover.setMode('room'),/physical acceptance/);
});
