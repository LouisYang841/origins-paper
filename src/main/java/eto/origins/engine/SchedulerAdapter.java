package eto.origins.engine;

import com.google.gson.JsonObject;

/**
 * 调度抽象层。所有调度调用必须经由本接口，禁止直接使用 Bukkit 调度器。
 * 将来迁移 Folia 时只需提供新实现，不改动业务代码。
 */
public interface SchedulerAdapter {

    /** 在拥有该实体的线程上执行（Folia: region/entity scheduler；Paper: 全局主线程）。 */
    void runForEntity(org.bukkit.entity.Entity entity, Runnable task);

    /** 在指定位置所属区域线程上延迟执行，单位 tick。 */
    void runAtLocation(org.bukkit.Location location, long delayTicks, Runnable task);

    /** 全局周期任务（Paper: 全局主线程；Folia 下应避免使用，仅限无世界访问的任务）。 */
    Cancelable runGlobalTimer(long delayTicks, long periodTicks, Runnable task);

    /** 异步执行（IO 等）。 */
    void runAsync(Runnable task);

    interface Cancelable {
        void cancel();
    }
}
