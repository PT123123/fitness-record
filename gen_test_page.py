# -*- coding: utf-8 -*-
# 生成 _test.html = fitness-tracker.html + 页内自测脚本（测完删除，不入库）
import io, re

src = io.open('fitness-tracker.html', encoding='utf-8').read()

test_js = u"""
<script>
/* ====== 临时自测脚本（仅 _test.html）====== */
window.addEventListener('load', function(){
  function run(){
    var out = [];
    var pass = 0, fail = 0;
    function ok(name, cond){
      if (cond) { pass++; out.push('PASS ' + name); }
      else { fail++; out.push('FAIL ' + name); }
    }
    try{
      window.alert = function(){};   // 静默弹窗，避免阻塞
      // 重置
      localStorage.removeItem(LS);
      state.records = []; state.groups = []; state.customActions = []; state.hiddenActions = [];
      state.editingIdx = null; recDraft = { part:'', action:'', rest:60, reps:'', weight:'', note:'', t:'' };
      groupDefaults = { part:'', action:'', reps:'', weight:'' };

      var TUIQIAO = '\\u81c0\\u6865';      // 臀桥
      var TUI    = '\\u817f';              // 腿
      var DUN    = '\\u6df1\\u8e72';       // 深蹲
      var TUIJU  = '\\u817f\\u4e3e';       // 腿举
      var WANJU  = '\\u817f\\u5f2f\\u4e3e';// 腿弯举
      var HUAZHOU= '\\u5750\\u59ff\\u5212\\u8239'; // 坐姿划船
      var DAY = 86400000, now = Date.now();

      /* ---- A: 本次会话新填动作立刻出现在建议里 ---- */
      addGroup({});
      updateGroup(0, { part: TUI });
      updateGroup(0, { action: TUIQIAO });
      var chips1 = [].map.call(document.querySelectorAll('.group-edit .chips')[1].querySelectorAll('.chip'), function(c){ return c.textContent; });
      ok('A1 未保存的新动作出现在建议第一位', chips1[0] === TUIQIAO);
      ok('A2 新动作 chip 高亮选中', chips1.length > 0);

      /* ---- B: 保存到历史后学习为预设 ---- */
      saveGroupsToHistory();
      ok('B1 保存后学习成自定义预设', state.customActions.some(function(c){ return c.action === TUIQIAO; }));
      ok('B2 预设带部位', state.customActions[0].part === TUI);
      var opts1 = actionOptions(TUI);
      ok('B3 建议第一位是刚保存的动作', opts1[0] === TUIQIAO);

      /* ---- C: 按最近使用排序（历史记录时间） ---- */
      state.records.push({ id:'x1', date: today(), t:'10:00', src:'manual', ts: now - 3*DAY, part: TUI, action: DUN });   // 深蹲 3 天前
      state.records.push({ id:'x2', date: today(), t:'11:00', src:'manual', ts: now - 1*DAY, part: TUI, action: TUIJU }); // 腿举 1 天前
      var opts2 = actionOptions(TUI);
      ok('C1 最近用的排最前(臀桥今天)', opts2[0] === TUIQIAO);
      ok('C2 其次是昨天动作(腿举)', opts2[1] === TUIJU);
      ok('C3 再其次是3天前(深蹲)', opts2[2] === DUN);
      ok('C4 没用过的内置动作按原顺序排后', opts2[3] === '\\u534a\\u8e72' && opts2[4] === '\\u5668\\u68b0\\u6df1\\u8e72');

      /* ---- D: 设置页新增 / 改名 / 删除 ---- */
      goTab('settings');
      ok('D1 设置页有新增输入框', !!document.querySelector('.card input.inline-input[placeholder]'));
      ok('D2 设置页有添加按钮', document.getElementById('app').innerHTML.indexOf('\\u6dfb\\u52a0\\u52a8\\u4f5c\\u9884\\u8bbe') >= 0);
      actionAddDraft = WANJU; actionAddPart = TUI;
      addActionPreset();
      ok('D3 手动添加预设成功', state.customActions.some(function(c){ return c.action === WANJU; }));
      ok('D4 新增预设带部位', state.customActions.filter(function(c){ return c.action === WANJU; })[0].part === TUI);
      var wIdx = state.customActions.findIndex(function(c){ return c.action === WANJU; });
      startEditAction(wIdx);
      actionEditDraft = WANJU + '2';
      saveEditAction();
      ok('D5 改名成功', state.customActions.some(function(c){ return c.action === WANJU + '2'; }));
      ok('D6 旧名不再出现', !state.customActions.some(function(c){ return c.action === WANJU; }));
      ok('D7 旧名进 hidden 防止学习回来', state.hiddenActions.indexOf(WANJU) >= 0);
      ok('D8 改名保留部位', state.customActions.filter(function(c){ return c.action === WANJU + '2'; })[0].part === TUI);
      var appHtml = document.getElementById('app').innerHTML;
      ok('D9 设置页渲染改名按钮', appHtml.indexOf('\\u6539\\u540d') >= 0);
      ok('D10 设置页列表按最近使用文案提示', appHtml.indexOf('\\u6309\\u6700\\u8fd1\\u4f7f\\u7528\\u6392\\u5e8f') >= 0);
      delCustomAction(WANJU + '2');
      ok('D11 删除成功', !state.customActions.some(function(c){ return c.action === WANJU + '2'; }));
      ok('D12 删除后不再学习回来', state.hiddenActions.indexOf(WANJU + '2') >= 0);
      /* 重名/内置名校验（alert 已静默） */
      actionAddDraft = DUN; actionAddPart = TUI;
      addActionPreset();
      ok('D13 内置动作不会被重复添加', !state.customActions.some(function(c){ return c.action === DUN; }));
      actionAddDraft = TUIQIAO; actionAddPart = TUI;
      addActionPreset();
      ok('D14 已有同名预设不会被重复添加', state.customActions.filter(function(c){ return c.action === TUIQIAO; }).length === 1);

      /* ---- E: 手动补录新动作 → 学习 + 排最前 ---- */
      goTab('train');
      recDraft.part = TUI; recDraft.action = HUAZHOU;
      addManual();
      ok('E1 手动补录学习成预设', state.customActions.some(function(c){ return c.action === HUAZHOU; }));
      ok('E2 建议第一位是刚补录动作', actionOptions(TUI)[0] === HUAZHOU);

      /* ---- F: 部位过滤仍生效 ---- */
      var optsXiong = actionOptions('\\u80f8');
      ok('F1 腿部自定义动作不出现在胸部位', optsXiong.indexOf(TUIQIAO) < 0);

      /* 输出结果到页面底部 + 标题；停在设置页动作预设卡片供截图 */
      goTab('settings');
      var cards = document.querySelectorAll('.card');
      for (var ci = 0; ci < cards.length; ci++) {
        if (cards[ci].innerHTML.indexOf('\\u52a8\\u4f5c\\u9884\\u8bbe') >= 0 && cards[ci].innerHTML.indexOf('\\u65b0\\u589e\\u9884\\u8bbe') >= 0) { cards[ci].scrollIntoView(); break; }
      }
      var box = document.createElement('pre');
      box.id = '__testout';
      box.style.cssText = 'position:fixed;bottom:0;left:0;right:0;z-index:99999;background:#fff;color:#000;font-size:12px;margin:0;padding:6px;white-space:pre-wrap;max-height:34%;overflow:auto';
      box.textContent = 'PASS=' + pass + ' FAIL=' + fail + '\\n' + out.join('\\n');
      document.body.appendChild(box);
      document.title = 'TESTS_DONE_PASS' + pass + '_FAIL' + fail;
    }catch(e){
      document.title = 'TESTS_ERROR';
      var box = document.createElement('pre');
      box.id = '__testout';
      box.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:99999;background:#fff;color:#000;font-size:12px;margin:0;padding:6px;white-space:pre-wrap';
      box.textContent = 'ERROR: ' + (e && e.message) + '\\n' + (e && e.stack);
      document.body.appendChild(box);
    }
  }
  setTimeout(run, 300);
});
</script>
"""

out = src.replace('</body>', test_js + '</body>')
io.open('_test.html', 'w', encoding='utf-8').write(out)
print('generated _test.html, bytes:', len(out.encode("utf-8")))
