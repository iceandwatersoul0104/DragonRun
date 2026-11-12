package com.acooldog.fuckrun;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.acooldog.fuckrun.data.AppPrefs;

/** 接收注入端广播，用于界面显示“已连接”。 */
public class StatusReceiver extends BroadcastReceiver {
    public static final String ACTION_STATUS = "com.acooldog.fuckrun.STATUS";
    public static final String ACTION_HOOK_READY = "com.acooldog.fuckrun.HOOK_READY";

    @Override
    public void onReceive(Context context, Intent intent) {
        App.markHookSeen();
        new AppPrefs(context).markHookSeen();
    }
}
