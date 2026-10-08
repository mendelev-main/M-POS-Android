const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/pos/native-workspace-navigation.js','utf8');
const tick=()=>new Promise(r=>setImmediate(r));
function host(){
 const calls=[],events=[],c={state:{tab:'pos',posPath:null,posFolder:'',search:'чай',editMode:false,paymentPage:null,cart:[{id:'unchanged',qty:2}]},_posFolderModal:null,
 document:{getElementById:()=>({firstElementChild:c.modal})},setTimeout:f=>f(),setTab:()=>events.push('legacy-tab'),onSearch:()=>{},
 render:()=>events.push('render'),flash:()=>events.push('error'),closeModal:()=>{c._posFolderModal=null;events.push('close')},renderPosFolderModal:()=>events.push('folder'),setupLayoutGridDrag:()=>events.push('drag'),
 MPosCore:{WorkspaceNavigation:{execute:input=>new Promise((resolve,reject)=>calls.push({input,resolve,reject}))}}};
 for(const name of ['openPosCategory','closePosCategory','openPosFolder','toggleEditMode'])c[name]=()=>events.push('legacy-'+name);
 c.window=c;vm.createContext(c);vm.runInContext(source,c);
 return{c,calls,events,ready:async()=>{calls[0].resolve({});await tick()}};
}
const prepared=(token='proposal')=>({allowed:true,proposalToken:token,patch:{posPath:'Кофе',posFolder:'',search:'',editMode:false},effect:'render'});
test('category uses native state, no catalogue/DOM payload, and waits for accepted proposal',async()=>{
 const h=host(),p=h.c.openPosCategory(' Кофе ');await h.ready();
 const request=h.calls[1].input;assert.equal(request.operation,'prepareRoute');assert.equal(request.route,'openCategory');assert.equal(request.expected.search,'чай');assert.equal(request.products,undefined);assert.equal(request.navigation,undefined);
 h.calls[1].resolve(prepared());await tick();assert.equal(h.c.state.posPath,null);assert.deepEqual(h.events,[]);
 assert.equal(h.calls[2].input.operation,'acceptRoute');h.calls[2].resolve(prepared());assert.equal(await p,true);
 assert.equal(h.c.state.posPath,'Кофе');assert.equal(h.c.state.cart[0].qty,2);assert.deepEqual(h.events,['render']);assert.equal(h.c.MPosCore.WorkspaceRoutes.hasPending(),false);
});
test('stale tab, query or modal cancels prepared transition before acceptance',async()=>{
 for(const change of [c=>{c.state.tab='receipts'},c=>{c.state.search='новый'},c=>{c.modal={}}]){
  const h=host(),p=h.c.openPosCategory('Кофе');await h.ready();change(h.c);h.calls[1].resolve(prepared());assert.equal(await p,false);
  assert.equal(h.calls[2].input.operation,'cancelRoute');h.calls[2].resolve({});assert.deepEqual(h.events,[]);assert.equal(h.c.state.posPath,null);
 }
});
test('queued category and Back use the accepted current selection',async()=>{
 const h=host(),a=h.c.openPosCategory('Кофе'),b=h.c.closePosCategory();await h.ready();h.calls[1].resolve(prepared());await tick();h.calls[2].resolve(prepared());await a;await tick();
 assert.equal(h.calls[3].input.route,'closeCategory');assert.equal(h.calls[3].input.expected.posPath,'Кофе');
 const back={allowed:true,proposalToken:'back',patch:{posPath:null,search:'',editMode:false},effect:'render'};
 h.calls[3].resolve(back);await tick();h.calls[4].resolve(back);assert.equal(await b,true);assert.equal(h.c.state.posPath,null);assert.deepEqual(h.events,['render','render']);
});
test('Back preserves folder modal priority and inline folder patch',async()=>{
 for(const modal of [true,false]){
  const h=host();h.c.state.posPath='Кофе';h.c.state.posFolder='legacy';if(modal)h.c._posFolderModal={category:'Кофе',id:'f'};
  const p=h.c.closePosCategory();await h.ready();assert.equal(h.calls[1].input.folderModal?.id,modal?'f':undefined);
  const result={allowed:true,proposalToken:'back',patch:modal?{}:{posFolder:'',search:''},effect:modal?'closeModal':'render'};
  h.calls[1].resolve(result);await tick();h.calls[2].resolve(result);assert.equal(await p,true);
  assert.equal(h.c.state.posPath,'Кофе');assert.equal(h.c.state.posFolder,modal?'legacy':'');assert.deepEqual(h.events,[modal?'close':'render']);
 }
});
test('native rejection never silently reverts to JS decision; explicit rollback remains available',async()=>{
 const h=host(),p=h.c.openPosFolder('missing');await h.ready();h.calls[1].reject(Error('projection changed'));assert.equal(await p,false);assert.deepEqual(h.events,['error']);
 h.c.MPosNativeWorkspaceNavigationEnabled=false;await h.c.openPosCategory('Кофе');assert.equal(h.events.at(-1),'legacy-openPosCategory');
 h.c.MPosNativeWorkspaceNavigationEnabled=true;h.c.MPosNativeWorkspaceRouteEnabled=false;await h.c.closePosCategory();assert.equal(h.events.at(-1),'legacy-closePosCategory');
});
test('unknown folder has no proposal and newer screen ignores a late acceptance',async()=>{
 const h=host(),p=h.c.openPosFolder('missing');await h.ready();h.calls[1].resolve({allowed:false});assert.equal(await p,false);assert.equal(h.calls.length,2);assert.deepEqual(h.events,[]);
 const q=h.c.openPosCategory('Кофе');await tick();h.calls[2].resolve(prepared());await tick();h.c.state.tab='receipts';h.calls[3].resolve(prepared());await tick();assert.equal(h.calls[4].input.operation,'discardRoute');h.calls[4].resolve({});assert.equal(await q,false);assert.equal(h.c.state.posPath,null);
});
