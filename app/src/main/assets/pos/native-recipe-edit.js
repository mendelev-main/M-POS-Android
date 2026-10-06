(function(global){
  'use strict';
  if(!global.MPosCore?.RecipeEdit)return;
  const enabled=()=>global.MPosNativeRecipeEditEnabled!==false;
  const value=id=>document.getElementById(id)?.value||'';
  const signature=p=>JSON.stringify([p?.id,p?.name,p?.type,p?.components]);
  const stamp=()=>JSON.stringify([state.products,global._pmEditingId,global._pmType,global._pmComponents,global._pmModifierGroups,value('pf-name'),value('pf-recipe-yield'),global._pmRecipeYield,global.MPosCore.OrderContext?.generation()]);
  let recipe=null,modifiers=null,preparing=false;
  async function read(input){
    const before=stamp();let result;try{result=await global.MPosCore.RecipeEdit.calculate(JSON.parse(JSON.stringify(input,(_key,v)=>{if(typeof v==='number'&&!Number.isFinite(v))throw Error('non-finite editor input');return v;})));}catch(error){if(!enabled()||before!==stamp())return null;throw error;}
    if(!enabled()||before!==stamp())return null;
    if(typeof result?.allowed!=='boolean')throw Error('invalid recipe decision');
    if(!result.allowed){flash(result.message||'Проверьте состав товара');return null;}
    return result;
  }
  global.MPosCore.ProductRecipes=Object.freeze({enabled,
    async prepareModifiers(editingId){
      if(!enabled())return true;
      const groups=global._pmModifierGroups||[];
      // Preserve reviewed legacy Infinity normalization through explicit compatibility.
      const infinite=v=>{const n=Number(v);return n===Infinity||n===-Infinity;};
      if(groups.some(g=>[g?.max,g?.min].some(infinite)||[g?.id,g?.name].some(v=>typeof v==='number'&&!Number.isFinite(v))||(Array.isArray(g?.options)?g.options:[]).some(o=>[o.qty,o.priceDelta].some(infinite)||[o.id,o.productId,o.posName].some(v=>typeof v==='number'&&!Number.isFinite(v))))){modifiers=null;return true;}
      const projected=groups.map(g=>{const row={};for(const key of ['id','name','min','max'])if(g&&Object.prototype.hasOwnProperty.call(g,key))row[key]=g[key];row.options=(Array.isArray(g?.options)?g.options:[]).map(o=>{const option={};for(const key of ['id','productId','posName','qty','priceDelta'])if(Object.prototype.hasOwnProperty.call(o,key))option[key]=o[key];return option;});return row;});
      const generatedIds=groups.map(g=>({id:g?.id||uid(),options:(Array.isArray(g?.options)?g.options:[]).map(o=>o.id||uid())}));
      const result=await read({version:1,operation:'modifiers',editingId:editingId??null,type:global._pmType,recipeYield:value('pf-recipe-yield')||global._pmRecipeYield||1,groups:projected,generatedIds});
      if(!result)return false;
      if(!Array.isArray(result.groups)||result.groups.length!==groups.length||result.groups.some(g=>!g||!Array.isArray(g.options)||typeof g.name!=='string'||!Number.isFinite(g.min)||!Number.isFinite(g.max)||g.options.some(o=>!o||typeof o.posName!=='string'||!Number.isFinite(o.qty)||!Number.isFinite(o.priceDelta))))throw Error('invalid normalized modifiers');
      const rows=new WeakMap();groups.forEach((g,i)=>{if(g&&typeof g==='object')rows.set(g,{input:JSON.stringify(g),result:result.groups[i]});});
      modifiers={groups,stamp:stamp(),rows,editingId};return true;
    }
  });
  const ingredients=global.productIngredients;
  global.productIngredients=function(p){
    if(enabled()&&arguments.length===1&&recipe&&recipe.stamp===stamp()&&recipe.signature===signature(p))return new Map(recipe.ingredients.map(i=>[i.productId,i.qty]));
    return ingredients.apply(this,arguments);
  };
  const validate=global.validateModifierGroups,normalize=global.normalizeModifierGroup;
  global.validateModifierGroups=function(groups,editingId){
    if(enabled()&&modifiers&&modifiers.groups===groups&&modifiers.stamp===stamp()&&(modifiers.editingId===editingId||(!modifiers.editingId&&!getProduct(editingId))))return;
    return validate.apply(this,arguments);
  };
  global.normalizeModifierGroup=function(group){
    const cached=enabled()&&modifiers?modifiers.rows.get(group):null;
    return cached&&cached.input===JSON.stringify(group)?JSON.parse(JSON.stringify(cached.result)):normalize.apply(this,arguments);
  };
  const save=global.saveProduct;
  global.saveProduct=async function(editingId){
    if(!enabled())return save.apply(this,arguments);
    if(preparing){flash('Дождитесь завершения изменения данных');return;}
    preparing=true;recipe=null;modifiers=null;
    try{
      if(global._pmType==='composite'&&(global._pmComponents||[]).length&&value('pf-name').trim()){
        const draft={id:editingId||null,name:value('pf-name').trim(),type:global._pmType,components:global._pmComponents};
        const result=await read({version:1,operation:'recipe',draft});if(!result)return;
        if(!Array.isArray(result.ingredients)||result.ingredients.some(i=>!i||!Number.isFinite(i.qty)||i.qty<=0))throw Error('invalid recipe ingredients');
        recipe={stamp:stamp(),signature:signature(draft),ingredients:result.ingredients};
      }
      return await save(editingId);
    }catch(error){flash('Не удалось проверить состав: '+(error?.message||'ошибка'));}
    finally{preparing=false;}
  };
})(window);
