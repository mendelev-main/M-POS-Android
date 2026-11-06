import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import './measure.js';
const repo=path.resolve(import.meta.dirname,'../..'),out=path.join(repo,'output/playwright/web-reference');
const server=spawn('python3',['scripts/web-visual-reference.py','--source',process.env.MPOS_REFERENCE_SOURCE||'../reference','--port','8765'],{cwd:repo,stdio:'inherit'});
let browser;
try {
  for(let n=0;n<100;n++){try{if((await fetch('http://127.0.0.1:8765/manifest.json')).ok)break;}catch{}await new Promise(r=>setTimeout(r,100));}
  const manifest=JSON.parse(await fs.readFile(path.join(out,'manifest.json'),'utf8'));
  browser=await chromium.launch({headless:true});
  const options={viewport:{width:1280,height:900},deviceScaleFactor:1.25,locale:'ru-RU',timezoneId:'Europe/Moscow',reducedMotion:'reduce'};
  await fs.mkdir(path.join(out,'screenshots'),{recursive:true});await fs.mkdir(path.join(out,'measurements'),{recursive:true});
  const failures=[],measurements={},results=[];
  for(const theme of ['light','dark'])for(const scene of manifest.scenes){
    const key=scene[0]+'-'+theme,context=await browser.newContext(options);
    await context.route('**/*',route=>new URL(route.request().url()).hostname==='127.0.0.1'?route.continue():route.abort());
    const page=await context.newPage();
    try{
      await page.goto(`http://127.0.0.1:8765/source/pos.html?scene=${scene[0]}&theme=${theme}`,{waitUntil:'networkidle'});
      await page.waitForFunction(id=>document.documentElement.dataset.referenceReady===id||document.documentElement.dataset.referenceError,scene[0],{timeout:15000});
      const error=await page.locator('html').getAttribute('data-reference-error');assert.equal(error,null,key+': '+error);
      assert.equal(await page.locator('html').getAttribute('data-reference-font'),'loaded',key+': Manrope missing');
      // Modal scenes must expose a real overlay/editor, not merely the underlying page.
      if(!['W01','W02','W03','W05','W06','C06','P01','P02','P03','P04','P05','P08','T01','S01','S02','R01','A01','K01','K03','K04','Q01','Q02','I01','I05','N01','N02'].includes(scene[0])){
        assert.ok(await page.locator('#modal-root .modal, #product-editor-root > *, #receiving-page-root > *, #payment-page-root').count(),key+': missing source overlay');
      }
      assert.equal(await page.locator('.flash-toast').count(),0,key+': unexpected fixture notification');
      const metrics=await page.evaluate(globalThis.measureReference);assert.equal(metrics.fontLoaded,true);
      await page.screenshot({path:path.join(out,'screenshots',key+'.png'),animations:'disabled'});
      measurements[key]=metrics;await fs.writeFile(path.join(out,'measurements',key+'.json'),JSON.stringify(metrics,null,2));
      results.push({id:scene[0],theme,status:'captured'});console.log('CAPTURED',key,scene[1]);
    }catch(error){failures.push({id:scene[0],theme,error:String(error)});console.error('FAILED',key,String(error));await page.screenshot({path:path.join(out,'screenshots',key+'-ERROR.png')}).catch(()=>{});}
    finally{await context.close();}
  }
  manifest.captured=failures.length===0;manifest.measurements=measurements;manifest.capture={results,failures,browser:browser.version(),capturedAt:new Date().toISOString(),nativePixelComparison:'pending'};
  await fs.writeFile(path.join(out,'manifest.json'),JSON.stringify(manifest,null,2));await fs.writeFile(path.join(out,'manifest.js'),'window.REFERENCE_MANIFEST='+JSON.stringify(manifest)+';');
  await fs.writeFile(path.join(out,'capture-report.json'),JSON.stringify(manifest.capture,null,2));
  assert.equal(failures.length,0,JSON.stringify(failures,null,2));
  console.log('WEB REFERENCE PASSED:',results.length,'PNG;',manifest.scenes.length,'scenes × 2 themes; native comparison remains pending');
}finally{await browser?.close();server.kill();}
