const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/pos/native-layout-ui.js','utf8'),tick=()=>new Promise(r=>setImmediate(r));
function host(read){const sent=[],frames=[],events=[],node={style:{opacity:'',pointerEvents:''},getBoundingClientRect:()=>({left:0,top:80,width:600,height:650})};let observer;
 const c={MPosCore:{WorkspaceNavigationLifecycle:{generation:()=>0,toolbar:async()=>{}},WorkspaceNavigation:{execute:async()=>read?read():({ok:true,authoritative:true,tiles:[],documentRevision:'hash'})}},webkit:{messageHandlers:{workspace:{postMessage:p=>{sent.push(p);return true}}}},innerWidth:1200,innerHeight:800,
 requestAnimationFrame:fn=>frames.push(fn),MutationObserver:class{constructor(fn){observer=fn}observe(){}},addEventListener(){},document:{readyState:'complete',hidden:false,querySelector:()=>node,getElementById:()=>({}),documentElement:{getAttribute:()=>null},addEventListener(){}},flash:m=>events.push(m),openLayoutEditor:()=>events.push('legacy'),openPosFolderEditor(){},openPosTileMove(){}};
 c.window=c;vm.createContext(c);vm.runInContext("let state={loaded:true,tab:'pos',search:'',posPath:null,posFolder:'',editMode:true,layoutTiles:[{type:'product',id:'p',col:0,row:0}]};let criticalStorageRecoveryPending=false",c);vm.runInContext(source,c);
 const flush=()=>{for(let n=0;frames.length&&n<10;n++)frames.shift()()};flush();return{c,sent,node,events,flush,mutate:()=>{observer();flush()},state:()=>vm.runInContext('state',c),blocked:()=>vm.runInContext('criticalStorageRecoveryPending',c)};}
test('native editor hides only source layout; direct forms do not create legacy modal',async()=>{
 const h=host();await tick();assert.equal(h.sent[0].action,'layoutShow');assert.equal(h.node.style.opacity,'0');h.c.openLayoutEditor();h.flush();await tick();assert.equal(h.sent.at(-1).form,'add');assert.deepEqual(h.events,[]);
 h.c.MPosNativeLayoutUiEnabled=false;h.mutate();await tick();assert.equal(h.node.style.opacity,'');h.c.openLayoutEditor();assert.deepEqual(h.events,['legacy']);
});
test('atomic acknowledgement accepts different object key ordering and never writes via JS storage',async()=>{
 const h=host();await tick();const token=h.sent[0].token,expected={tab:'pos',search:'',posPath:null,posFolder:'',editMode:true};
 h.c.MPosCore.LayoutUi.committed({token,requestId:'r',result:{allowed:true,key:'layout',expected,before:[{row:0,col:0,id:'p',type:'product'}],document:{tiles:[]}}});
 assert.equal(h.state().layoutTiles.length,0);assert.equal(h.sent.at(-1).action,'layoutApplied');assert.equal(h.blocked(),false);
});
test('late persisted result cannot overwrite imported state, and fallback restores source',async()=>{
 const h=host();await tick();const token=h.sent[0].token;h.state().layoutTiles=[{id:'imported'}];h.c.MPosCore.LayoutUi.committed({token,result:{allowed:true,key:'layout',expected:{tab:'pos',search:'',posPath:null,posFolder:'',editMode:true},before:[],document:{tiles:[]}}});
 assert.equal(h.state().layoutTiles[0].id,'imported');assert.equal(h.blocked(),true);assert.equal(h.node.style.opacity,'');
 const a=host();await tick();a.c.MPosCore.LayoutUi.action({token:a.sent[0].token,action:'fallback'});assert.equal(a.node.style.opacity,'');assert.equal(a.c.MPosNativeLayoutUiEnabled,false);
});
test('old async failure does not hide a newer panel and native folder Back clears projected descriptor',async()=>{
 const pending=[],h=host(()=>new Promise((resolve,reject)=>pending.push({resolve,reject})));await tick();h.state().search='new';h.mutate();await tick();
 pending[1].resolve({ok:true,authoritative:true,tiles:[],documentRevision:'new'});await tick();const token=h.sent.at(-1).token;
 pending[0].reject(Error('old'));await tick();assert.equal(h.node.style.opacity,'0');assert.deepEqual(h.events,[]);
 h.c.MPosCore.LayoutUi.action({token,action:'modal',active:true,parent:'folder'});assert.equal(h.c._posFolderModal.id,'folder');h.c.MPosCore.LayoutUi.back();assert.equal(h.sent.at(-1).action,'layoutBack');
 h.c.MPosCore.LayoutUi.action({token,action:'modal',active:false,parent:''});assert.equal(h.c._posFolderModal,null);
});
