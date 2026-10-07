// Small DOM fixture for executing reviewed product renderers/inline events without a browser.
const vm=require('node:vm');
const decode=s=>s.replace(/&quot;/g,'"').replace(/&#39;|&#x27;/g,"'").replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&amp;/g,'&');
function dom(context){
 const observers=[],frames=[];
 class Node{
  constructor(tag='div'){this.tagName=tag.toUpperCase();this.nodeType=1;this.children=[];this.attrs={};this.style={};this.dataset={};this._text='';this._listeners={};this._value=undefined;this.hidden=false;this.disabled=false;this.readOnly=false;this.checked=false;this.scrollTop=0;this.focus=()=>{document.activeElement=this};this.blur=()=>{document.activeElement=null};this.classList={contains:c=>this.classes.includes(c),add:c=>{this.attrs.class=[...this.classes,c].join(' ')},toggle:(c,on)=>{let cs=this.classes.filter(x=>x!==c);if(on)cs.push(c);this.attrs.class=cs.join(' ')}}}
  remove(){if(this.parentElement){this.parentElement.children=this.parentElement.children.filter(c=>c!==this);this.parentElement=null}} select(){} scrollIntoView(){}
  get classes(){return(this.attrs.class||'').split(/\s+/)} get id(){return this.attrs.id||''} set id(v){this.attrs.id=v}
  get type(){return this.attrs.type||'text'}get placeholder(){return this.attrs.placeholder||''}
  get isConnected(){return this===document.body||!!this.parentElement?.isConnected}
  get options(){return this.tagName==='SELECT'?this.children.filter(c=>c.tagName==='OPTION'):undefined}
  get value(){if(this._value!==undefined)return this._value;if(this.tagName==='SELECT')return(this.options.find(o=>'selected'in o.attrs)||this.options[0])?.value||'';if(this.tagName==='TEXTAREA')return this.textContent;return this.attrs.value??(this.tagName==='OPTION'?this.textContent:'')}
  set value(v){this._value=String(v)} get textContent(){return this._text+this.children.map(c=>c.textContent).join('')}set textContent(v){this._text=String(v);this.children.forEach(c=>c.parentElement=null);this.children=[]}
  get innerHTML(){return ''}set innerHTML(html){this.children.forEach(c=>c.parentElement=null);this.children=[];this._text='';parse(String(html),this)}
  getAttribute(k){return this.attrs[k]??null}setAttribute(k,v){this.attrs[k]=String(v);if(k.startsWith('data-'))this.dataset[k.slice(5).replace(/-([a-z])/g,(_,c)=>c.toUpperCase())]=String(v)}
  contains(node){for(let p=node;p;p=p.parentElement)if(p===this)return true;return false}
  addEventListener(name,fn,options={}){(this._listeners[name]||=[]).push({fn,once:!!options.once})}
  get outerHTML(){return ''}set outerHTML(html){const parent=this.parentElement;if(!parent)return;const index=parent.children.indexOf(this),replacement=new Node();parse(String(html),replacement);for(const child of replacement.children)child.parentElement=parent;parent.children.splice(index,1,...replacement.children);this.parentElement=null}
  appendChild(c){c.parentElement=this;this.children.push(c);return c}
  matches(selector){return selector.split(',').some(s=>{s=s.trim();if(s.includes(':checked')&&!this.checked)return false;s=s.replace(':checked','');const attr=[...s.matchAll(/\[([^=\]]+)(?:=["']?([^\]"']*)["']?)?\]/g)];if(attr.some(m=>!(m[1]in this.attrs)||(m[2]!==undefined&&this.attrs[m[1]]!==m[2])))return false;s=s.replace(/\[[^\]]+\]/g,'');const id=s.match(/#([\w-]+)/)?.[1];if(id&&id!==this.id)return false;const classes=[...s.matchAll(/\.([\w-]+)/g)].map(m=>m[1]);if(classes.some(c=>!this.classes.includes(c)))return false;const tag=s.match(/^[\w-]+/)?.[0];return !tag||tag.toUpperCase()===this.tagName})}
  querySelectorAll(s){const parts=s.trim().split(/\s+(?![^\[]*\])/),last=parts.pop(),found=[];for(const child of this.children){if(child.matches(last)){let p=child.parentElement,j=parts.length-1;while(p&&j>=0){if(p.matches(parts[j]))j--;p=p.parentElement}if(j<0)found.push(child)}found.push(...child.querySelectorAll(s))}return found}
  querySelector(s){return this.querySelectorAll(s)[0]||null}closest(s){for(let p=this;p;p=p.parentElement)if(p.matches(s))return p;return null}
  get clientWidth(){return this.getBoundingClientRect().width}get clientHeight(){return this.getBoundingClientRect().height}
  getBoundingClientRect(){return{left:0,top:0,width:1000,height:800}}
  dispatchEvent(e){e.target=this;e.preventDefault ||= ()=>{};e.stopPropagation ||= ()=>{e._propagationStopped=true};for(let p=this;p;p=p.parentElement){for(const listener of [...(p._listeners[e.type]||[])]){listener.fn(e);if(listener.once)p._listeners[e.type]=p._listeners[e.type].filter(x=>x!==listener)}if(typeof p['on'+e.type]==='function')p['on'+e.type](e);const code=p.attrs['on'+e.type];if(code){context._eventNode=p;context._fixtureEvent=e;vm.runInContext('(function(event){'+code+'}).call(_eventNode,_fixtureEvent)',context)}if(e._propagationStopped)break}return true}
  click(){if(!this.disabled)this.dispatchEvent({type:'click'})}
 }
 function parse(html,root){const stack=[root];for(const m of html.matchAll(/<\/?[^>]+>|[^<]+/g)){const t=m[0];if(t.startsWith('</')){stack.pop();continue}if(t.startsWith('<')){const tag=t.match(/^<([\w-]+)/)?.[1];if(!tag)continue;const node=new Node(tag);const rest=t.slice(tag.length+1,-1);for(const a of rest.matchAll(/([^\s=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s]+)))?/g))node.setAttribute(a[1],decode(a[2]??a[3]??a[4]??''));node.hidden='hidden'in node.attrs;node.disabled='disabled'in node.attrs;node.readOnly='readonly'in node.attrs;node.checked='checked'in node.attrs;stack.at(-1).appendChild(node);if(!t.endsWith('/>')&&!['input','img','br','hr','meta','link'].includes(tag))stack.push(node)}else stack.at(-1)._text+=decode(t)}}
 const document={hidden:false,readyState:'complete',activeElement:null,body:null,createElement:tag=>new Node(tag),documentElement:{getAttribute:()=>context.theme||'light'},getElementById:id=>document.body.querySelector('#'+id),querySelector:s=>document.body.querySelector(s),querySelectorAll:s=>document.body.querySelectorAll(s),addEventListener:()=>{}};document.body=new Node('body');
 document.body.innerHTML='<div id="app"><div class="screen active"></div></div><div id="modal-root"></div><div id="product-editor-root"></div>';
 return{document,Node,MutationObserver:class{constructor(f){observers.push(()=>{if(this.active)f()})}observe(){this.active=true}disconnect(){this.active=false}},requestAnimationFrame:f=>frames.push(f),pump(){observers.forEach(f=>f());for(let i=0;frames.length&&i<20;i++)frames.shift()()}};
}
module.exports={dom};
