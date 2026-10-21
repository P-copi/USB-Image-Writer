(function(){
'use strict';
var el=function(id){return document.getElementById(id);};
var state=el('state'),dest=el('destination'),label=el('label'),
photo=el('photo'),photoSection=el('photoSection'),photoText=el('photoText'),
detail=el('detail'),danger=el('danger'),launchState=el('launchState'),
autoStatus=el('autoStatus'),autoButton=el('autoToggle');
var links={native:el('native'),intent:el('intent'),route:el('route'),pin:el('pin')};
var seenRevision=null,pending=null,going=false,activePhotoUrl=null,local={};
function get(key){try{return localStorage.getItem('byd0211-'+key);}catch(e){return local[key]||null;}}
function put(key,val){local[key]=String(val);try{localStorage.setItem('byd0211-'+key,String(val));}catch(e){}}
var setting=get('auto'),armed=setting===null?true:setting==='yes';
function valid(q){
 if(typeof q!=='string'||!q.trim())return false;
 var m=/^([-+]?\d+(?:\.\d+)?)\s*,\s*([-+]?\d+(?:\.\d+)?)$/.exec(q.trim());
 if(!m)return false; // Only verified GPS image coordinates auto-open native navigation.
 var a=Number(m[1]),b=Number(m[2]);
 return isFinite(a)&&isFinite(b)&&Math.abs(a)<=90&&Math.abs(b)<=180&&!(Math.abs(a)<.000001&&Math.abs(b)<.000001);
}
function nativeUri(q){
 return 'intent:q='+encodeURIComponent(q)+'&mode=d#Intent;scheme=google.navigation;'+
        'package=com.google.android.apps.maps;action=android.intent.action.VIEW;end';
}
function genericUri(q){return 'google.navigation:q='+encodeURIComponent(q)+'&mode=d';}
function isNewConfirmedPhoto(d){
 return Boolean(d&&d.photoAvailable&&d.photoUrl&&
        typeof d.label==='string'&&d.label.indexOf('GPS จากภาพ ')===0&&valid(d.destination));
}
function showAuto(){
 autoButton.textContent=armed?'ปิดโหมดนำทางอัตโนมัติ':'เปิดโหมดนำทางอัตโนมัติ';
 autoButton.className=armed?'toggle enabled':'toggle';
 autoStatus.textContent=armed?
  'เปิดอยู่ • ภาพ GPS ใหม่จะทดลองเปิดแอป Maps ทันทีเมื่อ Browser พร้อมทำงาน':
  'ปิดอยู่ • ใช้ปุ่มสีเขียวเพื่อเริ่มนำทาง';
}
function updatePhoto(d){
 var available=isNewConfirmedPhoto(d)&&/^\/api\/photo\?rev=\d+$/.test(d.photoUrl);
 photoSection.hidden=!available;
 if(available){
   photoText.textContent=d.label;
   if(activePhotoUrl!==d.photoUrl){
     activePhotoUrl=d.photoUrl;
     photo.hidden=false;
     photo.src=d.photoUrl;
   }
 }else{
   activePhotoUrl=null;
   photo.removeAttribute('src');
   photo.hidden=true;
 }
}
photo.addEventListener('error',function(){
 photo.hidden=true;photoText.textContent='รับพิกัด GPS แล้ว แต่ภาพย่อโหลดไม่ได้ • ยังนำทางได้';
});
function showDestination(d){
 var ok=valid(d.destination);
 dest.textContent=d.destination;label.textContent=d.label+' • ครั้งที่ '+d.revision;
 links.native.href=nativeUri(d.destination);
 links.intent.href=genericUri(d.destination);
 links.route.href=d.directionsUrl;
 links.pin.href=d.searchUrl;
 Object.keys(links).forEach(function(k){
   if(ok)links[k].classList.remove('muted');
   else links[k].classList.add('muted');
 });
 danger.textContent=ok?'':'พิกัด GPS ไม่ถูกต้อง • ระบบจะไม่เปิดนำทางไปยัง 0,0';
 updatePhoto(d);
 return ok;
}
function attemptLaunch(){
 if(!armed||going||!pending||document.hidden||document.visibilityState!=='visible')return;
 var d=pending;
 if(!isNewConfirmedPhoto(d)){pending=null;return;}
 pending=null;
 var token=String(d.revision)+'-'+String(d.sentAtMs);
 if(get('attempt')===token)return;
 put('attempt',token); // avoid endless browser->Maps->browser loops
 going=true;
 launchState.textContent='✓ รับภาพและ GPS แล้ว • กำลังทดลองเปิด Google Maps โดยตรง…';
 // This is the same direct intent link confirmed to start native navigation when clicked.
 // Chromium may reject a non-user-initiated external app launch. Never synthesize clicks.
 window.setTimeout(function(){
   if(document.hidden){going=false;return;}
   window.location.assign(nativeUri(d.destination));
   window.setTimeout(function(){
     going=false;
     launchState.textContent='ส่งคำสั่งเปิด Maps แล้ว • หากยังอยู่หน้านี้ Chrome อาจบล็อก ให้แตะปุ่มสีเขียวเพียงครั้งเดียว';
   },1100);
 },650);
}
function poll(){
 fetch('/api/state?t='+Date.now(),{cache:'no-store'}).then(function(r){
   if(!r.ok)throw Error('HTTP '+r.status);return r.json();
 }).then(function(d){
   state.textContent='✓ เชื่อมต่อสำเร็จ • ได้รับข้อมูลภาพ GPS จาก Android หลัก';
   state.className='ok';
   var ok=showDestination(d);
   detail.textContent='revision '+d.revision+' • ส่งเมื่อ '+new Date(d.sentAtMs).toLocaleTimeString('th-TH')+' • '+d.feedback;
   var rev=Number(d.revision),now=Date.now(),sent=Number(d.sentAtMs)||0;
   // Restrict automatic launches to newly confirmed GPS photos, not stale destinations or samples.
   var fresh=sent>0&&now>=sent&&now-sent<300000;
   var token=String(rev)+'-'+String(sent);
   if(seenRevision===null){
     seenRevision=rev;
     if(armed&&ok&&fresh&&isNewConfirmedPhoto(d)&&get('attempt')!==token)pending=d;
   }else if(rev!==seenRevision){
     seenRevision=rev;pending=null;going=false;
     if(armed&&ok&&fresh&&isNewConfirmedPhoto(d)&&get('attempt')!==token)pending=d;
   }
   if(!armed)pending=null;
   if(pending&&armed&&!going)attemptLaunch();
 }).catch(function(e){
   state.textContent='× เชื่อมต่อไม่ได้: '+e.message;state.className='bad';
   detail.textContent='เปิดแอป BYD Web Bridge V0.2.11 ให้กำลังฟังพอร์ต 8769';
 });
}
links.native.addEventListener('click',function(){
 launchState.textContent='ผู้ใช้กดเปิดแอป Google Maps โดยตรง • รอดูผลบนจอ';
});
autoButton.addEventListener('click',function(){
 armed=!armed;put('auto',armed?'yes':'no');
 if(!armed)pending=null;
 showAuto();poll();
});
el('retry').addEventListener('click',poll);
document.addEventListener('visibilitychange',function(){if(!document.hidden)poll();});
window.addEventListener('pageshow',poll);
showAuto();poll();window.setInterval(poll,850);
})();