(function(){
'use strict';
var el=function(id){return document.getElementById(id);};
var state=el('state'),dest=el('destination'),label=el('label');
var detail=el('detail'),danger=el('danger'),tip=el('tip');
var autoStatus=el('autoStatus'),autoButton=el('autoToggle');
var links={native:el('native'),intent:el('intent'),route:el('route'),pin:el('pin')};
var seen=null,pending=null,going=false,store={};
function get(k){try{return localStorage.getItem('byd029-'+k);}catch(e){return store[k]||null;}}
function put(k,v){store[k]=String(v);try{localStorage.setItem('byd029-'+k,String(v));}catch(e){}}
var setting=get('auto'),armed=setting===null?true:setting==='yes';
function valid(q){
 if(typeof q!=='string'||!q.trim())return false;
 var m=/^([-+]?\d+(?:\.\d+)?)\s*,\s*([-+]?\d+(?:\.\d+)?)$/.exec(q.trim());
 if(!m)return true;
 var a=Number(m[1]),b=Number(m[2]);
 return isFinite(a)&&isFinite(b)&&Math.abs(a)<=90&&Math.abs(b)<=180&&!(Math.abs(a)<.000001&&Math.abs(b)<.000001);
}
function nativeUri(q){return 'google.navigation:q='+encodeURIComponent(q)+'&mode=d';}
function intentUri(q,fallback){
 return 'intent://www.google.com/maps/dir/?api=1&destination='+encodeURIComponent(q)+
 '&travelmode=driving&dir_action=navigate#Intent;scheme=https;package=com.google.android.apps.maps;'+
 'action=android.intent.action.VIEW;S.browser_fallback_url='+encodeURIComponent(fallback)+';end';
}
function showArmed(){
 autoButton.textContent=armed?'ปิดโหมดนำทางอัตโนมัติ':'เปิดโหมดนำทางอัตโนมัติ (ทดลอง)';
 autoButton.className=armed?'toggle enabled':'toggle';
 autoStatus.textContent=armed?'เปิดอยู่ • จะทดลองเปิดแอป Maps และใช้หน้าเว็บสำรอง':'ปิดอยู่ • ใช้ปุ่มเริ่มนำทางสีเขียวด้านบน';
}
function showLinks(d){
 var ok=valid(d.destination);dest.textContent=d.destination;
 label.textContent=d.label+' • ข้อมูลครั้งที่ '+d.revision;
 links.native.href=nativeUri(d.destination);
 links.intent.href=intentUri(d.destination,d.directionsUrl);
 links.route.href=d.directionsUrl;links.pin.href=d.searchUrl;
 Object.keys(links).forEach(function(k){
 if(ok)links[k].classList.remove('muted');else links[k].classList.add('muted');
 });
 danger.textContent=ok?'':'ไม่เปิดนำทาง: พิกัดเป็น 0,0 หรือผิดรูปแบบ';return ok;
}
function tryAutomatic(){
 if(!armed||going||!pending||document.hidden||document.visibilityState!=='visible')return;
 var d=pending;pending=null;if(!valid(d.destination))return;
 var token=String(d.revision)+'-'+String(d.sentAtMs||0);
 if(get('launched')===token)return;
 put('launched',token);going=true;
 tip.textContent='กำลังลองเปิด Maps • หาก Browser แสดงเส้นทาง ให้กด “เริ่ม” ตามปกติ';
 window.location.assign(intentUri(d.destination,d.directionsUrl));
 window.setTimeout(function(){going=false;},3500);
}
function poll(){
 fetch('/api/state?at='+Date.now(),{cache:'no-store'}).then(function(resp){
 if(!resp.ok)throw Error('HTTP '+resp.status);return resp.json();
 }).then(function(d){
 state.textContent='✓ เชื่อมต่อสำเร็จ — GPack Browser รับ GPS จาก Android หลักได้';
 state.className='ok';var ok=showLinks(d);
 detail.textContent='จุดหมายล่าสุด • revision '+d.revision+' • '+d.feedback;
 var rev=Number(d.revision),time=Number(d.sentAtMs)||0,now=Date.now();
 var fresh=time>0&&now>=time&&now-time<120000;
 var key=String(rev)+'-'+String(time);
 if(seen===null){
   seen=rev;
   if(armed&&ok&&fresh&&get('launched')!==key)pending=d;
 }else if(rev!==seen){
   seen=rev;
   if(armed&&ok&&get('launched')!==key)pending=d;
 }
 tryAutomatic();
 }).catch(function(e){
 state.textContent='× Web Bridge ติดต่อไม่ได้: '+e.message;state.className='bad';
 detail.textContent='เปิด V0.2.9 บน Android หลัก ตรวจว่าบริการใช้พอร์ต 8767';
 });
}
autoButton.addEventListener('click',function(){
 armed=!armed;put('auto',armed?'yes':'no');if(!armed)pending=null;showArmed();
});
el('retry').addEventListener('click',poll);
document.addEventListener('visibilitychange',function(){if(!document.hidden){poll();tryAutomatic();}});
window.addEventListener('pageshow',poll);
showArmed();poll();window.setInterval(poll,1500);
})();