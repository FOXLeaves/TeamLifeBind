import com.teamlifebind.common.MagicLootRules;
import com.teamlifebind.common.MagicMechanic;
import com.teamlifebind.common.MagicSchool;
import com.teamlifebind.common.SpellCatalogue;
import com.teamlifebind.common.SpellDefinition;
import com.teamlifebind.common.SpellBehaviour;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Regenerates the player catalogue from the actual shared runtime definitions. */
public class ExportMagicCatalogue {
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
    private static String number(double value) { return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    private static String mechanics(SpellDefinition spell) { return spell.mechanics().stream().map(Enum::name).collect(Collectors.joining(" → ")); }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        StringBuilder json = new StringBuilder("{\n  \"schema\": 2,\n  \"ordinaryCount\": 280,\n  \"superCount\": 20,\n  \"spells\": [\n");
        for (int i = 0; i < SpellCatalogue.all().size(); i++) {
            SpellDefinition s = SpellCatalogue.all().get(i);
            json.append("    {\"id\":").append(quote(s.id())).append(",\"zhName\":").append(quote(s.zhName()))
                .append(",\"enName\":").append(quote(s.enName())).append(",\"school\":").append(quote(s.school().name()))
                .append(",\"tier\":").append(s.tier()).append(",\"manaCost\":").append(s.manaCost())
                .append(",\"cooldownTicks\":").append(s.cooldownTicks()).append(",\"castTicks\":").append(s.castTicks())
                .append(",\"range\":").append(number(s.range())).append(",\"radius\":").append(number(s.radius()))
                .append(",\"damage\":").append(number(s.damage())).append(",\"mechanics\":[")
                .append(s.mechanics().stream().map(m -> quote(m.name())).collect(Collectors.joining(",")))
                .append("],\"zhDescription\":").append(quote(s.zhDescription())).append(",\"enDescription\":").append(quote(s.enDescription()))
                .append(",\"behaviour\":").append(behaviour(s.behaviour())).append("}");
            json.append(i + 1 == SpellCatalogue.all().size() ? "\n" : ",\n");
        }
        json.append("  ]\n}\n");
        Files.writeString(root.resolve("docs/magic-catalogue.json"), json, StandardCharsets.UTF_8);

        StringBuilder md = new StringBuilder("# 魔法目录：280 种普通魔法与 20 种超位魔法\n\n");
        md.append("本表由 `scripts/ExportMagicCatalogue.java` 从 `SpellCatalogue` 导出。操作、掉落和伴生物品见[魔法玩法说明](magic-system.md)。每位玩家最多学习三种；同阶内的书等概率抽取。伤害列是运行时计算使用的基础伤害点数（2 点 = 1 颗心），不等于整次法术必然造成的总伤害：投射方式、命中次数、学派互动、护甲、抗性、遮挡与事件取消都会影响结果。\n\n");
        md.append("300 项各有显式的独立触发配置，包括首次命中、重复命中、持续脉冲与结束后动作；条件包含残血、悬空、着地、潮湿、燃烧和距离。即便忽略 ID、名称、学派、颜色及数值，300 项的触发/动作结构与四阶段动态图样仍各不相同。碰撞、生命周期与 24 种动作组件由服务器执行器复用。超位还有专属阶段时长及开场/收尾动作。\n\n");
        md.append("## 阶位与抽取率\n\n| 阶位 | 本数 | 每个自然箱的书概率 | 每个空投的书概率 |\n| --- | ---: | ---: | ---: |\n");
        for (int tier = 1; tier <= 8; tier++) md.append("| ").append(tier == 8 ? "8（超位）" : tier).append(" | ")
            .append(SpellCatalogue.tier(tier).size()).append(" | ").append(String.format(Locale.ROOT, "%.4f%%", MagicLootRules.naturalProbability(tier) * 100))
            .append(" | ").append(String.format(Locale.ROOT, "%.2f%%", MagicLootRules.airdropProbability(tier) * 100)).append(" |\n");
        md.append("\n自然箱先以 12% 概率抽一本书；上表已包含这一步。空投必含一本书。模组自然箱只往空槽加入物品，若原箱已满，成功抽取也不会覆盖原物资。\n\n");
        for (MagicSchool school : MagicSchool.values()) {
            md.append("## ").append(school.zhName()).append(" / ").append(school.enName()).append("\n\n").append(school.zhEffect()).append("。\n\n");
            appendHeader(md);
            for (SpellDefinition spell : SpellCatalogue.all()) if (!spell.superTier() && spell.school() == school) appendSpell(md, spell);
            md.append("\n");
        }
        md.append("## 超位魔法\n\n超位书使用第 8 阶外观，需要 160–217 魔力；初始上限 100，必须先提高上限。阶段依序安排，共用瞄准区域；`CHANNEL` 开始后仍受引导条件及额外魔力消耗限制。\n\n");
        appendHeader(md);
        for (SpellDefinition spell : SpellCatalogue.all()) if (spell.superTier()) appendSpell(md, spell);
        md.append("\n## 超位独立阶段\n\n开场与收尾的增益只作用于施法者；其余动作只作用于落点附近有视线的合法敌人，不递归触发新的法术。时长是每个基础机制的存续窗口，开始时间依前序阶段递进。\n\n| 法术 | 阶段：持续秒数 / 开场 / 收尾 |\n| --- | --- |\n");
        for (SpellDefinition spell : SpellCatalogue.all()) if (spell.superTier()) {
            md.append("| ").append(spell.zhName()).append(" | ").append(spell.behaviour().stages().stream()
                .map(stage -> stage.mechanic() + ": " + number(stage.durationTicks() / 20.0) + "秒 / "
                    + stage.opening().stream().map(SpellBehaviour.Action::zh).collect(Collectors.joining("、")) + " / "
                    + stage.closing().stream().map(SpellBehaviour.Action::zh).collect(Collectors.joining("、")))
                .collect(Collectors.joining(" → "))).append(" |\n");
        }
        md.append("\n## 图样与动作\n\n每个法术的吟唱、飞行、命中和收尾分别使用几何图样，与旋转、反转、脉动、上升、折叠、公转或内收运动相组合。采样点硬上限 64；高阶层次仍受服务器总粒子预算约束。图样不是仅靠改变粒子颜色或数字种子产生差异。\n\n");
        md.append("动作：").append(Arrays.stream(SpellBehaviour.Action.values()).map(SpellBehaviour.Action::zh).collect(Collectors.joining("、"))).append("。\n");
        md.append("\n## 机制标识\n\n").append(Arrays.stream(MagicMechanic.values()).map(Enum::name).collect(Collectors.joining("、"))).append("。中文行为说明包含在各条目的最后一列。\n");
        Files.writeString(root.resolve("docs/magic-catalogue.md"), md, StandardCharsets.UTF_8);
        System.out.println("Exported " + SpellCatalogue.all().size() + " spells to docs/magic-catalogue.{json,md}");
    }
    private static void appendHeader(StringBuilder md) {
        md.append("| 名称 / ID | 阶位 | 魔力 | 冷却/吟唱（秒） | 距离/半径（格） | 基础伤害 | 机制 | 行为 | 吟唱/飞行/命中/收尾/运动 |\n| --- | ---: | ---: | --- | --- | ---: | --- | --- | --- |\n");
    }
    private static void appendSpell(StringBuilder md, SpellDefinition s) {
        md.append("| ").append(s.zhName()).append(" / `").append(s.id()).append("` | ").append(s.tier()).append(" | ").append(s.manaCost())
            .append(" | ").append(number(s.cooldownTicks() / 20.0)).append(" / ").append(number(s.castTicks() / 20.0))
            .append(" | ").append(number(s.range())).append(" / ").append(number(s.radius())).append(" | ").append(number(s.damage()))
            .append(" | ").append(mechanics(s)).append(" | ").append(s.zhDescription().replace("|", "\\|")).append(" | ")
            .append(s.behaviour().visual().fingerprint()).append(" |\n");
    }

    private static String behaviour(SpellBehaviour.Plan plan) {
        String rules = plan.rules().stream().map(rule -> "{\"event\":" + quote(rule.event().name())
            + ",\"condition\":" + quote(rule.condition().name()) + ",\"target\":" + quote(rule.target().name())
            + ",\"actions\":[" + rule.actions().stream().map(a -> quote(a.name())).collect(Collectors.joining(","))
            + "],\"limit\":" + rule.limit() + ",\"intervalTicks\":" + rule.intervalTicks()
            + ",\"delayTicks\":" + rule.delayTicks() + ",\"power\":" + number(rule.power()) + "}").collect(Collectors.joining(","));
        String stages = plan.stages().stream().map(stage -> "{\"mechanic\":" + quote(stage.mechanic().name())
            + ",\"durationTicks\":" + stage.durationTicks() + ",\"opening\":["
            + stage.opening().stream().map(a -> quote(a.name())).collect(Collectors.joining(",")) + "],\"closing\":["
            + stage.closing().stream().map(a -> quote(a.name())).collect(Collectors.joining(",")) + "]}").collect(Collectors.joining(","));
        var visual = plan.visual();
        return "{\"rules\":[" + rules + "],\"stages\":[" + stages + "],\"visual\":{\"cast\":" + quote(visual.cast().name())
            + ",\"travel\":" + quote(visual.travel().name()) + ",\"impact\":" + quote(visual.impact().name())
            + ",\"finish\":" + quote(visual.finish().name()) + ",\"motion\":" + quote(visual.motion().name()) + "}}";
    }
}
