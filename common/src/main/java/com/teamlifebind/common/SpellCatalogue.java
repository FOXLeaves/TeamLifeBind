package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 300 named spells with explicit per-ID trigger plans and animated geometry profiles. */
public final class SpellCatalogue {
    public static final int ORDINARY_COUNT = 280;
    public static final int SUPER_COUNT = 20;
    public static final int TOTAL_COUNT = ORDINARY_COUNT + SUPER_COUNT;

    private static final int[] TIERS = {1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 6, 6, 6, 7, 7};
    private static final String[] EN_DELIVERY = {
        "Lance", "Ray", "Breath", "Burst", "Satellites", "Chain", "Tide", "Maelstrom", "Starfall", "Trap",
        "Rush", "Step", "Bastion", "Aegis", "Familiar", "Brand", "Siphon", "Convergence", "Refrain", "Sanctum"
    };
    private static final String[][] ZH_NAMES = {
        {"火矢", "焰光射线", "龙息扇焰", "炎爆新星", "余烬环卫", "连锁燎原", "流火浪潮", "红莲火旋", "天降熔星", "爆燃地雷", "流焰突袭", "灰烬跃迁", "熔岩障壁", "凤凰火盾", "焰灵使魔", "焦土烙印", "灼心汲取", "太阳聚束", "不熄回响", "红莲圣域"},
        {"水弹", "激流水线", "瀑雨吐息", "潮汐震爆", "水珠环卫", "连锁水脉", "奔涌海潮", "深海漩涡", "天降雨星", "涌泉陷阱", "踏浪冲锋", "镜湖跃迁", "水幕障壁", "泡沫护盾", "潮灵使魔", "溺水刻印", "潮息汲取", "深洋聚束", "复潮回响", "沧海圣域"},
        {"冰锥", "霜冻射线", "极寒吐息", "碎冰新星", "冰晶环卫", "连锁冰棱", "冰河浪潮", "暴雪风眼", "天降冰陨", "寒霜地雷", "滑冰突袭", "冰镜跃迁", "冰晶障壁", "玄冰护盾", "霜灵使魔", "冻伤刻印", "冷魂汲取", "零度聚束", "永冻回响", "凛冬圣域"},
        {"风刃", "疾风射线", "狂岚吐息", "飓风新星", "风刃环卫", "连锁气流", "罡风浪潮", "龙卷风眼", "天降风星", "气旋陷阱", "疾风突袭", "空踏跃迁", "气压障壁", "风眼护盾", "岚灵使魔", "风切刻印", "真空汲取", "裂空聚束", "长风回响", "天空圣域"},
        {"石矛", "晶岩射线", "砂砾吐息", "地震新星", "岩块环卫", "连锁地脉", "碎岩浪潮", "流沙风眼", "天降陨石", "裂地陷阱", "崩岩突袭", "地脉跃迁", "磐石障壁", "岩壳护盾", "岩灵使魔", "重压刻印", "地气汲取", "地核聚束", "山鸣回响", "群山圣域"},
        {"闪电矛", "雷鸣射线", "雷云吐息", "雷暴新星", "电弧环卫", "连锁雷击", "电涌浪潮", "雷暴风眼", "天降雷星", "雷鸣地雷", "雷闪突袭", "电光跃迁", "电网障壁", "雷铠护盾", "雷灵使魔", "导电刻印", "电荷汲取", "天罚聚束", "轰雷回响", "雷霆圣域"},
        {"光箭", "晨曦射线", "圣光吐息", "耀光新星", "光环卫星", "连锁圣辉", "晨光浪潮", "辉光风眼", "天降光星", "闪光陷阱", "曙光突袭", "光棱跃迁", "辉晶障壁", "圣辉护盾", "光灵使魔", "净化刻印", "圣息汲取", "黎明聚束", "福音回响", "圣光圣域"},
        {"暗矢", "蚀影射线", "幽影吐息", "暗蚀新星", "影刃环卫", "连锁梦魇", "黑潮浪涌", "深影风眼", "天降暗星", "夜幕陷阱", "影袭冲锋", "暗影跃迁", "夜幕障壁", "暗影护盾", "影灵使魔", "梦魇刻印", "幽魂汲取", "永夜聚束", "暗潮回响", "永夜圣域"},
        {"奥术飞矢", "奥能射线", "奥流吐息", "奥术新星", "符文环卫", "连锁秘能", "秘能浪潮", "奥术风眼", "天降符星", "符文地雷", "奥闪突袭", "秘仪跃迁", "符文障壁", "奥能护盾", "符灵使魔", "破法刻印", "奥能汲取", "星核聚束", "秘仪回响", "奥术圣域"},
        {"荆棘矢", "藤刺射线", "花粉吐息", "繁花新星", "叶刃环卫", "连锁根须", "藤蔓浪潮", "荆棘风眼", "天降种星", "荆棘陷阱", "荆棘突袭", "林间跃迁", "根须障壁", "树皮护盾", "森灵使魔", "寄生刻印", "生命汲取", "古木聚束", "春生回响", "森海圣域"},
        {"引力矢", "重力射线", "压空吐息", "坍缩新星", "星石环卫", "连锁引力", "重力浪潮", "引力风眼", "天降重星", "坍缩陷阱", "重星突袭", "空间跃迁", "重力障壁", "引力护盾", "星灵使魔", "重力刻印", "引力汲取", "奇点聚束", "星轨回响", "奇点圣域"},
        {"时针矢", "迟滞射线", "流年吐息", "停时新星", "时针环卫", "连锁时痕", "岁月浪潮", "时隙风眼", "天降时星", "迟滞陷阱", "瞬时突袭", "时隙跃迁", "停滞障壁", "时沙护盾", "时灵使魔", "衰老刻印", "时光汲取", "纪元聚束", "轮回回响", "时停圣域"},
        {"血矢", "血虹射线", "血雾吐息", "绯红新星", "血珠环卫", "连锁血契", "血海浪潮", "血潮风眼", "天降血星", "血棘陷阱", "浴血突袭", "血影跃迁", "血晶障壁", "血契护盾", "血灵使魔", "放血刻印", "鲜血汲取", "血月聚束", "血脉回响", "血月圣域"},
        {"灵魂矢", "灵火射线", "灵息吐雾", "灵魂新星", "灵火环卫", "连锁魂锁", "灵潮浪涌", "魂旋风眼", "天降魂星", "灵魂陷阱", "魂火突袭", "灵界跃迁", "魂幕障壁", "魂火护盾", "灵魂使魔", "灵魂刻印", "魂息汲取", "魂灯聚束", "亡灵回响", "灵界圣域"}
    };
    private static final String[] ZH_DELIVERY_DESCRIPTION = {
        "沿视线射出可碰撞的单发飞矢，只命中路径上首个敌人。",
        "发射瞬时穿透射线，按路径依次命中多个敌人，实体和实心墙分别处理。",
        "向前展开扇形吐息，只覆盖视线角度内的近距离敌人。",
        "以自身为圆心释放环形冲击，向各方向扩散。",
        "召唤围绕自身旋转的卫星，靠近者被依次触发接触伤害。",
        "由首个命中目标跳跃到附近未命中的敌人，每个目标只传导一次。",
        "发出沿地面推进的波前，行进中的窄带依次命中沿途敌人。",
        "在瞄准地点形成持续旋涡，先聚拢再周期性施加伤害。",
        "标记瞄准地点，从上空延迟降下陨星，落点产生范围冲击。",
        "在瞄准地面埋设可见陷阱，首个敌人踏入时引爆并消失。",
        "沿朝向安全突进，穿过路径上的敌人造成冲撞命中。",
        "跃迁至视线中的安全落脚点，出发和抵达处分别产生短程冲击。",
        "在目标地点建立临时魔法障壁，对试图穿越的敌人反复阻挡并施加伤害。",
        "生成随身防护与反击环，被敌人贴身攻击时触发反击。",
        "召唤短时存续的魔法使魔，自动寻找合法敌人并持续攻击。",
        "向首个瞄准敌人施加可见印记，延迟后引爆，离开距离上限则失效。",
        "锁定前方敌人，按实际伤害汲取生命；不能从友军与无效目标获益。",
        "持续引导多次脉冲，移动过远、受限或死亡时中止剩余脉冲。",
        "记录首次命中的位置，延迟在原地点重放冲击，敌人可及时躲开。",
        "在目标地点展开大范围持续领域，周期性作用于领域内的合法敌人。"
    };
    private static final String[] EN_DELIVERY_DESCRIPTION = {
        "Fires a colliding projectile along the aim direction, hitting only the first enemy in its path.",
        "Projects an instant piercing ray through several enemies; solid walls terminate its path.",
        "Unfolds a forward cone of breath, covering only nearby enemies within its viewing angle.",
        "Releases an expanding radial shock centered on the caster.",
        "Creates satellites orbiting the caster; approaching enemies trigger successive contact hits.",
        "Jumps from the first struck enemy to nearby unvisited enemies; each target conducts only once.",
        "Sends a narrow ground wave forward, successively striking enemies crossed by its advancing front.",
        "Creates a persistent vortex at the aimed point, pulling targets in before periodic damage.",
        "Marks an aimed point for a delayed falling star and an impact explosion.",
        "Places a visible ground trap that detonates and disappears when the first enemy enters.",
        "Rushes safely forward, striking enemies crossed along the path.",
        "Blinks to a safe aimed landing point, creating short shockwaves at departure and arrival.",
        "Erects a temporary magical barrier that repeatedly blocks and strikes enemies attempting to cross.",
        "Creates a personal ward and retaliation ring triggered by close enemy attacks.",
        "Summons a temporary familiar that automatically acquires eligible enemies and attacks them.",
        "Brands the first aimed enemy for delayed detonation; excessive distance breaks the brand.",
        "Locks onto a forward enemy and siphons life from actual damage; friendly or invalid targets yield nothing.",
        "Channels repeated pulses; excessive movement, lost eligibility or death cancels remaining pulses.",
        "Records the first hit location and replays the impact there after a delay, allowing enemies to dodge.",
        "Creates a large persistent field at the aimed point, periodically affecting eligible enemies inside."
    };

    private static final List<SpellDefinition> ALL = build();
    private static final Map<String, SpellDefinition> BY_ID = index();
    private static final Map<Integer, List<SpellDefinition>> BY_TIER = tierIndex();
    private static final List<SpellDefinition> STARTER_POOL = ALL.stream().filter(spell -> spell.tier() == 1).toList();

    private SpellCatalogue() { }

    public static List<SpellDefinition> all() { return ALL; }
    public static SpellDefinition byId(String id) { return id == null ? null : BY_ID.get(id.trim().toLowerCase(Locale.ROOT)); }
    public static List<SpellDefinition> starterPool() { return STARTER_POOL; }
    public static List<SpellDefinition> tier(int tier) { return BY_TIER.getOrDefault(tier, List.of()); }

    private static List<SpellDefinition> build() {
        List<SpellDefinition> result = new ArrayList<>(TOTAL_COUNT);
        MagicMechanic[] deliveries = MagicMechanic.values();
        for (MagicSchool school : MagicSchool.values()) {
            for (int index = 0; index < deliveries.length; index++) {
                int tier = TIERS[index];
                MagicMechanic mechanic = deliveries[index];
                String id = school.name().toLowerCase(Locale.ROOT) + "_" + mechanic.name().toLowerCase(Locale.ROOT);
                String zhEffect = school == MagicSchool.GRAVITY && mechanic == MagicMechanic.NOVA
                    ? "环形爆压将敌人推离自身；下坠目标承受额外伤害" : school.zhEffect();
                String enEffect = school == MagicSchool.GRAVITY && mechanic == MagicMechanic.NOVA
                    ? "The pressure burst pushes enemies outward and deals bonus damage to falling targets" : school.enEffect();
                result.add(new SpellDefinition(id, ZH_NAMES[school.ordinal()][index], school.enName() + " " + EN_DELIVERY[index],
                    school, tier, 12 + tier * 8, 16 + tier * 12 + index * 2, 3 + tier * 3,
                    15 + tier * 4, 1.5 + tier * 0.55, 5 + tier * 6,
                    List.of(mechanic), ZH_DELIVERY_DESCRIPTION[index] + " " + zhEffect + "。 " + SpellBehaviour.byId(id).zhDescription(),
                    EN_DELIVERY_DESCRIPTION[index] + " " + enEffect + ". " + SpellBehaviour.byId(id).enDescription()));
            }
        }
        addSuper(result, "apocalypse_lotus", "末日红莲", "Apocalypse Lotus", MagicSchool.FIRE,
            "火域封锁落点，天降熔星引爆区域，余烬陷阱在爆炸后继续阻止追击。",
            "A fire domain seals the landing zone, falling molten stars detonate it, and ember mines prevent pursuit.",
            MagicMechanic.DOMAIN, MagicMechanic.METEOR, MagicMechanic.MINE);
        addSuper(result, "oceanic_judgement", "沧海裁决", "Oceanic Judgement", MagicSchool.WATER,
            "潮涡先聚拢并浸湿敌人，海啸穿过风眼，终末潮爆将目标推离中心。",
            "A tidal vortex gathers and wets enemies; a tsunami crosses it before a final nova throws targets outward.",
            MagicMechanic.VORTEX, MagicMechanic.WAVE, MagicMechanic.NOVA);
        addSuper(result, "absolute_winter", "绝对凛冬", "Absolute Winter", MagicSchool.ICE,
            "冰域冻结区域，冰墙限制逃离，延迟碎冰回响惩罚仍留在原位的敌人。",
            "A frozen domain chills the area, ice walls constrain escape, and a delayed shattering echo punishes enemies remaining.",
            MagicMechanic.DOMAIN, MagicMechanic.WALL, MagicMechanic.ECHO);
        addSuper(result, "skybreaker_tempest", "裂天风暴", "Skybreaker Tempest", MagicSchool.WIND,
            "旋风聚敌并卷起，轨道风刃护卫施法者，最终地面风浪将敌人抛出。",
            "A cyclone gathers and lifts enemies, orbiting wind blades guard the caster, and a final ground wave ejects them.",
            MagicMechanic.VORTEX, MagicMechanic.ORBIT, MagicMechanic.WAVE);
        addSuper(result, "continental_ruin", "大陆崩解", "Continental Ruin", MagicSchool.EARTH,
            "岩壁封锁目标区，陨石击落目标，地震波由落点向外传播。",
            "Rock barriers seal the target area, meteor impacts ground targets, and seismic waves spread from impact.",
            MagicMechanic.WALL, MagicMechanic.METEOR, MagicMechanic.WAVE);
        addSuper(result, "heavenly_thundercourt", "天罚雷庭", "Heavenly Thundercourt", MagicSchool.LIGHTNING,
            "导电刻印锁定目标，雷域持续麻痹，连锁电弧从印记目标传向周围敌人。",
            "A conductive brand locks onto a target, a thunder domain paralyzes, and chain arcs spread from the branded enemy.",
            MagicMechanic.MARK, MagicMechanic.DOMAIN, MagicMechanic.CHAIN);
        addSuper(result, "dawn_resurrection", "曙光重临", "Dawn Rebirth", MagicSchool.LIGHT,
            "圣盾净化施法者，光域暴露并灼伤敌人，重复圣辉脉冲汲取生命维持护盾。",
            "A holy shield cleanses the caster, a light domain exposes and sears enemies, and siphoning pulses sustain the ward.",
            MagicMechanic.SHIELD, MagicMechanic.DOMAIN, MagicMechanic.DRAIN);
        addSuper(result, "eternal_eclipse", "永夜日蚀", "Eternal Eclipse", MagicSchool.SHADOW,
            "暗域剥夺视野，影灵从黑暗追踪目标，背后的蚀影射线切断逃离路线。",
            "A dark domain strips vision, shadow familiars track enemies, and piercing rays cut their escape routes.",
            MagicMechanic.DOMAIN, MagicMechanic.SUMMON, MagicMechanic.BEAM);
        addSuper(result, "arcane_genesis", "秘法创世", "Arcane Genesis", MagicSchool.ARCANE,
            "破法射线粉碎护盾，奥术星陨连续轰击，先前落点再被秘能回响重放。",
            "Shield-breaking rays expose enemies, arcane meteors bombard them, and echoes replay earlier impact locations.",
            MagicMechanic.BEAM, MagicMechanic.METEOR, MagicMechanic.ECHO);
        addSuper(result, "worldtree_awakening", "世界树苏醒", "Worldtree Awakening", MagicSchool.NATURE,
            "森域缠根阻止逃离，古木使魔追击目标，汲取受伤敌人的生命使施法者恢复。",
            "A forest domain roots escapees, ancient-tree familiars pursue them, and siphons heal the caster from wounded enemies.",
            MagicMechanic.DOMAIN, MagicMechanic.SUMMON, MagicMechanic.DRAIN);
        addSuper(result, "black_hole_requiem", "黑洞镇魂曲", "Black Hole Requiem", MagicSchool.GRAVITY,
            "引力旋涡吸聚敌人，持续聚束压缩中心，最终坍缩新星将聚集者震出。",
            "A gravity vortex gathers enemies, channeled pulses compress its center, and a collapse nova ejects those gathered.",
            MagicMechanic.VORTEX, MagicMechanic.CHANNEL, MagicMechanic.NOVA);
        addSuper(result, "chronicle_zero", "纪元归零", "Chronicle Zero", MagicSchool.TIME,
            "时停领域减缓敌人行动，时痕烙印延迟爆发，命中地点再由时间回响重演。",
            "A stasis field slows actions, temporal brands detonate later, and echoes repeat hits at their recorded locations.",
            MagicMechanic.DOMAIN, MagicMechanic.MARK, MagicMechanic.ECHO);
        addSuper(result, "crimson_coronation", "绯红加冕", "Crimson Coronation", MagicSchool.BLOOD,
            "血域制造持续流血，血珠环卫保护近身，汲血脉冲以实际伤害回复生命。",
            "A blood domain causes bleeding, orbiting blood drops guard melee range, and siphoning pulses heal from actual damage.",
            MagicMechanic.DOMAIN, MagicMechanic.ORBIT, MagicMechanic.DRAIN);
        addSuper(result, "soul_king_procession", "魂王巡礼", "Soul King Procession", MagicSchool.SPIRIT,
            "灵域揭露隐形敌人，使魔逐个锁敌，魂锁在被发现的敌人之间连锁传播。",
            "A spirit domain reveals invisible enemies, familiars acquire them, and soul chains jump between revealed targets.",
            MagicMechanic.DOMAIN, MagicMechanic.SUMMON, MagicMechanic.CHAIN);
        addSuper(result, "phoenix_requiem", "凤凰终焉", "Phoenix Requiem", MagicSchool.FIRE,
            "火盾保护吟唱，凤凰使魔俯冲施压，终末吐息覆盖前方并在原位留下余烬回响。",
            "A fire ward protects channeling, phoenix familiars dive, and a final cone leaves an ember echo at its strike site.",
            MagicMechanic.SHIELD, MagicMechanic.SUMMON, MagicMechanic.CONE, MagicMechanic.ECHO);
        addSuper(result, "glacial_labyrinth", "冰葬迷宫", "Glacial Labyrinth", MagicSchool.ICE,
            "冰墙隔开战场，凝霜陷阱冻结闯入者，轨道冰棱围绕施法者，冰陨打击出口。",
            "Ice walls divide the battlefield, frost mines freeze intruders, orbiting shards guard the caster, and meteors strike exits.",
            MagicMechanic.WALL, MagicMechanic.MINE, MagicMechanic.ORBIT, MagicMechanic.METEOR);
        addSuper(result, "void_event_horizon", "虚空事件视界", "Void Event Horizon", MagicSchool.GRAVITY,
            "安全跃迁调整起点，引力领域困敌，贯穿射线压缩敌人，延迟回响再次轰击旧位置。",
            "A safe blink repositions the caster, a gravity domain confines enemies, piercing rays compress them, and echoes hit old positions.",
            MagicMechanic.BLINK, MagicMechanic.DOMAIN, MagicMechanic.BEAM, MagicMechanic.ECHO);
        addSuper(result, "storm_legion", "风暴军团", "Storm Legion", MagicSchool.LIGHTNING,
            "雷灵军团自动锁敌，雷霆扇面覆盖近身，连锁电弧传导，雷鸣地雷封住追击。",
            "Thunder familiars acquire enemies, a lightning cone covers close range, chains conduct onward, and thunder mines stop pursuit.",
            MagicMechanic.SUMMON, MagicMechanic.CONE, MagicMechanic.CHAIN, MagicMechanic.MINE);
        addSuper(result, "hourglass_end", "沙漏终刻", "Hourglass End", MagicSchool.TIME,
            "瞬时冲刺穿越敌阵，时墙限制追击，持续聚束积累时痕，终末回响重演打击。",
            "A temporal dash crosses enemies, stasis walls constrain pursuit, channeled pulses accumulate traces, and a final echo repeats strikes.",
            MagicMechanic.DASH, MagicMechanic.WALL, MagicMechanic.CHANNEL, MagicMechanic.ECHO);
        addSuper(result, "astral_annihilation", "星界湮灭", "Astral Annihilation", MagicSchool.ARCANE,
            "破法烙印先压低护盾，奥术旋涡聚敌，天降符星与贯穿射线交替，终末领域持续封锁落点。",
            "A shield-breaking brand weakens protection, an arcane vortex gathers enemies, meteors alternate with rays, and a domain seals the impact zone.",
            MagicMechanic.MARK, MagicMechanic.VORTEX, MagicMechanic.METEOR, MagicMechanic.BEAM, MagicMechanic.DOMAIN);
        if (result.size() != TOTAL_COUNT) throw new IllegalStateException("Unexpected spell catalogue size");
        return List.copyOf(result);
    }

    private static void addSuper(List<SpellDefinition> spells, String id, String zhName, String enName,
                                 MagicSchool school, String zhDescription, String enDescription,
                                 MagicMechanic... mechanics) {
        int index = spells.size() - ORDINARY_COUNT;
        spells.add(new SpellDefinition(id, zhName, enName, school, 8,
            160 + index * 3, 600 + index * 20, 80 + index * 2,
            56 + index * 0.5, 8 + index * 0.2, 78 + index * 2,
            List.of(mechanics), zhDescription + " " + school.zhEffect() + "。 " + SpellBehaviour.byId(id).zhDescription(),
            enDescription + " " + school.enEffect() + ". " + SpellBehaviour.byId(id).enDescription()));
    }

    private static Map<String, SpellDefinition> index() {
        Map<String, SpellDefinition> result = new LinkedHashMap<>();
        for (SpellDefinition spell : ALL) {
            if (result.put(spell.id(), spell) != null) throw new IllegalStateException("Duplicate spell id " + spell.id());
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<Integer, List<SpellDefinition>> tierIndex() {
        Map<Integer, List<SpellDefinition>> result = new LinkedHashMap<>();
        for (int tier = 1; tier <= 8; tier++) {
            final int requested = tier;
            result.put(tier, ALL.stream().filter(spell -> spell.tier() == requested).toList());
        }
        return Collections.unmodifiableMap(result);
    }
}
