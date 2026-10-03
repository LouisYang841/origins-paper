package eto.origins.engine;

/** 可被按键触发的主动 power。 */
public interface ActivePower {

    /** "primary_active" / "secondary_active" */
    String keySlot();

    /**
     * 触发；返回 false 表示未实际生效（如条件不满足），不进冷却。
     */
    boolean onTrigger();
}
