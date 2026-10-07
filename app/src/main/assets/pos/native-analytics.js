(function(global){
 'use strict';
 const core=global.MPosCore,original=global.renderAnalyticsScreen,oldData=global.analyticsData,oldInventory=global.inventoryCostValue;
 if(!core?.Analytics||typeof original!=='function')return;
 let scoped=null,cached=null,generation=0;
 const disabled=()=>global.MPosNativeAnalyticsEnabled===false;
 function period(){return {from:state.analyticsFrom||'',to:state.analyticsTo||''}}
 function input(){return {version:1,...period(),now:Date.now(),zone:Intl.DateTimeFormat().resolvedOptions().timeZone}}
 function current(ticket,p){const now=period();return !disabled()&&state.tab==='analytics'&&ticket===generation&&p.from===now.from&&p.to===now.to}
 function using(data,callback){const previous=scoped;scoped=data;try{return callback()}finally{scoped=previous}}
 function mark(html,busy=false){return html.replace('<div class="screen',`<div data-mpos-native-analytics aria-busy="${busy}" class="screen`)}
 function html(data){return mark(using(data,()=>original()))}
 function placeholder(error){return `<div data-mpos-native-analytics aria-busy="${!error}" class="screen content-screen active"><div class="card"><div class="analytics-title">Аналитика продаж</div><div class="center-note" role="status">${error?'Не удалось загрузить аналитику. '+escapeHtml(error):'Загрузка аналитики…'}</div>${error?'<button class="btn btn-primary" onclick="render()">Повторить</button>':''}</div></div>`}
 global.analyticsData=function(){if(disabled())return oldData.apply(this,arguments);if(!scoped)throw Error('Аналитика ещё не загружена');return {...scoped,orders:{length:scoped.orderCount}}};
 global.inventoryCostValue=function(){if(disabled())return oldInventory.apply(this,arguments);if(!scoped)throw Error('Аналитика ещё не загружена');return scoped.inventoryValue};
 function apply(ticket,p,content){
  if(!current(ticket,p))return;
  const node=document.querySelector('[data-mpos-native-analytics]');if(!node)return;
  const focused=document.activeElement;
  if(focused&&node.contains?.(focused)&&focused.matches?.('input[type="date"]')){focused.addEventListener('blur',()=>apply(ticket,p,content),{once:true});return}
  const scroll=node.scrollTop;node.outerHTML=content;const updated=document.querySelector('[data-mpos-native-analytics]');if(updated&&Number.isFinite(scroll))updated.scrollTop=scroll;
 }
 global.renderAnalyticsScreen=function(){
  if(disabled())return original.apply(this,arguments);
  if(state.tab!=='analytics'){generation++;return '<div data-mpos-native-analytics class="screen content-screen"></div>'}
  const ticket=++generation,p=period(),request=input();
  core.Analytics.read(request).then(result=>{
   if(!current(ticket,p))return;if(!result.data||['revenue','cash','card','cost','profit','avg','inventoryValue'].some(key=>typeof result.data[key]!=='number'||!Number.isFinite(result.data[key])))throw Error('В сохранённых данных есть некорректные суммы. Проверьте чеки.');const day=localDateString(new Date(request.now));if((!p.from||!p.to)&&day!==localDateString(new Date())){cached=null;render();return}cached={period:p,day,data:result.data};apply(ticket,p,html(result.data));
  }).catch(error=>{if(current(ticket,p)){cached=null;apply(ticket,p,placeholder(error.message))}});
  const day=localDateString(new Date());
  if(cached&&cached.day===day&&cached.period.from===p.from&&cached.period.to===p.to)return mark(using(cached.data,()=>original()),true);
  return placeholder();
 };
})(window);
