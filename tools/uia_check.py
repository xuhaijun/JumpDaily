import subprocess, xml.etree.ElementTree as ET, re, sys, time

NS = "com.jumpdaily.jump"

def adb(args, capture=False):
    r = subprocess.run(["adb"] + args, capture_output=capture, text=capture)
    if capture:
        return r.stdout
    return None

def launch():
    adb(["shell", "am", "start", "-n", f"{NS}/.MainActivity"])
    time.sleep(2.5)

def dump():
    adb(["shell", "uiautomator", "dump", "/sdcard/ui.xml"])
    adb(["pull", "/sdcard/ui.xml", "/tmp/ui.xml"])
    return ET.parse("/tmp/ui.xml").getroot()

def nodes(root):
    out = []
    for n in root.iter("node"):
        b = n.get("bounds") or ""
        m = re.findall(r"\d+", b)
        if len(m) == 4:
            x1, y1, x2, y2 = map(int, m)
            out.append({
                "text": n.get("text") or "",
                "desc": n.get("content-desc") or "",
                "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2,
            })
    return out

def find(root, text=None, desc=None, contains=False):
    res = []
    for n in nodes(root):
        hit = False
        if text is not None:
            hit = (text in n["text"]) if contains else (n["text"] == text)
        if not hit and desc is not None:
            hit = (desc in n["desc"]) if contains else (n["desc"] == desc)
        if hit:
            res.append(n)
    return res

def tap(x, y):
    adb(["shell", "input", "tap", str(x), str(y)])
    time.sleep(1.2)

def shot(name):
    adb(["shell", "screencap", "-p", f"/sdcard/{name}.png"])
    adb(["pull", f"/sdcard/{name}.png", f"D:/SmallTools/JumpDaily/{name}.png"])

def type_text(s):
    adb(["shell", "input", "text", s])
    time.sleep(0.6)

if __name__ == "__main__":
    step = sys.argv[1]
    if step == "home":
        launch()
        root = dump()
        shot("v_home")
        # 找调整目标 -> 设置
        t = find(root, text="调整目标")
        print("调整目标:", t)
    elif step == "settings":
        root = dump()
        t = find(root, text="调整目标")
        if t:
            tap(t[0]["cx"], t[0]["cy"])
        time.sleep(1.5)
        shot("v_settings")
        root = dump()
        print("下载中文语音包:", find(root, text="下载中文语音包", contains=True))
        print("引擎:", find(root, text="当前语音引擎", contains=True))
    elif step == "children_edit":
        # 从首页进切换 -> children -> 编辑第一个 -> 输入长名字
        root = dump()
        sw = find(root, text="切换", contains=True)
        if sw:
            tap(sw[0]["cx"], sw[0]["cy"])
        time.sleep(1.5)
        root = dump()
        ed = find(root, desc="编辑") or find(root, text="编辑", contains=True)
        print("编辑按钮:", ed)
        if ed:
            tap(ed[0]["cx"], ed[0]["cy"])
        time.sleep(1.5)
        # 清空并输入长名字（先全选删除再输入）
        adb(["shell", "input", "keyevent", "KEYCODE_CTRL_A"])
        adb(["shell", "input", "keyevent", "KEYCODE_DEL"])
        time.sleep(0.4)
        type_text("超级长的宝贝名字测试省略号效果")
        time.sleep(0.5)
        shot("v_edit_longname")
        root = dump()
        print("计数器:", find(root, text="/10", contains=True))
        # 保存并返回首页看省略号
        sv = find(root, text="保存修改") or find(root, text="保存")
        if sv:
            tap(sv[0]["cx"], sv[0]["cy"])
        time.sleep(1.5)
        root = dump()
        back = find(root, text="切换", contains=True)
        if back:
            tap(back[0]["cx"], back[0]["cy"])  # 回到首页（ChildrenScreen 无底栏，需系统返回）
        time.sleep(1.0)
        adb(["shell", "input", "keyevent", "KEYCODE_BACK"])
        time.sleep(1.5)
        shot("v_home_longname")
