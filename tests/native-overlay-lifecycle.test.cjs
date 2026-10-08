const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const asset=name=>fs.readFileSync('app/src/main/assets/pos/'+name,'utf8');
function host(){const sent=[],events=[],c={MPosCore:{},webkit:{messageHandlers:{workspace:{postMessage:p=>sent.push(p)}}},addEventListener(){},document:{querySelector(){throw Error('DOM must not be read')},getElementById(){throw Error('DOM must not be read')}},currentShiftEmployeeIsAdmin:()=>true,
 showModal:()=>events.push('show'),closeModal:()=>events.push('modal'),renderPosFolderModal(){},renderWarehousePage(){},closeWarehousePage:()=>events.push('warehouse'),renderReceivingDocument(){},finishReceivingPage:()=>events.push('receiving'),cancelBackupImport(){c._pendingBackupImport=null;events.push('import')}};
 c.window=c;vm.createContext(c);vm.runInContext('let state={editMode:false}',c);vm.runInContext(asset('native-system-back.js'),c);vm.runInContext(asset('native-overlay-lifecycle.js'),c);return{c,sent,events,back:action=>c.MPosCore.SystemBack.applyNative({revision:sent.at(-1).revision,action})};}
test('native lifecycle tracks priorities without querying a mounted DOM',()=>{
 const h=host();assert.equal(h.back('BACKGROUND'),true);h.c._receivingDraft={orderId:'order'};h.c.renderReceivingDocument();assert.equal(h.back('FINISH_RECEIVING'),true);
 h.c.renderWarehousePage();h.c.showModal();h.c._pendingBackupImport={};h.c.MPosCore.OverlayLifecycle.changed();assert.equal(h.back('CANCEL_IMPORT'),true);
 assert.equal(h.back('CLOSE_MODAL'),true);h.c.closeModal();assert.equal(h.back('CLOSE_WAREHOUSE'),true);h.c.finishReceivingPage();assert.equal(h.back('BACKGROUND'),true);
 assert.deepEqual(h.events,['receiving','show','import','modal','modal','warehouse','receiving']);
});
test('replacement overlay/import identity invalidates a captured native revision',()=>{
 const h=host();h.c.showModal();const old=h.sent.at(-1).revision;h.c.showModal();assert.equal(h.c.MPosCore.SystemBack.applyNative({revision:old,action:'CLOSE_MODAL'}),false);
 h.c._pendingBackupImport={};h.c.MPosCore.OverlayLifecycle.changed();const imported=h.sent.at(-1).revision;h.c._pendingBackupImport={};assert.equal(h.c.MPosCore.SystemBack.applyNative({revision:imported,action:'CANCEL_IMPORT'}),false);
 assert.ok(!h.events.includes('import'));
});
test('native layout modal uses its owner Back and explicit rollback is published',()=>{
 const h=host();let token='layout';h.c.MPosCore.LayoutUi={activeToken:()=>token,back:()=>{token=null;h.events.push('layout');h.c.MPosCore.OverlayLifecycle.changed()}};
 h.c.MPosCore.OverlayLifecycle.changed();assert.equal(h.back('CLOSE_MODAL'),true);assert.deepEqual(h.events,['layout']);
 h.c.MPosNativeSystemBackEnabled=false;h.c.MPosCore.OverlayLifecycle.changed();assert.equal(h.sent.at(-1).enabled,false);assert.equal(h.back('BACKGROUND'),false);
});
