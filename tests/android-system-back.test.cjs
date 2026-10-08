const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/pos/native-system-back.js','utf8');
function host(){const events=[],c={MPosCore:{},document:{querySelector:()=>c.modal,getElementById:id=>({firstElementChild:id==='warehouse-root'?c.warehouse:c.receiving})},cancelBackupImport:()=>events.push('import'),closeModal:()=>events.push('modal'),closeWarehousePage:()=>events.push('warehouse'),finishReceivingPage:()=>events.push('receiving')};c.window=c;vm.createContext(c);vm.runInContext(source,c);return{c,events,capture:()=>JSON.parse(c.MPosCore.SystemBack.capture())};}
test('snapshot reports typed presence only; native-selected action executes once',()=>{
 const h=host();h.c.modal={};h.c.warehouse={};h.c._pendingBackupImport={synthetic:true};const s=h.capture();
 assert.equal(s.pendingImport,true);assert.equal(s.modal,true);assert.equal(s.warehouse,true);assert.equal(s.receiving,false);assert.equal(s.stamp,undefined);
 assert.equal(h.c.MPosCore.SystemBack.apply({token:s.token,action:'CANCEL_IMPORT'}),true);assert.deepEqual(h.events,['import']);assert.equal(h.c.MPosCore.SystemBack.apply({token:s.token,action:'CANCEL_IMPORT'}),false);
});
test('replacement modal, warehouse, receiving or import blocks stale native action',()=>{
 for(const field of ['modal','warehouse','receiving','_pendingBackupImport']){const h=host();h.c[field]={};const s=h.capture();h.c[field]={};assert.equal(h.c.MPosCore.SystemBack.apply({token:s.token,action:'CLOSE_MODAL'}),false);assert.deepEqual(h.events,[]);}
});
test('new capture invalidates old token; unsupported action or explicit rollback has no effect',()=>{
 const h=host(),a=h.capture(),b=h.capture();assert.equal(h.c.MPosCore.SystemBack.apply({token:a.token,action:'BACKGROUND'}),false);
 assert.equal(h.c.MPosCore.SystemBack.apply({token:b.token,action:'deleteReceipt'}),false);h.c.MPosNativeSystemBackEnabled=false;assert.equal(h.c.MPosCore.SystemBack.capture(),null);assert.deepEqual(h.events,[]);
});
test('background acknowledges stable empty screen and receiving uses existing finish operation',()=>{
 const h=host(),a=h.capture();assert.equal(h.c.MPosCore.SystemBack.apply({token:a.token,action:'BACKGROUND'}),true);assert.deepEqual(h.events,[]);
 h.c.receiving={};const b=h.capture();assert.equal(h.c.MPosCore.SystemBack.apply({token:b.token,action:'FINISH_RECEIVING'}),true);assert.deepEqual(h.events,['receiving']);
});
test('native opening without HTML is modal presence and token replacement rejects stale Back',()=>{
 const h=host();let token='opening';h.c.MPosCore.NativeOpenForm={activeToken:()=>token};
 const first=h.capture();assert.equal(first.modal,true);token='new-opening';
 assert.equal(h.c.MPosCore.SystemBack.apply({token:first.token,action:'CLOSE_MODAL'}),false);
 const next=h.capture();assert.equal(h.c.MPosCore.SystemBack.apply({token:next.token,action:'CLOSE_MODAL'}),true);assert.deepEqual(h.events,['modal']);
});
