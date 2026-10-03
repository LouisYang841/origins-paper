# OriginsPaper 手工回归清单

测试服：`~/origins-paper/server`（Purpur 1.21.1，offline 模式，端口 25565）。
准备：两个测试号（A 主测、B 辅助观察）。控制台：`origin set <玩家> <id>`，玩家自查 `/origin info`。
主动技能键位：**潜行 + 副手切换（F）** = primary；直接副手切换 = secondary。

## human 人类
- [ ] 无任何能力，各项 vanilla 行为正常

## avian 羽人
- [ ] 移速 +20%（tailwind）
- [ ] 潜行/不潜行下坠落缓慢（slow_falling，只保证不摔伤）
- [ ] 睡眠高度 Y<86 被拒并提示（fresh_air）
- [ ] 蹭墙 + toggle 开启时可爬墙（climbing，主动：潜行+F）
- [ ] 不吃肉（vegetarian：右键/食用肉类被拒）
- [ ] 醒来获得鸡蛋（lay_eggs）
- [ ] like_air：飞行速度同步移速（近似实现）

## blazeborn 炽焰之子
- [ ] 火/岩浆免伤（fire_immunity）
- [ ] 中毒/饥饿效果免疫（hotblooded）
- [ ] 下界重生（nether_spawn，注意非床重生）
- [ ] 水中/雨中持续受伤（water_vulnerability）
- [ ] 日光下燃烧（burn_in_daylight）

## enderian 末影裔
- [ ] 徒手/任意工具快速挖石（strong_arms_break_speed 近似：急迫）
- [ ] 石头无工具可采集掉落（modify_harvest 近似）
- [ ] 主动：扔出末影珍珠（throw_ender_pearl，潜行+F，CD 1.5s）
- [ ] 怕水（water_vulnerability？enderian 受水伤害）
- [ ] 疾跑跳跃更高（sprint_jump）
- [ ] 恐惧小黑（claustrophobia，狭小空间叠加虚弱/缓慢）

## feline 猫裔
- [ ] 摔落无伤（fall_immunity）
- [ ] 缓慢潜行无视野收缩（velvet_paws 仅降级：sculk 震动未实现）
- [ ] 夜视（cat_vision）
- [ ] 不能挖石（weak_arms 近似：挖掘疲劳）
- [ ] 不吃素（carnivore）

## merling 深海人鱼
- [ ] 水下呼吸（water_breathing）
- [ ] 水下挖掘加速（aqua_affinity 近似）
- [ ] 水下夜视 toggle（water_vision）
- [ ] 陆上持续掉血（water_vulnerability + no_water_for_gills）

## shulk 潜影贝裔
- [ ] 天生护甲（natural_armor）
- [ ] 血上限 -6（fragile）
- [ ] 狭小空间获得负面效果（claustrophobia）
- [ ] 额外背包 → **降级：未实现**（控制台应出现"暂不支持"）

## arachnid 蛛裔
- [ ] 吃肉限定（carnivore）
- [ ] 爬墙 toggle（climbing）
- [ ] 蛛网不减速（no_cobweb_slowdown）
- [ ] 命中目标结网（master_of_webs webbing）
- [ ] 命中目标发光/可见（master_of_webs entity_glow 近似）
- [ ] 耐久 -4 血（fragile）

## elytrian 翼灵
- [ ] 摔落/冲撞伤害 ×1.5（more_kinetic_damage）
- [ ] 只能穿轻甲（light_armor：胸甲>5 防弹回）
- [ ] 飞行时伤害加成（aerial_combatant）
- [ ] 天生鞘翅 → **降级：未实现**
- [ ] 血上限 -6（fragile）

## phantom 幻影
- [ ] 主动：幻影化 toggle（潜行+F；食物>6 才可用）
- [ ] 幻影化时隐身（invisibility）
- [ ] 幻影化时饥饿加速（hunger_over_time）
- [ ] 日光燃烧（burn_in_daylight；隐身时不烧）
- [ ] 重生后自动恢复幻影化（phantomize_callback）
- [ ] 穿墙（phasing）→ **降级：未实现**
- [ ] 离玩家远处自然恢复（nine_lives？无——幻影有 regenerate？核对）

## 通用
- [ ] 首次进服自动弹起源选择 GUI
- [ ] `/origin select` 重新打开 GUI，点击切换生效
- [ ] `/origin set <玩家> <id>` 管理员改起源
- [ ] `/origin reload` 重载内容
- [ ] 退出重进：起源持久化
- [ ] 未支持的 power 在设置起源时控制台打印"暂不支持"（inventory/phasing/elytra_flight/overlay/model_color/recipe/prevent_game_event/prevent_block_selection/prevent_entity_render）
