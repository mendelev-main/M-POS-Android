(() => {
  const manifest=window.REFERENCE_MANIFEST,scenes=manifest.scenes,$=id=>document.getElementById(id);
  const savedKey='mpos-visual-review:'+manifest.reference,reviewed=new Set(JSON.parse(localStorage.getItem(savedKey)||'[]'));
  let current=decodeURIComponent(location.hash.slice(1))||'W02',imageUrl=null;
  $('source').textContent='Web '+manifest.reference.slice(0,7)+' · '+scenes.length+' окон/состояний';
  function nav(){let group='';$('scenes').replaceChildren();const query=$('search').value.toLowerCase();for(const s of scenes){if(!(s[0]+' '+s[1]+' '+s[2]).toLowerCase().includes(query))continue;if(s[2]!==group){group=s[2];const h=document.createElement('h2');h.textContent=group;$('scenes').append(h);}const b=document.createElement('button');b.className=s[0]===current?'active':'';const span=document.createElement('span');span.textContent=s[0];b.append(span,document.createTextNode(s[1]+(reviewed.has(s[0]+':'+$('theme').value)?' ✓':'')));b.onclick=()=>select(s[0]);$('scenes').append(b);}}
  function resize(){const scale=$('zoom').value==='fit'?Math.min(1,$('viewport').clientWidth/1280):Number($('zoom').value);$('stage').style.transform=`scale(${scale})`;$('viewport').style.height=(900*scale+2)+'px';}
  function mode(){const v=$('mode').value,has=!!imageUrl;$('native-image').style.display=has&&v!=='reference'?'block':'none';$('native-image').style.opacity=v==='overlay'?Number($('opacity').value)/100:1;$('native-image').style.mixBlendMode=v==='difference'?'difference':'normal';}
  function select(id){const s=scenes.find(s=>s[0]===id)||scenes[0];current=s[0];location.hash=current;const theme=$('theme').value,key=current+'-'+theme;$('title').textContent=s[0]+' · '+s[1];$('group').textContent=s[2];$('position').textContent=(scenes.indexOf(s)+1)+' / '+scenes.length;
    const url='source/pos.html?scene='+encodeURIComponent(current)+'&theme='+theme;$('live').href=url;
    if(manifest.captured){$('live').href='screenshots/'+key+'.png';$('live').textContent='Открыть PNG 1:1';$('frame').style.display='none';$('golden').style.display='block';$('golden').src='screenshots/'+key+'.png';$('status').textContent='PNG исходного Web · 1600×1125 · область 1280×900 CSS px, DPR 1,25 · '+theme+' · native ещё не сверен';}
    else{$('golden').style.display='none';$('frame').style.display='block';$('frame').src=url;$('status').textContent='Открываю исходное Web-окно…';}
    if(imageUrl){URL.revokeObjectURL(imageUrl);imageUrl=null;$('native-image').removeAttribute('src');$('native').value='';$('mode').value='reference';}
    $('measurements').textContent='Выберите «Размеры и CSS»';nav();resize();mode();
  }
  $('search').oninput=nav;$('theme').onchange=()=>select(current);$('zoom').onchange=resize;window.onresize=resize;
  $('prev').onclick=()=>select(scenes[(scenes.findIndex(s=>s[0]===current)+scenes.length-1)%scenes.length][0]);$('next').onclick=()=>select(scenes[(scenes.findIndex(s=>s[0]===current)+1)%scenes.length][0]);
  $('mode').onchange=mode;$('opacity').oninput=mode;
  $('native').onchange=()=>{const file=$('native').files[0];if(!file)return;if(imageUrl)URL.revokeObjectURL(imageUrl);imageUrl=URL.createObjectURL(file);const image=$('native-image');image.onload=()=>{const valid=image.naturalWidth===1600&&image.naturalHeight===1125;$('status').textContent=valid?'Native PNG: 1600×1125. Совместите одинаковые данные, состояние и область приложения.':'Размер native: '+image.naturalWidth+'×'+image.naturalHeight+'. Для строгой проверки нужен PNG 1600×1125 без системных панелей. Масштабирование сейчас только для просмотра.';};image.src=imageUrl;$('mode').value='overlay';mode();};
  $('metrics').onclick=()=>{const key=current+'-'+$('theme').value;let result=manifest.measurements?.[key];try{result??=$('frame').contentWindow.measureReference();}catch{}$('measurements').textContent=result?JSON.stringify(result,null,2):'Замеры ещё не сняты. Запустите scripts/web-visual-reference.py для живого Web-эталона.';$('details').open=true;};
  $('review').onclick=()=>{reviewed.has(current+':'+$('theme').value)?reviewed.delete(current+':'+$('theme').value):reviewed.add(current+':'+$('theme').value);localStorage.setItem(savedKey,JSON.stringify([...reviewed]));nav();};
  window.addEventListener('message',e=>{if(e.origin!==location.origin||e.source!==$('frame').contentWindow||e.data.id!==current)return;if(e.data.type==='mpos-reference-ready')$('status').textContent='Исходное Web-окно готово · 1280×900 CSS px. Шрифт Manrope. Native ещё не сверен.';if(e.data.type==='mpos-reference-error')$('status').textContent='Ошибка эталона: '+e.data.error;});
  window.onhashchange=()=>{const id=decodeURIComponent(location.hash.slice(1));if(id!==current)select(id);};select(current);
})();
