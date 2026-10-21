(function(){
'use strict';
var el=function(id){return document.getElementById(id);};
var state=el('state'),dest=el('destination'),label=el('label'),status=el('actionStatus'),
error=el('error'),diagnostics=el('diagnostics'),photo=el('photo'),photoText=el('photoText'),
photoSection=el('photoSection'),detail=el('detail'),toggle=el('autoToggle'),autoStatus=el('autoStatus');
var links={native:el('native'),alternative:el('alternative'),webRoute:el('webRoute'),pin:el('pin')};
var lastRevision=null,lastPhoto=null,attempting=false, pending=null;
var mem={};
function read(k){try{return localStorage.getItem('byd0212-'+k);}catch(e){return mem[k]||null;}}
function write(k,v){mem[k]=String(v);try{localStorage.setItem('byd0212-'+k,String(v));}catch(e){}}
var mode=read('auto'),auto=mode===null?true:mode==='on';
function valid(q){
 var m=/^([-+]?\d+(?:\.\d+)?)\s*,\s*([-+]?\d+(?:\.\d+)?)$/.exec(String(q||'').trim());
 if(!m)return false;
 var a=Number(m[1]),b=Number(m[2]);
 return isFinite(a)&&isFinite(b)&&Math.abs(a)<=90&&Math.abs(b)<=180&&!(Math.abs(a)<.000001&&Math.abs(b)<.000001);
}
function nativeUri(q){
 return 'intent:q='+encodeURIComponent(q)+'&mode=d#Intent;scheme=google.navigation;'+
  'package=com.google.android.apps.maps;action=android.intent.action.VIEW;end';
}
function imageEvent(d){
 return !!(d&&d.photoAvailable===true&&
 typeof d.photoUrl==='string'&&/^\/api\/photo\?rev=\d+$/.test(d.photoUrl)&&
 typeof d.label==='string'&&d.label.indexOf('GPS จากภาพ ')===0&&valid(d.destination));
}
function token(d){return String(d.revision)+'-'+String(d.sentAtMs);}
function showMode(){
 toggle.textContent=auto?'ปิดโหมดทดลองเปิดนำทางอัตโนมัติ':'เปิดโหมดทดลองเปิดนำทางอัตโนมัติ';
 toggle.className=auto?'on':'off';
 autoStatus.textContent=auto?
   'เปิด • ทดลองเปิด Maps ตรงหนึ่งครั้งเมื่อได้รับภาพ GPS ใหม่':
   'ปิด • กดปุ่มนำทางสีเขียวได้ตามปกติ';
}
function showImage(d){
 var visible=imageEvent(d);
 photoSection.hidden=!visible;
 if(!visible){
   photo.removeAttribute('src');photo.hidden=true;lastPhoto=null;return;
 }
 photoText.textContent=d.label;
 // Do not load a thumbnail until the user opens diagnostics.
 if(!diagnostics.open)return;
 if(lastPhoto!==d.photoUrl){
   lastPhoto=d.photoUrl;photo.hidden=false;photo.src=d.photoUrl;
 }
}
photo.addEventListener('error',function(){
 photo.hidden=true;photoText.textContent='ไม่สามารถแสดงภาพย่อได้ แต่ GPS ยังนำทางได้';
});
diagnostics.addEventListener('toggle',function(){
 if(diagnostics.open && pendingImage)showImage(pendingImage);
});
var pendingImage=null;
function present(d){
 var ok=valid(d.destination);
 dest.textContent=ok?d.destination:'ไม่มีพิกัด GPS ที่ถูกต้อง';
 label.textContent=d.label+' • ครั้งที่ '+d.revision;
 links.native.href=nativeUri(d.destination);
 links.alternative.href='google.navigation:q='+encodeURIComponent(d.destination)+'&mode=d';
 links.webRoute.href=d.directionsUrl;
 links.pin.href=d.searchUrl;
 Object.keys(links).forEach(function(k){
   if(ok)links[k].classList.remove('muted');else links[k].classList.add('muted');
 });
 error.textContent=ok?'':'ยังไม่สามารถนำทางได้: ต้องมีพิกัด GPS จริง ไม่ใช่ 0,0';
 pendingImage=d;showImage(d);
}
function launchAutomatic(d){
 if(!auto||attempting||!pending||document.hidden||document.visibilityState!=='visible')return;
 if(!imageEvent(d)){pending=null;return;}
 var key=token(d);
 if(read('attempted')===key){pending=null;return;}
 pending=null;attempting=true;
 write('attempted',key); // Avoid repeated navigation when returning to Browser.
 status.textContent='✓ รับภาพ GPS แล้ว • กำลังทดลองเปิด Google Maps โดยตรง…';
 // Chrome may reject external app launches from background or JavaScript without a user gesture.
 // Never synthesize clicks, automate Android prompt acceptance or use web fallback redirects.
 window.location.assign(nativeUri(d.destination));
 window.setTimeout(function(){
   attempting=false;
   status.textContent='ส่งคำสั่งแล้ว • หากยังอยู่หน้านี้ ให้กดปุ่มสีเขียวครั้งเดียว';
 },1500);
}
function check(){
 fetch('/api/state?t='+Date.now(),{cache:'no-store'}).then(function(r){
   if(!r.ok)throw Error('HTTP '+r.status);return r.json();
 }).then(function(d){
   state.textContent='✓ เชื่อมต่อ Web Bridge สำเร็จ • รับข้อมูลจาก Android หลัก';
   state.className='success';
   present(d);
   var rev=Number(d.revision),ms=Number(d.sentAtMs),now=Date.now();
   var fresh=ms>0&&ms<=now&&now-ms<120000;
   var key=token(d);
   if(lastRevision===null){
     lastRevision=rev;
     if(auto&&fresh&&imageEvent(d)&&read('attempted')!==key)pending=d;
   }else if(rev!==lastRevision){
     lastRevision=rev;pending=null;attempting=false;
     if(auto&&fresh&&imageEvent(d)&&read('attempted')!==key)pending=d;
     if(!auto)status.textContent='✓ ได้รับภาพใหม่แล้ว • กดปุ่มสีเขียวเพื่อเริ่มนำทาง';
   }
   detail.textContent='revision '+d.revision+' • '+d.feedback;
   if(auto&&pending)launchAutomatic(pending);
 }).catch(function(e){
   state.textContent='× ไม่สามารถเชื่อมต่อได้: '+e.message;
   state.className='error';
   error.textContent='เปิดแอป V0.2.12 บน Android หลัก และตรวจบริการ 127.0.0.1:8770';
 });
}
links.native.addEventListener('click',function(){
 status.textContent='กดเปิด Google Maps แล้ว • กำลังเข้าสู่ระบบนำทาง';
});
toggle.addEventListener('click',function(){
 auto=!auto;write('auto',auto?'on':'off');
 if(!auto)pending=null;
 showMode();check();
});
el('retry').addEventListener('click',check);
document.addEventListener('visibilitychange',function(){if(!document.hidden)check();});
window.addEventListener('pageshow',check);
showMode();check();window.setInterval(check,1100);
})();