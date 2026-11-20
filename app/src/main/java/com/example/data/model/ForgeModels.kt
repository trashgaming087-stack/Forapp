package com.example.data.model

enum class SourceType(val label: String) {
    URL("Website URL"),
    HTML_BUNDLE("HTML / JS"),
    TEMPLATE("Starter App")
}

data class StarterTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val defaultAppName: String,
    val defaultPackage: String,
    val themeHex: String,
    val badgeText: String,
    val htmlCode: String
)

data class ProjectDraft(
    val id: Long = 0L,
    val appName: String = "Pulse Calc",
    val packageName: String = "com.forge.pulsecalc",
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val sourceType: SourceType = SourceType.TEMPLATE,
    val targetUrl: String = "https://developer.android.com",
    val htmlContent: String = StarterTemplates.all.first().htmlCode,
    val themeColorHex: String = "#0B0F14",
    val accentColorHex: String = "#00E5A3",
    val badgeLabel: String = "PC",
    val customIconUri: String? = null,
    val enableJavaScript: Boolean = true,
    val domStorageEnabled: Boolean = true,
    val fullscreen: Boolean = false,
    val pullToRefresh: Boolean = true,
    val selectedKeyId: Long = 0L
)

data class BuildLogEntry(
    val timestampMs: Long,
    val stage: String,
    val message: String,
    val isSuccess: Boolean = false,
    val isError: Boolean = false
)

sealed interface BuildPipelineState {
    data object Idle : BuildPipelineState
    data class Running(
        val stepIndex: Int,
        val totalSteps: Int,
        val currentStage: String,
        val logs: List<BuildLogEntry>
    ) : BuildPipelineState

    data class Succeeded(
        val buildId: Long,
        val apkPath: String,
        val fileName: String,
        val fileSizeBytes: Long,
        val durationMs: Long,
        val certSha256: String,
        val isVerified: Boolean,
        val logs: List<BuildLogEntry>
    ) : BuildPipelineState

    data class Failed(
        val errorMessage: String,
        val failedStage: String,
        val logs: List<BuildLogEntry>
    ) : BuildPipelineState
}

data class ApkEntryGroup(
    val category: String,
    val entryCount: Int,
    val totalBytes: Long
)

data class ApkInspectionReport(
    val fileName: String,
    val fileSizeBytes: Long,
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val versionCode: Int,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val isSignatureVerified: Boolean,
    val v1Verified: Boolean,
    val v2Verified: Boolean,
    val v3Verified: Boolean,
    val resourcesAligned4Byte: Boolean,
    val signerSubject: String,
    val certSha256: String,
    val certSha1: String,
    val entryGroups: List<ApkEntryGroup>,
    val verificationWarnings: List<String>,
    val verificationErrors: List<String>
)

object StarterTemplates {
    val all: List<StarterTemplate> = listOf(
        StarterTemplate(
            id = "tip_split",
            title = "Tip & Split Pro",
            subtitle = "Interactive bill & tip calculator with instant split math",
            category = "Utility",
            defaultAppName = "SplitBill",
            defaultPackage = "com.forge.splitbill",
            themeHex = "#0B0F14",
            badgeText = "SB",
            htmlCode = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>SplitBill</title>
<style>
  :root { --bg:#0B0F14; --card:#131A22; --accent:#00E5A3; --text:#F1F5F9; --muted:#94A3B8; }
  * { box-sizing:border-box; font-family:system-ui,-apple-system,sans-serif; }
  body { margin:0; background:var(--bg); color:var(--text); padding:24px; min-height:100vh; }
  .card { background:var(--card); border:1px solid #263244; border-radius:20px; padding:20px; margin-bottom:16px; }
  h1 { margin:0 0 4px; font-size:24px; }
  p.sub { margin:0 0 20px; color:var(--muted); font-size:14px; }
  label { display:block; font-size:12px; color:var(--muted); margin-bottom:6px; text-transform:uppercase; letter-spacing:0.06em; }
  input[type=number] { width:100%; padding:14px; border-radius:12px; border:1px solid #2A374A; background:#0B0F14; color:var(--text); font-size:20px; font-weight:700; margin-bottom:16px; }
  .pills { display:flex; gap:8px; margin-bottom:16px; }
  .pill { flex:1; padding:12px 0; border-radius:12px; border:1px solid #2A374A; background:#1B2430; color:var(--text); font-weight:600; cursor:pointer; }
  .pill.active { background:var(--accent); color:#002419; border-color:var(--accent); }
  .result { display:flex; justify-content:space-between; align-items:center; padding:12px 0; border-bottom:1px solid #222D3D; }
  .result:last-child { border-bottom:none; }
  .big { font-size:28px; font-weight:800; color:var(--accent); }
</style>
</head>
<body>
  <h1>SplitBill</h1>
  <p class="sub">Built with APKForge</p>
  <div class="card">
    <label>Bill Total ($)</label>
    <input id="bill" type="number" value="84.50" step="0.50" oninput="calc()">
    <label>Tip Percentage</label>
    <div class="pills">
      <button class="pill" onclick="setTip(10,this)">10%</button>
      <button class="pill active" onclick="setTip(15,this)">15%</button>
      <button class="pill" onclick="setTip(18,this)">18%</button>
      <button class="pill" onclick="setTip(22,this)">22%</button>
    </div>
    <label>Split Between People</label>
    <input id="people" type="number" value="2" min="1" oninput="calc()">
  </div>
  <div class="card">
    <div class="result"><span>Tip Amount</span><strong id="tipOut">$12.68</strong></div>
    <div class="result"><span>Total Bill</span><strong id="totalOut">$97.18</strong></div>
    <div class="result"><span>Per Person</span><span class="big" id="eachOut">$48.59</span></div>
  </div>
<script>
  let tip = 15;
  function setTip(v, el){ tip = v; document.querySelectorAll('.pill').forEach(b=>b.classList.remove('active')); el.classList.add('active'); calc(); }
  function calc(){
    const bill = Math.max(0, parseFloat(document.getElementById('bill').value)||0);
    const ppl = Math.max(1, parseInt(document.getElementById('people').value)||1);
    const t = bill * (tip/100), tot = bill + t, each = tot / ppl;
    document.getElementById('tipOut').textContent = '$'+t.toFixed(2);
    document.getElementById('totalOut').textContent = '$'+tot.toFixed(2);
    document.getElementById('eachOut').textContent = '$'+each.toFixed(2);
  }
  calc();
</script>
</body>
</html>
            """.trimIndent()
        ),
        StarterTemplate(
            id = "pomodoro_focus",
            title = "Focus Timer",
            subtitle = "Minimalist 25/5 Pomodoro countdown with session counter",
            category = "Productivity",
            defaultAppName = "FocusPulse",
            defaultPackage = "com.forge.focuspulse",
            themeHex = "#0F172A",
            badgeText = "FP",
            htmlCode = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>FocusPulse</title>
<style>
  * { box-sizing:border-box; font-family:system-ui,sans-serif; }
  body { margin:0; background:#0F172A; color:#F8FAFC; display:flex; flex-direction:column; align-items:center; justify-content:center; min-height:100vh; padding:24px; }
  .ring { width:240px; height:240px; border-radius:50%; border:6px solid #38BDF8; display:flex; flex-direction:column; align-items:center; justify-content:center; box-shadow:0 0 40px rgba(56,189,248,0.2); margin-bottom:28px; }
  .time { font-size:52px; font-weight:800; font-variant-numeric:tabular-nums; }
  .mode { color:#38BDF8; font-size:13px; letter-spacing:0.12em; text-transform:uppercase; margin-top:6px; }
  .row { display:flex; gap:12px; width:100%; max-width:300px; }
  button { flex:1; padding:14px; border-radius:14px; border:none; font-size:16px; font-weight:700; cursor:pointer; }
  .primary { background:#00E5A3; color:#002419; }
  .ghost { background:#1E293B; color:#E2E8F0; }
  .stats { margin-top:24px; color:#94A3B8; font-size:14px; }
</style>
</head>
<body>
  <div class="ring">
    <div class="time" id="clock">25:00</div>
    <div class="mode" id="mode">FOCUS SESSION</div>
  </div>
  <div class="row">
    <button class="primary" id="toggleBtn" onclick="toggle()">Start</button>
    <button class="ghost" onclick="reset()">Reset</button>
  </div>
  <div class="stats">Completed Sessions: <strong id="count">0</strong></div>
<script>
  let seconds = 1500, running = false, timer = null, sessions = 0, isBreak = false;
  function render(){
    const m = String(Math.floor(seconds/60)).padStart(2,'0');
    const s = String(seconds%60).padStart(2,'0');
    document.getElementById('clock').textContent = m+':'+s;
    document.getElementById('mode').textContent = isBreak ? 'SHORT BREAK' : 'FOCUS SESSION';
    document.getElementById('toggleBtn').textContent = running ? 'Pause' : 'Start';
    document.getElementById('count').textContent = sessions;
  }
  function toggle(){
    running = !running;
    if(running){
      timer = setInterval(()=>{
        if(seconds > 0){ seconds--; }
        else {
          if(!isBreak){ sessions++; isBreak = true; seconds = 300; }
          else { isBreak = false; seconds = 1500; }
          running = false; clearInterval(timer);
        }
        render();
      }, 1000);
    } else { clearInterval(timer); }
    render();
  }
  function reset(){ clearInterval(timer); running = false; isBreak = false; seconds = 1500; render(); }
  render();
</script>
</body>
</html>
            """.trimIndent()
        ),
        StarterTemplate(
            id = "scratch_notes",
            title = "Offline Scratchpad",
            subtitle = "Local-storage quick notes & checklist app",
            category = "Notes",
            defaultAppName = "QuickNote",
            defaultPackage = "com.forge.quicknote",
            themeHex = "#111827",
            badgeText = "QN",
            htmlCode = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>QuickNote</title>
<style>
  * { box-sizing:border-box; font-family:system-ui,sans-serif; }
  body { margin:0; background:#111827; color:#F9FAFB; padding:20px; }
  h2 { margin:0 0 12px; }
  .bar { display:flex; gap:8px; margin-bottom:16px; }
  input { flex:1; padding:12px 14px; border-radius:12px; border:1px solid #374151; background:#1F2937; color:#fff; font-size:15px; }
  button.add { padding:12px 18px; border-radius:12px; border:none; background:#00E5A3; color:#002419; font-weight:700; }
  .item { background:#1F2937; border:1px solid #374151; padding:14px; border-radius:14px; margin-bottom:10px; display:flex; justify-content:space-between; align-items:center; }
  .del { background:transparent; border:none; color:#F87171; font-weight:700; font-size:16px; }
</style>
</head>
<body>
  <h2>QuickNote</h2>
  <div class="bar">
    <input id="noteInput" placeholder="Write a quick note...">
    <button class="add" onclick="addNote()">Add</button>
  </div>
  <div id="list"></div>
<script>
  let notes = JSON.parse(localStorage.getItem('forge_notes') || '["Welcome to your offline APK!"]');
  function save(){ localStorage.setItem('forge_notes', JSON.stringify(notes)); render(); }
  function addNote(){
    const el = document.getElementById('noteInput');
    if(!el.value.trim()) return;
    notes.unshift(el.value.trim()); el.value=''; save();
  }
  function removeNote(i){ notes.splice(i,1); save(); }
  function render(){
    document.getElementById('list').innerHTML = notes.map((n,i)=>
      '<div class="item"><span>'+n.replace(/</g,'&lt;')+'</span><button class="del" onclick="removeNote('+i+')">✕</button></div>'
    ).join('');
  }
  render();
</script>
</body>
</html>
            """.trimIndent()
        ),
        StarterTemplate(
            id = "portfolio_card",
            title = "Creator Card",
            subtitle = "Modern dark bio link hub and showcase card",
            category = "Social",
            defaultAppName = "LinkHub",
            defaultPackage = "com.forge.linkhub",
            themeHex = "#0B0F14",
            badgeText = "LH",
            htmlCode = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Creator Card</title>
<style>
  * { box-sizing:border-box; font-family:system-ui,sans-serif; }
  body { margin:0; background:linear-gradient(160deg,#0B0F14,#131A22); color:#F8FAFC; min-height:100vh; padding:32px 20px; text-align:center; }
  .avatar { width:84px; height:84px; border-radius:24px; background:linear-gradient(135deg,#00E5A3,#38BDF8); color:#0B0F14; font-weight:900; font-size:32px; display:flex; align-items:center; justify-content:center; margin:0 auto 14px; }
  h1 { margin:0 0 6px; font-size:24px; }
  p { color:#94A3B8; margin:0 0 28px; }
  a.btn { display:block; text-decoration:none; background:#1B2430; border:1px solid #2A374A; color:#F1F5F9; padding:16px; border-radius:16px; margin-bottom:12px; font-weight:600; }
  a.btn.hi { background:#00E5A3; color:#002419; border-color:#00E5A3; }
</style>
</head>
<body>
  <div class="avatar">AF</div>
  <h1>Alex Forge</h1>
  <p>Mobile Builder &amp; Designer</p>
  <a class="btn hi" href="https://developer.android.com">Featured Project</a>
  <a class="btn" href="https://github.com">GitHub Repositories</a>
  <a class="btn" href="mailto:hello@example.com">Contact Me</a>
</body>
</html>
            """.trimIndent()
        )
    )
}
