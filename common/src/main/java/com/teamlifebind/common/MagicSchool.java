package com.teamlifebind.common;

/** Schools define the world interaction applied by an adapter, independently of delivery shape. */
public enum MagicSchool {
    FIRE("炎", "Fire", "点燃目标；已燃烧的目标承受额外伤害", "Ignites targets and deals bonus damage to already burning enemies"),
    WATER("潮", "Water", "熄火、附着潮湿并使后续雷击传导", "Extinguishes fire, applies wetness and conducts subsequent lightning"),
    ICE("霜", "Ice", "冻结减速；对潮湿目标凝结，对冻结目标碎冰", "Slows and freezes; wet targets crystallize and frozen targets shatter"),
    WIND("岚", "Wind", "推动目标并悬空，冲散持续领域", "Pushes and lifts targets, scattering persistent fields"),
    EARTH("岩", "Earth", "获得短暂抗性，命中施加沉重减速并击落上升中的目标", "Grants brief resistance, slows struck enemies and grounds rising targets"),
    LIGHTNING("雷", "Lightning", "使目标麻痹，潮湿目标额外传导电弧", "Paralyzes targets and chains additional arcs through wet targets"),
    LIGHT("辉", "Light", "净化自身中毒、虚弱、失明与凋零，显露敌人并对亡灵增伤", "Cleanses poison, weakness, blindness and wither, reveals enemies and sears undead"),
    SHADOW("影", "Shadow", "施法时短暂隐身，命中施加失明，背袭追加伤害", "Briefly hides the caster, blinds struck targets and deals bonus damage from behind"),
    ARCANE("奥", "Arcane", "击破吸收护盾并使法术易伤叠层", "Breaks absorption shields and stacks vulnerability to spells"),
    NATURE("森", "Nature", "施加毒素与缠根减速；伤害残血目标时回复生命", "Poisons and roots movement, healing the caster when damaging wounded targets"),
    GRAVITY("引", "Gravity", "牵引目标并压低高度；下坠的目标承受额外伤害", "Pulls targets inward and downward, dealing bonus damage to falling enemies"),
    TIME("时", "Time", "减缓移动与采掘；连续命中追加上一击伤害并加速自身冷却", "Slows movement and mining, echoes part of recent hit damage and hastens the caster's cooldowns"),
    BLOOD("血", "Blood", "施法献祭少量生命，命中施加流血并从实际伤害中吸取生命", "Sacrifices a little health on casting, applies bleeding and siphons life from actual damage"),
    SPIRIT("灵", "Spirit", "显露隐形目标并施加虚弱；灵魂飞矢具有追踪能力", "Reveals invisible targets and weakens their attacks; spirit lances seek enemies");

    private final String zhName;
    private final String enName;
    private final String zhEffect;
    private final String enEffect;

    MagicSchool(String zhName, String enName, String zhEffect, String enEffect) {
        this.zhName = zhName;
        this.enName = enName;
        this.zhEffect = zhEffect;
        this.enEffect = enEffect;
    }

    public String zhName() { return zhName; }
    public String enName() { return enName; }
    public String zhEffect() { return zhEffect; }
    public String enEffect() { return enEffect; }
}
