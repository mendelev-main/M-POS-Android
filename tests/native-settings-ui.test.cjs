const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const adapter=fs.readFileSync('app/src/main/assets/pos/native-settings-ui.js','utf8');
class Node{
 constructor(tag='div',classes='',text=''){this.tagName=tag.toUpperCase();this.nodeType=1;this.classes=new Set(classes.split(' '));this.classList={contains:c=>this.classes.has(c)};this.textContent=text;this.children=[];this.style={opacity:'',pointerEvents:''};this.dataset={};this.attrs={};this.isConnected=true;this.value='';this.type='text';this.hidden=false;this.disabled=false;this.readOnly=false;this.checked=false;this.focus=()=>{};this.blur=()=>{};this.onclick=()=>{};this.onchange=()=>{}}
 add(n){n.parentElement=this;this.children.push(n);return n} getAttribute(k){return this.attrs[k]??null}
 matches(s){return s.split(',').some(s=>{s=s.trim();if(s.startsWith('.'))return this.classes.has(s.slice(1));if(s.startsWith('#'))return this.id===s.slice(1);if(s==='input[type="hidden"]')return this.tagName==='INPUT'&&this.type==='hidden';return this.tagName===s.toUpperCase()})}
 querySelector(s){for(const child of this.children){if(child.matches(s))return child;const nested=child.querySelector(s);if(nested)return nested}return null}
 closest(s){for(let n=this;n;n=n.parentElement)if(n.matches(s))return n;return null}
 getBoundingClientRect(){return{left:200,top:0,width:600,height:800}} dispatchEvent(){this.onchange()} click(){this.onclick()} detach(){this.isConnected=false;this.children.forEach(c=>c.detach())}
}
function host(configure){
 const sent=[],events=[],raf=[];let modal=null,page=null,done;
 const pane=new Node('div','content-screen active');pane.add(new Node('div','content-title','Настройки'));const card=pane.add(new Node('div','settings-card'));card.add(new Node('div','settings-section-title','Сотрудники'));
 const button=card.add(new Node('button','btn btn-primary','Добавить сотрудника'));
 const document={hidden:false,readyState:'complete',body:new Node(),documentElement:{getAttribute:()=> 'light'},activeElement:null,addEventListener:()=>{},getElementById:id=>id==='printer-page'?page:id==='modal-root'?new Node():id==='app'?pane:null,querySelector:s=>s==='#modal-root .modal'?modal:s.includes('data-mpos-settings-ui')?pane:null};
 const h={state:{loaded:true,tab:'settings'},document,innerWidth:1000,innerHeight:800,Event:class{},MutationObserver:class{observe(){}},requestAnimationFrame:f=>raf.push(f),addEventListener:()=>{},webkit:{messageHandlers:{settingsScreen:{postMessage:p=>{sent.push(JSON.parse(JSON.stringify(p)));return true}}}},flash:s=>events.push(s),renderSettingsScreen:()=>'<div class="screen content-screen active">',renderNetworkScreen:()=>'<div class="screen content-screen active">',showModal:()=>{modal=new Node('div','modal');modal.add(new Node('div','modal-title','Новый сотрудник'))},closeModal:()=>{events.push('close');modal?.detach();modal=null},saveEmployee:async()=>{events.push('save');const result=await new Promise(r=>done=r);if(result)h.closeModal();else h.flash('Проверьте пароль');return result}};
 let name,role,password,save;
 h.openEmployeeModal=()=>{h.showModal('reviewed');const container=modal.add(new Node('div','field'));container.add(new Node('label','','ФИО'));name=container.add(new Node('input'));name.id='ef-name';name.value='Сотрудник';role=modal.add(new Node('input'));role.type='checkbox';role.id='ef-admin';const wrap=modal.add(new Node());wrap.hidden=true;password=wrap.add(new Node('input'));password.type='password';password.id='ef-admin-password';role.onchange=()=>wrap.hidden=!role.checked;save=modal.add(new Node('button','btn btn-primary','Сохранить'));save.onclick=()=>h.saveEmployee();const cancel=modal.add(new Node('button','btn','Отмена'));cancel.onclick=()=>h.closeModal()};
 h.window=h;if(configure)configure(h);button.onclick=()=>h.openEmployeeModal();vm.createContext(h);if(h.lexicalState){const initial=h.state;delete h.state;vm.runInContext('let state = '+JSON.stringify(initial),h);}if(configure)vm.runInContext(fs.readFileSync("app/src/main/assets/pos/native-connection-tests.js","utf8"),h);vm.runInContext(adapter,h);
 function flush(){for(let i=0;raf.length&&i<10;i++)raf.shift()()}
 function action(p){return h.__nativeSettingsAction(p)}
 function open(){h.openEmployeeModal();flush();return sent.filter(p=>p.action==='formShow').at(-1)}
 flush();return{h,sent,events,pane,button,document,flush,action,open,get fields(){return{name,role,password,save}},reply:ok=>done(ok),setPage:p=>page=p};
}
test('settings hub uses reviewed sections/actions and no new permission or business calculation',async()=>{const h=host(),show=h.sent.find(p=>p.action==='show');assert.ok(show);assert.equal(show.items[0].text,'Настройки');assert.equal(show.items[1].items[0].text,'Сотрудники');assert.equal(h.pane.style.opacity,'0');await h.action({action:'click',token:show.token,key:'0',fields:{}});h.flush();assert.ok(h.sent.some(p=>p.action==='formShow'));assert.match(h.h.renderSettingsScreen(),/data-mpos-settings-ui="settings"/)});
test('employee native fields bind to existing handler, lock duplicate submit and wait for rejection',async()=>{const h=host(),form=h.open();assert.equal(form.items.filter(i=>i.kind==='field').length,3);assert.equal(form.items.find(i=>i.type==='password').visible,false);const p=h.action({action:'click',token:form.token,key:'0',fields:{0:'Иван',1:false,2:'synthetic-invalid'}});await h.action({action:'click',token:form.token,key:'0',fields:{0:'Other'}});assert.deepEqual(h.events,['save']);assert.equal(h.fields.name.value,'Иван');assert.equal(h.fields.password.value,'synthetic-invalid');h.reply(false);await p;assert.equal(h.sent.at(-1).action,'formResult');assert.equal(h.sent.at(-1).message,'Проверьте пароль');assert.ok(!h.events.includes('close'))});
test('role toggle calls reviewed visibility logic and cancel uses reviewed close',async()=>{const h=host(),form=h.open();await h.action({action:'change',token:form.token,fields:{1:true}});const update=h.sent.at(-1);assert.equal(update.action,'formUpdate');assert.equal(update.fields.find(f=>f.type==='password').visible,true);await h.action({action:'cancel',token:form.token});assert.ok(h.events.includes('close'));assert.ok(h.sent.some(p=>p.action==='formHide'))});
test('stale or forged action tokens and detached buttons cannot invoke business handler',async()=>{const h=host(),form=h.open();await h.action({action:'click',token:'stale',key:'0'});await h.action({action:'click',token:form.token,key:'saveEmployee()'});await h.action({action:'click',token:form.token,key:'100'});h.fields.save.detach();await h.action({action:'click',token:form.token,key:'0'});assert.deepEqual(h.events,[])});
test('success closes via reviewed handler after acknowledgement; disabled UI restores source presentation',async()=>{const h=host(),form=h.open();const p=h.action({action:'click',token:form.token,key:'0',fields:{}});assert.ok(!h.events.includes('close'));h.reply(true);await p;assert.ok(h.events.includes('close'));h.h.MPosNativeSettingsUiEnabled=false;h.flush();assert.equal(h.pane.style.opacity,'');assert.equal(h.pane.style.pointerEvents,'')});
test('non-settings modals remain on their own presentation path; imported backup confirmation is recognized without copying verifier',()=>{const h=host();h.h.showModal('other');h.flush();assert.ok(!h.sent.some(p=>p.action==='formShow'));assert.equal(h.sent.at(-1).action,'hide');const html=fs.readFileSync('app/src/main/assets/pos/pos.html','utf8');assert.ok(html.indexOf('src="native-platform-settings.js"')<html.indexOf('<script>loadAll()'));assert.ok(html.indexOf('src="native-settings-ui.js"')<html.indexOf('<script>loadAll()'));assert.doesNotMatch(adapter,/eval\(|new Function|onclick.*getAttribute/)});

test('unknown employee commit blocks further native submission and source cancel is the single cancel route',async()=>{const h=host(),form=h.open();assert.equal(form.items.filter(i=>i.kind==='button').length,1);assert.equal(form.cancelLabel,'Отмена');const p=h.action({action:'click',token:form.token,key:'0',fields:{}});h.h.criticalStorageRecoveryPending=true;h.reply(false);await p;assert.equal(h.sent.at(-1).blocked,true);await h.action({action:'cancel',token:form.token});assert.equal(h.events.filter(e=>e==='close').length,1)});


test('connection result reaches native form once after HTTP response; overlapping gestures stay locked',async()=>{
 let reply,requests=0;
 const x=host(h=>{h.setTimeout=setTimeout;h.clearTimeout=clearTimeout;h.AbortController=AbortController;h.currentShiftEmployeeIsAdmin=()=>true;h.networkConfigFromState=()=>({backendUrl:'https://backend.example',deviceKey:'fixture'});h.fetch=()=>{requests++;return new Promise(r=>reply=r)}});
 const form=x.open();x.fields.save.onclick=()=>x.h.testWebOrder();
 const p=x.action({action:'click',token:form.token,key:'0',fields:{}});
 await x.action({action:'click',token:form.token,key:'0',fields:{}});assert.equal(requests,1);assert.ok(!x.sent.some(r=>r.action==='formResult'&&r.token===form.token));
 reply({ok:true,status:200});await p;const result=x.sent.at(-1);assert.equal(result.action,'formResult');assert.equal(result.token,form.token);assert.equal(result.message,'Тестовый заказ отправлен');assert.equal(result.error,false);
});

test('late connection result cannot enter a replacement native form or its pending operation',async()=>{
 let reply;
 const x=host(h=>{h.setTimeout=setTimeout;h.clearTimeout=clearTimeout;h.AbortController=AbortController;h.currentShiftEmployeeIsAdmin=()=>true;h.networkConfigFromState=()=>({backendUrl:'https://backend.example',deviceKey:'fixture'});h.fetch=()=>new Promise(r=>reply=r)});
 const old=x.open();x.fields.save.onclick=()=>x.h.testWebOrder();const p=x.action({action:'click',token:old.token,key:'0',fields:{}});
 x.h.closeModal();x.flush();const next=x.open();const newer=x.action({action:'click',token:next.token,key:'0',fields:{}});
 reply({ok:false,status:500});await p;assert.ok(!x.events.some(m=>String(m).includes('HTTP 500')));assert.ok(!x.sent.some(r=>r.action==='formResult'&&r.token===old.token));
 x.reply(false);await newer;assert.equal(x.sent.at(-1).message,'Проверьте пароль');
});


test('reopened form cannot start another WEB request while its previous test is still pending',async()=>{
 let reply,requests=0;
 const x=host(h=>{h.setTimeout=setTimeout;h.clearTimeout=clearTimeout;h.AbortController=AbortController;h.currentShiftEmployeeIsAdmin=()=>true;h.networkConfigFromState=()=>({backendUrl:'https://backend.example',deviceKey:'fixture'});h.fetch=()=>{requests++;return new Promise(r=>reply=r)}});
 const old=x.open();x.fields.save.onclick=()=>x.h.testWebOrder();const p=x.action({action:'click',token:old.token,key:'0',fields:{}});
 x.h.closeModal();x.flush();const next=x.open();x.fields.save.onclick=()=>x.h.testWebOrder();await x.action({action:'click',token:next.token,key:'0',fields:{}});
 assert.equal(requests,1);assert.equal(x.sent.at(-1).message,'Дождитесь завершения предыдущей проверки');const count=x.sent.length;
 reply({ok:true,status:200});await p;assert.equal(x.sent.length,count);assert.ok(!x.events.includes('Тестовый заказ отправлен'));
});

test('production lexical state enables settings hub and native editing without window.state',async()=>{
 const h=host(c=>{c.lexicalState=true});assert.equal(h.h.state,undefined);
 const show=h.sent.find(p=>p.action==='show');assert.ok(show);assert.equal(h.pane.style.opacity,'0');
 await h.action({action:'click',token:show.token,key:'0',fields:{}});h.flush();assert.ok(h.sent.some(p=>p.action==='formShow'));
});
