package com.safetyrepair;

/**
 * 全部业务内容（决策树 / 速查卡 / 品牌目录 / 默认密码 / 知识库）。
 * 纯 Java 常量，由原生 Fragment 直接读取，不依赖任何 Web 资源。
 */
public class ContentData {

    /** 决策树节点：branch=分类入口(可选项)，judge=判断(是/否)，fix=处理动作 */
    public static class WNode {
        public String id, kind, text, tag, yes, no, next;
        public String[] opts, gotos;
        public WNode(String id, String kind, String text) {
            this.id = id; this.kind = kind; this.text = text;
        }
    }

    public static final WNode[] CAMERA_WIZARD;
    public static final WNode[] DOOR_WIZARD;
    static {
        // ---------- 摄像头决策树 ----------
        WNode cRoot = new WNode("c_root", "branch", "海康威视摄像头 · 选择问题类型");
        cRoot.opts = new String[]{"设备离线 / 无法访问", "画面异常（在线但异常）", "录像异常"};
        cRoot.gotos = new String[]{"c_off1", "c_pic_root", "c_rec_root"};

        WNode cOff1 = new WNode("c_off1", "judge", "电源灯 / 网口灯是否亮？");
        cOff1.yes = "c_off2"; cOff1.no = "c_offfix1";
        WNode cOfffix1 = new WNode("c_offfix1", "fix", "查供电：PoE 端口状态 / 独立 12V，换电源或网线");
        WNode cOff2 = new WNode("c_off2", "judge", "ping IP 是否通？");
        cOff2.yes = "c_offfix2"; cOff2.no = "c_offfix3";
        WNode cOfffix2 = new WNode("c_offfix2", "fix", "查端口 8000/554、重启设备；复位前先备份配置");
        WNode cOfffix3 = new WNode("c_offfix3", "fix", "同网段其他也不通→上联/VLAN/网关；仅本机→换网线/光纤，笔记本直连测");

        WNode cPicRoot = new WNode("c_pic_root", "branch", "画面异常 · 选择现象");
        cPicRoot.opts = new String[]{"黑屏/无视频", "花屏/卡顿", "偏色", "模糊", "夜视差"};
        cPicRoot.gotos = new String[]{"c_pic1", "c_pic2", "c_pic3", "c_pic4", "c_pic5"};
        WNode cPic1 = new WNode("c_pic1", "fix", "查 RTSP:554、编码 H.265 兼容、主子码流参数");
        WNode cPic2 = new WNode("c_pic2", "fix", "换六类网线、查交换机上联拥塞、降码率/分辨率");
        WNode cPic3 = new WNode("c_pic3", "fix", "重置白平衡 / IR-CUT");
        WNode cPic4 = new WNode("c_pic4", "fix", "清洁镜头、调焦、提码率");
        WNode cPic5 = new WNode("c_pic5", "fix", "调补光 / 低照度模式");

        WNode cRecRoot = new WNode("c_rec_root", "branch", "录像异常 · 选择现象");
        cRecRoot.opts = new String[]{"无录像", "断录", "录不满", "回放卡"};
        cRecRoot.gotos = new String[]{"c_rec1", "c_rec2", "c_rec3", "c_rec4"};
        WNode cRec1 = new WNode("c_rec1", "fix", "设录像计划、格式化硬盘、查存储策略");
        WNode cRec2 = new WNode("c_rec2", "fix", "校时 NTP、查 SMART 硬盘、查网络抖动");
        WNode cRec3 = new WNode("c_rec3", "fix", "加硬盘容量、开循环覆盖");
        WNode cRec4 = new WNode("c_rec4", "fix", "分散回放、加盘 / 解码头扩容");

        CAMERA_WIZARD = new WNode[]{cRoot, cOff1, cOfffix1, cOff2, cOfffix2, cOfffix3,
                cPicRoot, cPic1, cPic2, cPic3, cPic4, cPic5, cRecRoot, cRec1, cRec2, cRec3, cRec4};

        // ---------- 门禁决策树 ----------
        WNode dRoot = new WNode("d_root", "branch", "立林门禁 · 选择问题类型");
        dRoot.opts = new String[]{"刷卡不开门 / 识别失败", "门关不上 / 假锁 / 自动开门",
                "门磁误报 / 状态不对", "控制器离线", "消防联动异常（最高优先）"};
        dRoot.gotos = new String[]{"d_card1", "d_lock_root", "d_mag", "d_ctrl", "d_fire"};

        WNode dCard1 = new WNode("d_card1", "judge", "平台权限 / 卡片状态正常？");
        dCard1.yes = "d_card2"; dCard1.no = "d_cardfix1";
        WNode dCardfix1 = new WNode("d_cardfix1", "fix", "重新发卡 / 解挂 / 延期");
        WNode dCard2 = new WNode("d_card2", "judge", "换已知好卡仍不开？");
        dCard2.yes = "d_cardfix2"; dCard2.no = "d_cardfix3";
        WNode dCardfix2 = new WNode("d_cardfix2", "fix", "测读卡器 12V、韦根线序 → 修供电/接线/换读卡器");
        WNode dCardfix3 = new WNode("d_cardfix3", "fix", "原卡损坏，补卡");

        WNode dLockRoot = new WNode("d_lock_root", "branch", "门关不上 · 选择现象");
        dLockRoot.opts = new String[]{"假锁", "自动开门"};
        dLockRoot.gotos = new String[]{"d_lock1", "d_lock2"};
        WNode dLock1 = new WNode("d_lock1", "fix", "电锁电压不足（加大线径/缩短距离）、调锁扣板");
        WNode dLock2 = new WNode("d_lock2", "fix", "换门磁/出门按钮；继电器粘连换板");

        WNode dMag = new WNode("d_mag", "fix", "调灵敏度/安装间隙、换磁、查控制器输入点配置");

        WNode dCtrl = new WNode("d_ctrl", "branch", "控制器离线 · 选择通讯方式");
        dCtrl.opts = new String[]{"TCP/IP", "RS485"};
        dCtrl.gotos = new String[]{"d_ctrl1", "d_ctrl2"};
        WNode dCtrl1 = new WNode("d_ctrl1", "fix", "ping → 网线/VLAN/IP 冲突/端口 8000");
        WNode dCtrl2 = new WNode("d_ctrl2", "fix", "终端电阻 / 极性 / 地址拨码冲突");

        WNode dFire = new WNode("d_fire", "fix", "查联动模块接线 → 断电开锁逻辑（常闭磁力锁断电即开）→ UPS 放电");
        dFire.tag = "最高优先"; dFire.next = "d_fire2";
        WNode dFire2 = new WNode("d_fire2", "fix", "修后必须实测一次断电开锁，并留消防联动测试记录");

        DOOR_WIZARD = new WNode[]{dRoot, dCard1, dCardfix1, dCard2, dCardfix2, dCardfix3,
                dLockRoot, dLock1, dLock2, dMag, dCtrl, dCtrl1, dCtrl2, dFire, dFire2};
    }

    /** 速查卡：{现象, 先做(前2步), 若未恢复} */
    public static final String[][] CAMERA_QUICK = {
        {"平台显示离线、Web 打不开", "①看电源/网口灯 ②ping IP", "灯不亮查供电/PoE；ping不通换网线或直连"},
        {"画面黑屏/无视频", "①确认在线 ②查 RTSP:554 与编码(H.265)", "调主子码流参数、平台取流设置"},
        {"画面花屏/卡顿", "①换六类网线 ②查交换机上联占用", "降码率/分辨率、排查拥塞"},
        {"画面偏色", "①重置白平衡 ②检查 IR-CUT", "恢复出厂图像参数"},
        {"画面模糊", "①擦镜头 ②调焦", "提码率、查镜头污渍"},
        {"夜视差", "①查补光 ②调低照度模式", "测红外灯/感光参数"},
        {"无录像", "①查录像计划 ②硬盘是否格式化", "查存储策略/容量"},
        {"录像断断续续", "①校时 NTP ②查硬盘 SMART", "查网络抖动、换盘"},
        {"回放卡", "①分散回放 ②查解码负载", "加盘/解码头扩容"},
    };
    public static final String[][] DOOR_QUICK = {
        {"刷卡不开门", "①查平台权限/卡片状态 ②换已知好卡", "好卡不开→测读卡器12V/韦根线；仍不动作→查控制器+电锁电压"},
        {"卡片刷不上", "①确认卡片类型匹配 ②清洁卡片/读卡器", "补卡或换读卡器"},
        {"门关不上/假锁", "①测电锁电压 ②调锁扣板", "加大线径/缩短供电距离"},
        {"门自动开", "①查门磁 ②查出门按钮", "换门磁/按钮；继电器粘连换板"},
        {"门磁误报", "①调灵敏度 ②查安装间隙", "换磁、查输入点配置"},
        {"控制器离线", "①ping IP ②查网线/VLAN", "IP冲突/端口；RS485查终端电阻/极性/拨码"},
        {"权限错了/乱", "①查发卡/注销记录 ②核对权限矩阵", "批量恢复须变更窗口+备份"},
        {"火警门不开 ⚠", "①查联动模块接线 ②查断电开锁逻辑", "查UPS放电；修后实测断电开锁并留记录"},
    };

    /** 摄像头主流品牌：{名称, 默认端口, 默认用户, 默认密码, RTSP模板, ONVIF, SDK/对接} */
    public static final String[][] CAMERA_BRANDS = {
        {"海康威视", "80/554/8000", "admin", "12345", "rtsp://u:p@IP:554/Streaming/Channels/101", "支持", "ISAPI/SDK/iVMS-4200"},
        {"大华", "80/554/37777", "admin", "admin", "rtsp://u:p@IP:554/cam/realmonitor?channel=1&subtype=0", "支持", "SDK/DMSS"},
        {"宇视", "80/554/7611", "admin", "123456", "rtsp://u:p@IP:554/unicast/c1/s1/live", "支持", "SDK/IMOS"},
        {"华为", "80/554/5060", "admin", "Admin@123", "rtsp://u:p@IP:554/LiveMedia/ch1/Media1", "支持", "SDK/HoloSens"},
        {"TP-LINK", "80/554", "admin", "admin", "rtsp://u:p@IP:554/stream1", "支持", "APP/VIGI"},
        {"天地伟业", "80/554", "admin", "1111", "rtsp://u:p@IP:554/1/1", "支持", "SDK"},
        {"同为/Tiandy", "80/554", "admin", "12345", "rtsp://u:p@IP:554/stream1", "支持", "SDK"},
        {"博世Bosch", "80/554", "service", "service", "rtsp://u:p@IP:554/rtsp_tunnel", "支持", "BVMS/SDK"},
        {"安讯士Axis", "80/554", "root", "root", "rtsp://u:p@IP:554/axis-media/media.amp", "支持", "VAPIX/SDK"},
        {"汉邦高科", "80/554", "admin", "888888", "rtsp://u:p@IP:554", "支持", "SDK"},
    };

    /** 门禁主流品牌：{名称, 协议类型, 已知端口, SDK/平台, 对接方式, 类型} */
    public static final String[][] DOOR_BRANDS = {
        {"立林", "TCP/IP/RS485/韦根", "8000", "立林管理软件/开放平台", "私有协议，需厂商SDK或开放平台对接", "厂商私有"},
        {"中控ZKTeco", "TCP/IP/RS485", "4370", "ZKBioSecurity/SDK", "私有SDK，有开放API", "厂商私有"},
        {"霍尼韦尔", "TCP/IP/RS485", "80", "WinPak/ProWatch", "私有协议，需集成授权", "厂商私有"},
        {"博世Bosch", "TCP/IP", "80", "BVMS/AMS", "私有/OSDP可选", "厂商私有"},
        {"达实", "TCP/IP/RS485", "8080", "达实一卡通平台", "私有协议", "厂商私有"},
        {"海康门禁", "TCP/IP", "8000", "iVMS-4200/ISAPI", "ISAPI开放，可ONVIF-AC发现", "开放标准"},
        {"大华门禁", "TCP/IP", "37777", "DSS/SDK", "SDK开放", "厂商私有"},
        {"微耕", "TCP/IP/RS485", "60000", "微耕管理软件", "私有协议", "厂商私有"},
        {"捷顺", "TCP/IP", "8080", "捷顺云平台", "私有协议/云对接", "厂商私有"},
        {"披克", "TCP/IP/RS485", "80", "披克管理软件", "私有协议", "厂商私有"},
        {"同方锐安", "TCP/IP", "80", "锐安一卡通", "私有协议", "厂商私有"},
    };

    /** 默认密码速查（仅授权设备初始化/上线必改密） */
    public static final String[][] DEFAULT_PASS = {
        {"海康威视", "admin", "12345", "上线必须修改"},
        {"大华", "admin", "admin", "上线必须修改"},
        {"宇视", "admin", "123456", "上线必须修改"},
        {"华为", "admin", "Admin@123", "上线必须修改"},
        {"TP-LINK", "admin", "admin", "上线必须修改"},
        {"天地伟业", "admin", "1111", "上线必须修改"},
        {"同为", "admin", "12345", "上线必须修改"},
        {"安讯士", "root", "root", "上线必须修改"},
        {"立林门禁", "admin", "123456", "默认口令，须变更"},
    };

    /** 知识库 {标题, 内容} */
    public static final String[][] KB = {
        {"通用排障顺序", "物理 → 网络 → 设备 → 配置。\n先易后难、先链路后设备、改前必备份、修后必实测。"},
        {"摄像头快速口诀", "灯 → ping → PoE → 直连 → 重启 → 配置"},
        {"门禁快速口诀", "权限 → 好卡 → 读卡器 → 控制器 → 锁/电"},
        {"NTP 校时", "门禁事件与录像时间必须一致，否则事后追溯对不上。统一 NTP，每日校时巡检（误差≤2s）。"},
        {"PoE 预算", "端口数 × 单口功率(af≈15.4W/at≈30W/bt≈60-90W) × 0.8 安全系数；立林门禁控制器多为12V/24V独立供电，电锁必须独立电源+UPS。"},
        {"存储计算", "码流(Mbps) × 3600 × 24 × 天数 × 路数 ÷ 8 = GB。"},
        {"消防联动红线", "火警信号必须触发断电开锁（常闭磁力锁断电即开）。这是消防验收硬指标，绝不能用断电闭锁逻辑。修后必须实测。"},
        {"操作合规", "任何门禁权限批量修改、联动逻辑变更、换锁，必须在变更窗口+备份原配置后操作，改完立即实测。"},
    };

    public static final String REDLINE_CAM =
            "摄像头：登高作业用脚手架/升降车；批量改配置前导出配置备份；复位前必备份。";
    public static final String REDLINE_DOOR =
            "门禁维修红线：换锁 / 断电开锁测试 / 权限批量恢复 → 登记 + 变更窗口 + 备份原配置；临时旁路电锁须专人值守、修复即恢复，禁长期旁路。";
    public static final String MANTRA =
            "摄像头：灯 → ping → PoE → 直连 → 重启 → 配置\n门禁：权限 → 好卡 → 读卡器 → 控制器 → 锁/电\n共性：先易后难、先链路后设备、改前必备份、修后必实测";
}
