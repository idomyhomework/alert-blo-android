package com.example.alertblo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

// Relanza el servicio de alertas tras reiniciar el móvil. Sin esto, el
// servicio solo arranca cuando el usuario abre la app manualmente, así que
// tras un reinicio dejarían de llegar alertas hasta volver a abrirla.
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String accion = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(accion)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(accion)
                || "android.intent.action.QUICKBOOT_POWERON".equals(accion)) {
            Intent srv = new Intent(context, AlertaService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(srv);
            } else {
                context.startService(srv);
            }
        }
    }
}
