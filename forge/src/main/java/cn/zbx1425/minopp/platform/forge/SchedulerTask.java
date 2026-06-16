package cn.zbx1425.minopp.platform.forge;

import cn.zbx1425.minopp.game.TaskScheduler;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SchedulerTask implements TaskScheduler {
    private static final List<ScheduledTask> tasks = new ArrayList<>();

    private static class ScheduledTask {
        int ticksRemaining;
        Runnable task;
        ScheduledTask(int ticks, Runnable task) {
            this.ticksRemaining = ticks;
            this.task = task;
        }
    }

    @Override
    public void schedule(int ticks, Runnable task) {
        tasks.add(new ScheduledTask(ticks, task));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (tasks.isEmpty() || event.phase != TickEvent.Phase.END) return;
        Iterator<ScheduledTask> it = tasks.iterator();
        while (it.hasNext()) {
            ScheduledTask t = it.next();
            if (--t.ticksRemaining <= 0) {
                t.task.run();
                it.remove();
            }
        }
    }
}