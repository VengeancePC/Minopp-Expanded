// common: cn.zbx1425.minopp.game.TaskScheduler
package cn.zbx1425.minopp.game;

public interface TaskScheduler {
    void schedule(int ticks, Runnable task);

    class Holder {
        public static TaskScheduler INSTANCE;
    }
}