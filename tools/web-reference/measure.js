// DOM-derived measurements; no inferred Compose values and no normalization of the Web CSS.
globalThis.measureReference=function measureReference(){
  const root=getComputedStyle(document.documentElement);
  const tokens=Object.fromEntries(['--bg','--surface','--ink','--muted','--accent','--accent-soft','--navy','--border','--danger','--radius-l','--radius-m','--radius-s','--shadow'].map(k=>[k,root.getPropertyValue(k).trim()]));
  const selectors='.topbar,.main,.pos-main,.cart-panel,.cart-head,.cart-row,.cart-actions,.modal,.modal-title,.modal-actions,.payment-page,.payment-summary,.payment-methods,.payment-actions,.split-payment,.product-editor,.pe-layout,.settings-card,.btn,input,select,textarea';
  const elements=[...document.querySelectorAll(selectors)].filter(e=>{const r=e.getBoundingClientRect();const s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&r.bottom>0&&r.right>0&&r.top<innerHeight&&r.left<innerWidth;}).map(e=>{
    const r=e.getBoundingClientRect(),s=getComputedStyle(e),round=x=>Math.round(x*100)/100;
    return {tag:e.tagName,id:e.id,classes:e.className,text:(e.innerText||e.getAttribute('aria-label')||'').trim().slice(0,90),rect:{x:round(r.x),y:round(r.y),width:round(r.width),height:round(r.height)},background:s.backgroundColor,color:s.color,border:s.border,borderRadius:s.borderRadius,padding:s.padding,gap:s.gap,fontFamily:s.fontFamily,fontSize:s.fontSize,fontWeight:s.fontWeight,lineHeight:s.lineHeight,shadow:s.boxShadow};
  });
  return {scene:document.documentElement.dataset.referenceReady,viewport:{width:innerWidth,height:innerHeight,dpr:devicePixelRatio},fontLoaded:document.fonts.check('15px Manrope'),tokens,elements};
};
