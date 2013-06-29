package net.easymodo.asagi;


public class ThreadUtils {
    public static final Thread.UncaughtExceptionHandler UNCAUGHT_EXCEPTION_HANDLER = new DumperUncaughtExceptionHandler();

    public static class DumperUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            System.err.print("Exception in thread: \"" + t.getName() + "\" ");
            e.printStackTrace();
            if (e instanceof OutOfMemoryError) {
                System.err.println("Terminating due to out of memory error. Raise VM max heap size? (-Xmx)");
            } else {
                System.err.println("Terminating dumper due to unexpected exception.");
                System.err.println("Please report this issue if you believe it is a bug.");
            }
            System.exit(-1);
        }
    }

    public static void initThread(String boardName, Runnable runnable, String threadName, int numberOfThreads) {
        for (int i = 0; i < numberOfThreads; i++) {
            Thread threadToLaunch = new Thread(runnable);
            if (numberOfThreads > 1)
                threadToLaunch.setName(threadName + " #" + i + " - " + boardName);
            else
                threadToLaunch.setName(threadName + " - " + boardName);
            threadToLaunch.setUncaughtExceptionHandler(UNCAUGHT_EXCEPTION_HANDLER);
            threadToLaunch.start();
        }
    }
}
