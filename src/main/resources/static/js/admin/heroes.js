document.addEventListener("DOMContentLoaded",async()=>{
 const body=document.getElementById("hero-list"),search=document.getElementById("hero-search"),rarity=document.getElementById("rarity-filter"),status=document.getElementById("status-filter"),count=document.getElementById("hero-count"),message=document.getElementById("page-message");
 const esc=v=>String(v??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
 let heroes=[];
 function render(){
  const q=search.value.trim().toLowerCase();
  const rows=heroes.filter(h=>(!q||h.heroName.toLowerCase().includes(q)||h.heroCode.toLowerCase().includes(q))&&(!rarity.value||h.rarity===rarity.value)&&(!status.value||String(Boolean(h.enabled))===status.value));
  count.textContent=rows.length+" / "+heroes.length+" tướng";
  body.innerHTML=rows.length?rows.map(h=>`<tr><td><div class="hero-name">${esc(h.heroName)}</div><div class="hero-code">${esc(h.avatarCode)}</div></td><td><code>${esc(h.heroCode)}</code></td><td>${esc(h.rarity)}</td><td>${esc(h.role)}</td><td>${esc(h.faction||"—")}</td><td><span class="status ${h.enabled?"":"off"}">${h.enabled?"ĐANG BẬT":"ĐANG TẮT"}</span></td><td><a class="edit-link" href="/admin/heroes/${encodeURIComponent(h.heroCode)}">CHỈNH SỬA →</a></td></tr>`).join(""):'<tr><td colspan="7" class="empty">Không tìm thấy tướng phù hợp.</td></tr>';
 }
 try{const r=await fetch("/admin/api/heroes");if(!r.ok)throw Error("Không thể tải danh sách (HTTP "+r.status+")");heroes=await r.json();render();}
 catch(e){message.hidden=false;message.textContent=e.message;body.innerHTML='<tr><td colspan="7" class="empty">Tải dữ liệu thất bại.</td></tr>';}
 [search,rarity,status].forEach(x=>x.addEventListener("input",render));
});
