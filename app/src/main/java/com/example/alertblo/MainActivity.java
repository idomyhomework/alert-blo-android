package com.example.alertblo;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.alertblo.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity{

    public static String ID_DISPOSITIVO;
    public static final String IP_SERVIDOR = BuildConfig.IP_SERVIDOR;
    public static final String ACCION_NUEVA_ALERTA = "nueva_alerta";

    private static final String PREFS_NOMBRE = "alertas_prefs";
    private static final String PREFS_CLAVE  = "historial_alertas";

    // Lista en memoria — se carga desde SharedPreferences al arrancar
    public static final List<Alerta> alertasRecibidas = new ArrayList<>();

    private AlertaAdapter adapter;
    private EditText editTextAlerta;
    private Switch switchSilencio;

    // Receptor local que refresca la lista cuando llega una alerta nueva
    private final BroadcastReceiver receptorAlerta = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            adapter.notifyDataSetChanged();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prepararApp();

        editTextAlerta = findViewById(R.id.editTextAlerta);
        switchSilencio = findViewById(R.id.switchSilencio);
        Button btnCrearAlerta = findViewById(R.id.btnCrearAlerta);
        ListView listaAlertas = findViewById(R.id.listaAlertas);

        // Cargar historial guardado antes de conectar el adapter
        cargarHistorial(this);

        adapter = new AlertaAdapter(this, alertasRecibidas);
        listaAlertas.setAdapter(adapter);

        btnCrearAlerta.setOnClickListener(v -> crearAlerta());

        // Consultar el servidor por si hay alguna alerta nueva
        new Thread(() -> getAlerta(this)).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                receptorAlerta, new IntentFilter(ACCION_NUEVA_ALERTA));
        verificarAccesoDnd();
    }

    // Comprueba si la app tiene permiso para saltarse el modo No Molestar.
    // Si no lo tiene, muestra un diálogo para que el usuario lo conceda.
    private void verificarAccesoDnd() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (!nm.isNotificationPolicyAccessGranted()) {
            new AlertDialog.Builder(this)
                    .setTitle("Permiso necesario")
                    .setMessage("Para que las alertas críticas suenen aunque el móvil esté en silencio, necesitamos acceso a \"No molestar\". Pulsa Aceptar para concederlo.")
                    .setPositiveButton("Aceptar", (d, w) ->
                            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
                    .setNegativeButton("Cancelar", null)
                    .show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receptorAlerta);
    }

    // Consulta al servidor si hay una alerta nueva para este dispositivo.
    public static void getAlerta(Context context) {
        Alerta alerta = Servidor.getAlerta(ID_DISPOSITIVO);
        if (alerta != null) {
            int idxPendiente = -1;
            for (int i = 0; i < alertasRecibidas.size(); i++) {
                if (Alerta.PENDIENTE.equals(alertasRecibidas.get(i).getEstado())
                        && alertasRecibidas.get(i).getTextoAlerta().equals(alerta.getTextoAlerta())) {
                    idxPendiente = i;
                    break;
                }
            }
            if (idxPendiente >= 0) {
                alertasRecibidas.get(idxPendiente).estado = Alerta.RECIBIDA;
            } else {
                alertasRecibidas.add(0, alerta);
            }
            guardarHistorial(context);
            LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(new Intent(ACCION_NUEVA_ALERTA));
            GeoHandler.obtenerUbicacion(context, loc -> {
                boolean enZona = GeoHandler.estaDentroDeZona(loc);
                mostrarNotificacionAlerta(context, alerta, enZona);
            });
        }
    }

    // Guarda la lista de alertas en SharedPreferences como JSON
    private static void guardarHistorial(Context context) {
        JSONArray json = new JSONArray();
        for (Alerta alerta : alertasRecibidas) {
            try {
                json.put(alerta.toJson());
            } catch (Exception e) {
                // Error al serializar
            }
        }
        context.getSharedPreferences(PREFS_NOMBRE, Context.MODE_PRIVATE)
                .edit()
                .putString(PREFS_CLAVE, json.toString())
                .apply();
    }
    // Carga el historial guardado en SharedPreferences y lo añade a la lista
    private static void cargarHistorial(Context context) {
        alertasRecibidas.clear();
        String json = context.getSharedPreferences(PREFS_NOMBRE, Context.MODE_PRIVATE)
                .getString(PREFS_CLAVE, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                Alerta alerta = Alerta.fromJson(array.getJSONObject(i));
                alertasRecibidas.add(alerta);
            }
        } catch (Exception e) {
            // Si el JSON está corrupto, empezamos con lista vacía
        }
    }

    // Muestra una notificación con el texto de la alerta, suena aunque el móvil esté en silencio.
    private static void mostrarNotificacionAlerta(Context context, Alerta alerta, boolean enZona) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        String canalId = (!alerta.isSilent() && enZona) ? "canal_alerta_urgente_v2" : "canal_alerta_normal_v2";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, canalId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("¡Alerta!")
                .setContentText(alerta.getTextoAlerta())
                .setPriority(NotificationCompat.PRIORITY_MAX);

        if (!alerta.isSilent() && enZona) {
            builder.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
            builder.setVibrate(new long[]{0, 500});
        }

        NotificationManagerCompat.from(context).notify(2, builder.build());
    }

    // Envía una nueva alerta al servidor con el texto introducido por el usuario.
    private void crearAlerta() {
        String textoAlerta = editTextAlerta.getText().toString().trim();
        if (textoAlerta.isEmpty()) {
            Toast.makeText(this, "Escribe el texto de la alerta", Toast.LENGTH_SHORT).show();
            return;
        }
        int silencio = switchSilencio.isChecked() ? 1 : 0;
        String hora = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date());
        Alerta nueva = new Alerta(0, textoAlerta, hora, silencio, Alerta.PENDIENTE);
        alertasRecibidas.add(0, nueva);
        guardarHistorial(this);
        adapter.notifyDataSetChanged();
        editTextAlerta.setText("");

        new Thread(() -> {
            boolean ok = Servidor.crearAlerta(ID_DISPOSITIVO, textoAlerta, silencio);
            if (!ok) {
                runOnUiThread(() -> {
                    nueva.estado = Alerta.FALLIDA;
                    guardarHistorial(this);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Error al enviar la alerta", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private static final int RC_PERMISOS = 10;

    // Obtiene el ID del dispositivo y pide los permisos necesarios.
    // El servicio se arranca en onRequestPermissionsResult, una vez resueltos
    // los permisos, para no lanzar un foreground service de tipo location sin
    // tener el permiso concedido (provocaría un cierre de la app en Android 14+).
    private void prepararApp() {
        ID_DISPOSITIVO = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ANDROID_ID
        );

        List<String> permisos = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        permisos.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        permisos.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);

        requestPermissions(permisos.toArray(new String[0]), RC_PERMISOS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_PERMISOS) {
            // Los permisos ya están resueltos: el servicio puede arrancar con el
            // tipo correcto (con o sin location según lo concedido).
            iniciarServicio();
        }
    }

    private void iniciarServicio() {
        Intent srv = new Intent(this, AlertaService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(srv);
        } else {
            startService(srv);
        }
    }
}
