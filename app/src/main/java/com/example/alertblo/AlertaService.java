package com.example.alertblo;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.*;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;

public class AlertaService extends Service {

    private static final String CANAL_SERVICIO = "canal_servei";
    private static final int    INTERVALO_MS   = 20_000;
    private static final int    NOTIF_SERVICIO = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // Runnable que se ejecuta cada INTERVALO_MS y comprueba si hay alertas nuevas
    private final Runnable comprobador = new Runnable()  {
        @Override
        public void run() {
            new Thread(() -> {
                MainActivity.getAlerta(AlertaService.this);
            }).start();
            handler.postDelayed(this, INTERVALO_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        crearCanales();
        // Notificación persistente obligatoria para ForegroundService
        Notification notifServicio = new NotificationCompat.Builder(this, CANAL_SERVICIO)
                .setContentTitle("Servicio de alertas activo")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
        // En Android 10+ se debe declarar el tipo de servicio. Solo incluimos
        // location si el permiso está concedido; de lo contrario el sistema
        // lanza ForegroundServiceStartNotAllowedException y la app se cierra.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int tipo = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            if (tienePermisoUbicacion()) {
                tipo |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            }
            startForeground(NOTIF_SERVICIO, notifServicio, tipo);
        } else {
            startForeground(NOTIF_SERVICIO, notifServicio);
        }
    }

    private boolean tienePermisoUbicacion() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.post(comprobador);
        // START_STICKY: Android reinicia el servicio si lo mata
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(comprobador);
        super.onDestroy();
    }

    // Crea los dos canales de notificación
    private void crearCanales() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            // Canal NORMAL — respeta silencio del móvil.
            nm.deleteNotificationChannel("canal_alerta_normal");
            nm.deleteNotificationChannel("canal_alerta_normal_v2");
            NotificationChannel canalNormal = new NotificationChannel(
                    "canal_alerta_normal_v2",
                    "Alertas normales",
                    NotificationManager.IMPORTANCE_HIGH);
            canalNormal.setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .build()
            );
            nm.createNotificationChannel(canalNormal);

            // Canal URGENTE — ignora silencio del móvil.
            nm.deleteNotificationChannel("canal_alerta_urgente");
            nm.deleteNotificationChannel("canal_alerta_urgente_v2");
            NotificationChannel canalUrgente = new NotificationChannel(
                    "canal_alerta_urgente_v2",
                    "Alertas urgentes",
                    NotificationManager.IMPORTANCE_HIGH);
            canalUrgente.setBypassDnd(true);
            canalUrgente.setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .build()
            );
            canalUrgente.enableVibration(true);
            canalUrgente.setVibrationPattern(new long[]{0, 500, 200, 500});
            nm.createNotificationChannel(canalUrgente);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
