document.addEventListener("DOMContentLoaded",()=>{
 const $=id=>document.getElementById(id),form=$("hero-edit-form"),editor=$("skill-editor"),save=$("save-hero"),msg=$("page-message"),code=decodeURIComponent(location.pathname.split("/").filter(Boolean).pop());
 const statKeys=["hp","atk","def","speed","crit","critDamage","accuracy","resistance","penFlat","penPercent","lifesteal"];
 const effectTypes=["DAMAGE","HEAL","BUFF","DEBUFF","CC","SHIELD","EVASION","BLOCK","REFLECT","IMMUNITY","DISPEL","REVIVE","EXTRA_ACTION","COUNTER","DOT","HOT"];
 const targets=[
  ["SELF","Bản thân người thi triển"],
  ["RANDOM","Ngẫu nhiên 1 mục tiêu"],
  ["RANDOM_2","Ngẫu nhiên tối đa 2 mục tiêu"],
  ["RANDOM_3","Ngẫu nhiên tối đa 3 mục tiêu"],
  ["FRONT","Toàn bộ hàng trước"],
  ["BACK","Toàn bộ hàng sau"],
  ["LOWEST_HP","Mục tiêu có HP hiện tại thấp nhất"],
  ["LOWEST_HP_PERCENT","Mục tiêu có % HP thấp nhất"],
  ["HIGHEST_HP_PERCENT","Mục tiêu có % HP cao nhất"],
  ["HIGHEST_ATK","Mục tiêu có ATK cao nhất"],
  ["LOWEST_ATK","Mục tiêu có ATK thấp nhất"],
  ["ALL_ENEMIES","Toàn bộ mục tiêu trong phe được chọn"],
  ["ALL_ALLIES","Toàn bộ mục tiêu trong phe được chọn (alias)"]
 ];
 const esc=v=>String(v??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
 const val=(id,v)=>$(id).value=v??"";
 let current;
 function message(s,ok=false){msg.hidden=false;msg.textContent=s;msg.classList.toggle("success",ok);}
 function stats(id,data,prefix){$(id).innerHTML=statKeys.map(k=>`<label>${k.toUpperCase()}<input type="number" min="0" step="any" required data-prefix="${prefix}" data-key="${k}" value="${esc(data[k]??0)}"></label>`).join("");}
 function configOf(s){try{const x=JSON.parse(s||"{}");return x&&typeof x==="object"&&!Array.isArray(x)?x:{};}catch(_){return {};}}
 function effectHtml(e,i){
  const c=configOf(e.configJson);
  const advanced={...c};["animation","animationDurationMs","impactTimeMs","flatValue","statType","statusType","immunityType","dispelType","permanent","trigger"].forEach(k=>delete advanced[k]);
  return `<article class="effect-card"><div class="effect-heading"><span>HIỆU ỨNG #${i+1}</span><label class="check-label"><input type="checkbox" data-enabled ${e.enabled===false?"":"checked"}> Kích hoạt</label></div>
  <div class="effect-fields">
  <label>Thứ tự<input data-order type="number" min="1" value="${esc(e.effectOrder??i+1)}"></label>
  <label>Loại hiệu ứng<select data-type>${effectTypes.map(x=>`<option ${x===e.effectType?"selected":""}>${x}</option>`).join("")}</select></label>
  <label>Mục tiêu<select data-target>${targets.map(([value,label])=>`<option value="${value}" ${value===e.targetType?"selected":""}>${label}</option>`).join("")}</select><small class="target-help">Phe mục tiêu được xác định theo loại hiệu ứng: sát thương/giảm chỉ số chọn địch; hồi máu/tăng chỉ số/chắn chọn đồng minh. COUNTER luôn đánh kẻ vừa gây sát thương.</small></label>
  <label>Hệ số tác động<input data-mult type="number" min="0" step="any" value="${esc(e.multiplier??0)}"></label>
  <label>Xác suất (0–1)<input data-chance type="number" min="0" max="1" step="any" value="${esc(e.chance??1)}"></label>
  <label>Duration (round)<input data-duration type="number" min="0" step="1" value="${esc(e.duration??0)}"></label>
  <label>Animation Spine<input data-animation value="${esc(c.animation??"")}"></label>
  <label>Thời lượng animation (ms)<input data-animation-duration type="number" min="0" value="${esc(c.animationDurationMs??1000)}"></label>
  <label>Thời điểm tác động (ms)<input data-impact-time type="number" min="0" value="${esc(c.impactTimeMs??0)}"></label>
  <label>Giá trị cộng cố định<input data-flat-value type="number" min="0" step="any" value="${esc(c.flatValue??0)}"></label>
  <label>Chỉ số BUFF/DEBUFF<select data-stat-type><option value="">Không áp dụng</option>${["ATK","DEF","SPEED","CRIT","CRIT_DAMAGE","ACCURACY","RESISTANCE","PEN_FLAT","PEN_PERCENT","LIFESTEAL"].map(x=>`<option ${x===c.statType?"selected":""}>${x}</option>`).join("")}</select></label>
  <label>Trạng thái CC<select data-status-type><option value="">Không áp dụng</option>${["STUN","FREEZE","SILENCE","TAUNT"].map(x=>`<option ${x===c.statusType?"selected":""}>${x}</option>`).join("")}</select></label>
  <label>Miễn nhiễm<select data-immunity-type><option value="">Không áp dụng</option>${["BUFF","DEBUFF","CC","DAMAGE"].map(x=>`<option ${x===c.immunityType?"selected":""}>${x}</option>`).join("")}</select></label>
  <label>Giải hiệu ứng<select data-dispel-type><option value="">Không áp dụng</option>${["BUFF","DEBUFF","CC"].map(x=>`<option ${x===c.dispelType?"selected":""}>${x}</option>`).join("")}</select></label>
  <label>Trigger nội tại<select data-trigger><option value="">Mặc định</option>${["BATTLE_START","ROUND_START","BEFORE_ACTION","AFTER_ACTION","ON_ATTACK","ON_DAMAGED","ON_KILL"].map(x=>`<option ${x===c.trigger?"selected":""}>${x}</option>`).join("")}</select></label>
  <label class="check-label"><input data-permanent type="checkbox" ${c.permanent===true?"checked":""}> Hiệu ứng vĩnh viễn</label>
  <label class="wide">Cấu hình nâng cao (JSON)<textarea data-config rows="4" spellcheck="false">${esc(JSON.stringify(advanced,null,2))}</textarea></label></div>
  <div class="effect-actions"><button type="button" class="small-button danger" data-remove>XÓA HIỆU ỨNG</button></div></article>`;
 }
 function skills(items){editor.innerHTML=items.map(s=>`<article class="skill-card" data-skill-code="${esc(s.skillCode)}" data-order="${s.skillOrder}">
  <div class="skill-heading"><h3>${String(s.skillOrder).padStart(2,"0")} · ${esc(s.skillName)}</h3><span>${esc(s.skillType)} · ${esc(s.skillCode)}</span></div>
  <div class="skill-fields"><label>Tên kỹ năng<input data-name required value="${esc(s.skillName)}"></label><label>Mã icon<input data-icon value="${esc(s.iconCode)}"></label><label class="check-label"><input type="checkbox" data-skill-enabled ${s.enabled===false?"":"checked"}> Kích hoạt</label><label class="wide">Mô tả<textarea data-description rows="2">${esc(s.description)}</textarea></label></div>
  <div class="effect-list">${(s.effects||[]).map(effectHtml).join("")}</div><button type="button" class="small-button add-effect" data-add>＋ THÊM HIỆU ỨNG</button></article>`).join("");}
 function fill(h){
  current=h;$("edit-title").textContent="CHỈNH SỬA · "+h.heroName;$("edit-subtitle").textContent=h.heroCode+" · "+h.rarity+" · "+h.role;
  val("hero-code",h.heroCode);val("hero-name",h.heroName);val("hero-rarity",h.rarity);val("hero-role",h.role);val("hero-faction",h.faction);val("hero-avatar",h.avatarCode);val("hero-sort",h.sortOrder);val("hero-description",h.description);val("basic-attack-movement",h.basicAttackMovement||"DASH");$("hero-enabled").checked=h.enabled!==false;
  $("asset-info").textContent="Skeleton: "+(h.spineSkeleton||"chưa có")+" · Atlas: "+(h.spineAtlas||"chưa có")+" · Texture: "+(h.spineTextures||[]).length+" file";
  stats("base-stats",h.baseStats,"base");stats("growth-stats",h.growthStats,"growth");skills(h.skills||[]);
 }
 function collectStats(prefix){const o={};document.querySelectorAll('[data-prefix="'+prefix+'"]').forEach(x=>o[x.dataset.key]=Number(x.value));return o;}
 function collect(){
  return {heroName:$("hero-name").value.trim(),rarity:$("hero-rarity").value,role:$("hero-role").value,faction:$("hero-faction").value.trim(),avatarCode:$("hero-avatar").value.trim(),description:$("hero-description").value,enabled:$("hero-enabled").checked,sortOrder:Number($("hero-sort").value||0),basicAttackMovement:$("basic-attack-movement").value,baseStats:collectStats("base"),growthStats:collectStats("growth"),skills:[...editor.querySelectorAll(".skill-card")].map(s=>({skillCode:s.dataset.skillCode,skillOrder:Number(s.dataset.order),skillName:s.querySelector("[data-name]").value.trim(),description:s.querySelector("[data-description]").value,iconCode:s.querySelector("[data-icon]").value,enabled:s.querySelector("[data-skill-enabled]").checked,effects:[...s.querySelectorAll(".effect-card")].map(e=>{
   let c;try{c=JSON.parse(e.querySelector("[data-config]").value||"{}");}catch(_){throw Error("JSON cấu hình hiệu ứng không hợp lệ ở kỹ năng "+s.dataset.order);}
   if(!c||typeof c!=="object"||Array.isArray(c))throw Error("Cấu hình hiệu ứng phải là JSON object.");
   const a=e.querySelector("[data-animation]").value.trim();if(a)c.animation=a;else delete c.animation;
   c.animationDurationMs=Number(e.querySelector("[data-animation-duration]").value||0);c.impactTimeMs=Number(e.querySelector("[data-impact-time]").value||0);
   c.flatValue=Number(e.querySelector("[data-flat-value]").value||0);
   for(const [selector,key] of [["[data-stat-type]","statType"],["[data-status-type]","statusType"],["[data-immunity-type]","immunityType"],["[data-dispel-type]","dispelType"],["[data-trigger]","trigger"]]){const v=e.querySelector(selector).value.trim();if(v)c[key]=v;else delete c[key];}
   c.permanent=e.querySelector("[data-permanent]").checked;
   const ch=e.querySelector("[data-chance]").value;
   return {effectOrder:Number(e.querySelector("[data-order]").value),effectType:e.querySelector("[data-type]").value,targetType:e.querySelector("[data-target]").value,multiplier:Number(e.querySelector("[data-mult]").value),chance:ch===""?null:Number(ch),duration:Number(e.querySelector("[data-duration]").value),configJson:JSON.stringify(c),enabled:e.querySelector("[data-enabled]").checked};
  })}))};
 }
 async function load(){try{const r=await fetch("/admin/api/heroes/"+encodeURIComponent(code));const d=await r.json();if(!r.ok)throw Error(d.detail||d.message||"Không tải được dữ liệu.");fill(d);}catch(e){message(e.message);save.disabled=true;}}
 editor.addEventListener("click",e=>{
  if(e.target.closest("[data-add]")){const list=e.target.closest(".skill-card").querySelector(".effect-list"),n=list.querySelectorAll(".effect-card").length;const box=document.createElement("div");box.innerHTML=effectHtml({effectType:"DAMAGE",targetType:"ALL_ENEMIES",effectOrder:n+1,multiplier:1,chance:1,duration:0,configJson:"{}",enabled:true},n);list.append(box.firstElementChild);}
  if(e.target.closest("[data-remove]")){const card=e.target.closest(".effect-card");if(card.parentElement.querySelectorAll(".effect-card").length<=1){message("Mỗi kỹ năng cần ít nhất một hiệu ứng.");return;}card.remove();}
 });
 form.addEventListener("submit",async e=>{e.preventDefault();if(!form.reportValidity())return;save.disabled=true;save.textContent="ĐANG LƯU...";msg.hidden=true;try{
  const payload=collect(),headers={"Content-Type":"application/json","Accept":"application/json"},token=document.querySelector('meta[name="_csrf"]')?.content,hdr=document.querySelector('meta[name="_csrf_header"]')?.content;if(token&&hdr)headers[hdr]=token;
  const r=await fetch("/admin/api/heroes/"+encodeURIComponent(code),{method:"PUT",credentials:"same-origin",headers,body:JSON.stringify(payload)}),d=await r.json().catch(()=>({}));if(!r.ok)throw Error(d.detail||d.message||"Lưu cấu hình thất bại.");fill(d);message("Đã lưu thông tin tướng, chỉ số, kỹ năng và hiệu ứng.",true);
 }catch(err){message(err.message||"Lưu thất bại.");}finally{save.disabled=false;save.textContent="LƯU THAY ĐỔI";}});
 load();
});
