package org.gstk;

public class Constants {
    public static final String PROJECT_VERSION = "2.0.0";

    public static final int TILE_DOWNLOAD_ATTEMPTS = 15;
    public static final int DOWNLOAD_RETRY_DELAY_MS = 2000;
    public static final int TILE_WRITE_INTERVAL = 500;
    public static final int MAX_SINGLE_THREAD_DOWNLOAD = 100;
    public static final int EXECUTOR_TIMEOUT_HOURS = 744;  // 24 hours * 31 days = 1 month
}