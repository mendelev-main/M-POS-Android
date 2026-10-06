/* Refresh only money nodes after a matching native quote; preserve root DOM, forms and scroll. */
(function(global){
  'use strict';
  if(!global.MPosCore?.PaymentTotals||typeof global.renderCartPanel!=='function')return;
  const totals=global.MPosCore.PaymentTotals,original=global.renderCartPanel;
  const enabled=()=>global.MPosNativeCartPreviewEnabled!==false&&totals.enabled();
  let scheduled=false,running=false,dirty=false;
  function apply(){
    if(!enabled()||state.tab!=='pos')return;
    const summary=totals.presentation();if(!summary)return;
    const panel=global.document.querySelector('.cart-panel');if(!panel)return;
    const lines=panel.querySelectorAll('.cart-row-linetotal');
    if(lines.length!==state.cart.length)return;
    lines.forEach((node,i)=>{node.textContent=money(summary.lines[i].total);});
    const foot=panel.querySelector('.cart-foot');if(!foot)return;
    const rows=[...foot.children].filter(node=>node.classList.contains('total-row'));
    const totalRow=rows[rows.length-1];if(!totalRow)return;
    const value=totalRow.querySelector('.value');if(value)value.textContent=fullMoney(summary.total);
    let loyalty=rows.find(row=>row.querySelector('.label')?.textContent==='Программа лояльности');
    const discount=summary.loyaltyDiscount;
    if(discount>0){
      if(!loyalty){loyalty=global.document.createElement('div');loyalty.className='total-row cart-total-meta';const label=global.document.createElement('span');label.className='label';label.textContent='Программа лояльности';const amount=global.document.createElement('span');amount.className='value';loyalty.append(label,amount);foot.insertBefore(loyalty,totalRow);}
      loyalty.querySelector('.value').textContent='−'+fullMoney(discount);
    }else loyalty?.remove();
    const deliveryTotal=global.document.querySelector('.delivery-total-value');
    if(deliveryTotal)deliveryTotal.textContent=fullMoney(summary.total);
  }
  function schedule(){
    if(scheduled||running||!enabled())return;
    scheduled=true;
    global.setTimeout(async()=>{
      scheduled=false;if(!enabled()||state.tab!=='pos')return;
      running=true;dirty=false;
      try{if(totals.current()||await totals.prepare())apply();}catch(_error){/* Reviewed preview stays; payment still requires its own authoritative gates. */}
      finally{running=false;if(dirty&&!totals.current())schedule();}
    },0);
  }
  global.renderCartPanel=function(...args){
    const html=original.apply(this,args);
    if(enabled()&&state.tab==='pos'){dirty=true;schedule();}
    return html;
  };
  global.MPosCore.CartPreview=Object.freeze({refresh(){dirty=true;schedule();}});
})(window);
