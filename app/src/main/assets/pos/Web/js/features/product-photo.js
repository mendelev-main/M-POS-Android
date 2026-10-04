function pickProductImage(){
  window._pmPhotoRequestSession=window._pmSession;
  if(window.webkit?.messageHandlers?.photoPicker){
    window.webkit.messageHandlers.photoPicker.postMessage({action:'pick'});
  }else{
    document.getElementById('pf-image-input')?.click();
  }
}
function handleNativeProductImage(dataUrl,localImageId){
  if(!dataUrl || window._pmPhotoRequestSession!==window._pmSession || !document.getElementById('pe-save')){if(localImageId)window.webkit?.messageHandlers?.photoPicker?.postMessage({action:'remove',id:localImageId});return}
  if(window._pmLocalImageId&&window._pmLocalImageId!==localImageId)window.webkit?.messageHandlers?.photoPicker?.postMessage({action:'remove',id:window._pmLocalImageId});
  window._pmImageData=dataUrl;
  window._pmLocalImageId=localImageId||null;
  window._pmRemoveImage=false;
  const el=document.getElementById('pf-image-preview');
  if(el) el.innerHTML=`<img src="${escapeAttr(dataUrl)}" alt="">`;
  updateProductEditorSummary();
}
const nativeProductImageReads=new Map();
function readNativeProductImage(id){if(!id||!window.webkit?.messageHandlers?.photoPicker)return Promise.resolve(null);return new Promise(resolve=>{const requestId=uid();const timeout=setTimeout(()=>{nativeProductImageReads.delete(requestId);resolve(null)},3000);nativeProductImageReads.set(requestId,data=>{clearTimeout(timeout);resolve(data)});window.webkit.messageHandlers.photoPicker.postMessage({action:'read',id,requestId})})}
function handleNativeProductImageRead(requestId,dataUrl){const done=nativeProductImageReads.get(requestId);if(done){nativeProductImageReads.delete(requestId);done(dataUrl)}}

function handleProductImage(input){
  const file=input?.files?.[0]; if(!file) return;
  const session=window._pmSession;
  const reader=new FileReader();
  reader.onload=()=>{
    const img=new Image();
    img.onload=()=>{
      if(session!==window._pmSession || !document.getElementById('pe-save'))return;
      let dataUrl='';
      const c=document.createElement('canvas');
      const ctx=c.getContext('2d');
      if(!ctx){flash('Не удалось подготовить фото');return;}
      for(let attempt=0;attempt<8 && !dataUrl;attempt++){
        const scale=Math.min(1,(1200*Math.pow(.75,attempt))/Math.max(img.width,img.height));
        c.width=Math.max(1,Math.floor(img.width*scale));c.height=Math.max(1,Math.floor(img.height*scale));
        ctx.fillStyle='#fff';ctx.fillRect(0,0,c.width,c.height);
        ctx.drawImage(img,0,0,c.width,c.height);
        for(const quality of [.82,.7,.55]){
          const candidate=c.toDataURL('image/jpeg',quality);
          if(candidate.startsWith('data:image/jpeg;base64,') && candidate.length<=800023){dataUrl=candidate;break;}
        }
      }
      if(!dataUrl){flash('Не удалось уменьшить фото. Выберите другое изображение.');return;}
      window._pmImageData=dataUrl;
      window._pmRemoveImage=false;
      const el=document.getElementById('pf-image-preview'); if(el) el.innerHTML=`<img src="${window._pmImageData}" alt="">`;
      updateProductEditorSummary();
    };
    img.src=reader.result;
  };
  reader.readAsDataURL(file);
}
function removeProductImage(){
  if(window._pmLocalImageId)window.webkit?.messageHandlers?.photoPicker?.postMessage({action:'remove',id:window._pmLocalImageId});
  window._pmImageData=null;window._pmLocalImageId=null;window._pmRemoveImage=true;
  const el=document.getElementById('pf-image-preview'); if(el) el.innerHTML='<div class="product-image-empty">📷 Фото будет удалено после сохранения</div>';
}
