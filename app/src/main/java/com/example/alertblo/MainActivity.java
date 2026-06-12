package com.example.alertblo;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.alertblo.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity{

    public static String ID_DISPOSITIVO;
    public static final String IP_SERVIDOR = BuildConfig.IP_SERVIDOR;
    public static final String ACCION_NUEVA_ALERTA = "nueva_alerta";

    private static final String PREFS_NOMBRE = "alertas_prefs";
    private static final String PREFS_CLAVE  = "historial_alertas";

    // Lista en memoria — se carga desde SharedPreferences al arrancar
    public static final List<Alerta> alertasRecibidas = new ArrayList<>();

    // Lista que ve la ListView: es la maestra filtrada. No tocar la maestra
    // al filtrar para no romper getAlerta() ni la persistencia.
    private final List<Alerta> alertasMostradas = new ArrayList<>();

    // Tipos de filtro por categoría de alerta.
    private static final int TIPO_TODAS       = 0;
    private static final int TIPO_URGENTES    = 1;
    private static final int TIPO_SILENCIADAS = 2;

    // Estado actual de los filtros.
    private String textoFiltro = "";
    private int tipoFiltro = TIPO_TODAS;
    private String fechaFiltro = null; // "yyyy-MM-dd" o null = sin filtro

    private AlertaAdapter adapter;
    private AlertDialog dialogoDnd;

    private View panelFiltros;
    private EditText editBuscar;
    private Button btnFiltroTodas, btnFiltroUrgentes, btnFiltroSilenciadas, btnFiltroFecha;
    private View textoVacio;
    private Button btnBorrarHistorial;

    // Receptor local que refresca la lista cuando llega una alerta nueva
    private final BroadcastReceiver receptorAlerta = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Reaplicar el filtro vigente para que la alerta nueva respete
            // la búsqueda/tipo/fecha activos.
            aplicarFiltro();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // El ID del dispositivo se necesita siempre (también tras recrear la Activity).
        ID_DISPOSITIVO = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ANDROID_ID);

        // Permisos y servicio SOLO en el arranque real. Si savedInstanceState != null
        // es una recreación (p. ej. cambio de idioma): no los repetimos, porque
        // relanzar el foreground service en recreaciones rápidas provoca
        // ForegroundServiceStartNotAllowedException y la app se cierra.
        if (savedInstanceState == null) {
            prepararApp();
        }

        ListView listaAlertas = findViewById(R.id.listaAlertas);

        // Selector de idioma (bandera en el header)
        ImageButton btnIdioma = findViewById(R.id.btnIdioma);
        actualizarIconoIdioma(btnIdioma);
        btnIdioma.setOnClickListener(v -> alternarIdioma());

        // Cargar historial guardado antes de conectar el adapter
        cargarHistorial(this);

        // La ListView muestra la lista filtrada, no la maestra.
        adapter = new AlertaAdapter(this, alertasMostradas);
        listaAlertas.setAdapter(adapter);

        configurarFiltros();
        aplicarFiltro();

        // Consultar el servidor por si hay alguna alerta nueva
        new Thread(() -> getAlerta(this)).start();
    }

    // Devuelve el código del idioma activo de la app ("es" o "ca").
    private String idiomaActual() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (!locales.isEmpty()) {
            return locales.get(0).getLanguage();
        }
        return getResources().getConfiguration().getLocales().get(0).getLanguage();
    }

    // Pone en el botón la bandera del idioma activo.
    private void actualizarIconoIdioma(ImageButton btn) {
        boolean valenciano = "ca".equals(idiomaActual());
        btn.setImageResource(valenciano ? R.drawable.flag_va : R.drawable.flag_es);
    }

    // Alterna entre castellano y valenciano. AppCompat recrea la Activity y
    // guarda el idioma elegido automáticamente (ver autoStoreLocales en el manifest).
    private void alternarIdioma() {
        String siguiente = "ca".equals(idiomaActual()) ? "es" : "ca";
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(siguiente));
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
        // No mostrar el diálogo si la Activity se está cerrando/recreando
        // (evita BadTokenException al cambiar de idioma) o si ya está visible.
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (dialogoDnd != null && dialogoDnd.isShowing()) {
            return;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (!nm.isNotificationPolicyAccessGranted()) {
            dialogoDnd = new AlertDialog.Builder(this)
                    .setTitle(R.string.permiso_titulo)
                    .setMessage(R.string.permiso_mensaje)
                    .setPositiveButton(R.string.aceptar, (d, w) ->
                            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
                    .setNegativeButton(R.string.cancelar, null)
                    .create();
            dialogoDnd.show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receptorAlerta);
    }

    @Override
    protected void onDestroy() {
        // Cerrar el diálogo si sigue abierto al recrear/destruir la Activity,
        // para no filtrar la ventana (WindowLeaked) ni provocar un cierre.
        if (dialogoDnd != null && dialogoDnd.isShowing()) {
            dialogoDnd.dismiss();
        }
        dialogoDnd = null;
        super.onDestroy();
    }

    // Enlaza los controles del panel de filtros y sus listeners.
    private void configurarFiltros() {
        Button btnFiltros          = findViewById(R.id.btnFiltros);
        panelFiltros               = findViewById(R.id.panelFiltros);
        editBuscar                 = findViewById(R.id.editBuscar);
        btnFiltroTodas             = findViewById(R.id.btnFiltroTodas);
        btnFiltroUrgentes          = findViewById(R.id.btnFiltroUrgentes);
        btnFiltroSilenciadas       = findViewById(R.id.btnFiltroSilenciadas);
        btnFiltroFecha             = findViewById(R.id.btnFiltroFecha);
        Button btnLimpiar          = findViewById(R.id.btnLimpiarFiltros);
        textoVacio                 = findViewById(R.id.textoVacio);
        btnBorrarHistorial         = findViewById(R.id.btnBorrarHistorial);

        // Botón "Filtros"/"Ocultar": muestra u oculta el panel y cambia su texto.
        btnFiltros.setOnClickListener(v -> {
            boolean visible = panelFiltros.getVisibility() == View.VISIBLE;
            panelFiltros.setVisibility(visible ? View.GONE : View.VISIBLE);
            btnFiltros.setText(visible ? R.string.btn_filtros : R.string.ocultar_filtros);
        });

        // Búsqueda en vivo por texto.
        editBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                textoFiltro = s.toString();
                aplicarFiltro();
            }
        });

        // Filtro por tipo.
        btnFiltroTodas.setOnClickListener(v -> seleccionarTipo(TIPO_TODAS));
        btnFiltroUrgentes.setOnClickListener(v -> seleccionarTipo(TIPO_URGENTES));
        btnFiltroSilenciadas.setOnClickListener(v -> seleccionarTipo(TIPO_SILENCIADAS));

        // Filtro por fecha.
        btnFiltroFecha.setOnClickListener(v -> mostrarSelectorFecha());

        // Limpiar filtros y borrar historial.
        btnLimpiar.setOnClickListener(v -> limpiarFiltros());
        btnBorrarHistorial.setOnClickListener(v -> confirmarBorrado());

        actualizarBotonesTipo();
    }

    private void seleccionarTipo(int tipo) {
        tipoFiltro = tipo;
        actualizarBotonesTipo();
        aplicarFiltro();
    }

    // Resalta el botón de tipo activo bajando la opacidad de los demás.
    private void actualizarBotonesTipo() {
        btnFiltroTodas.setAlpha(tipoFiltro == TIPO_TODAS ? 1f : 0.4f);
        btnFiltroUrgentes.setAlpha(tipoFiltro == TIPO_URGENTES ? 1f : 0.4f);
        btnFiltroSilenciadas.setAlpha(tipoFiltro == TIPO_SILENCIADAS ? 1f : 0.4f);
    }

    // Abre un DatePickerDialog y aplica la fecha elegida como filtro.
    private void mostrarSelectorFecha() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            // Guardamos en ISO para comparar; mostramos en formato local.
            fechaFiltro = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
            btnFiltroFecha.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month + 1, year));
            aplicarFiltro();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    // Resetea todos los filtros a su estado inicial.
    private void limpiarFiltros() {
        textoFiltro = "";
        editBuscar.setText("");
        tipoFiltro = TIPO_TODAS;
        actualizarBotonesTipo();
        fechaFiltro = null;
        btnFiltroFecha.setText(R.string.filtro_fecha);
        aplicarFiltro();
    }

    // Reconstruye la lista mostrada aplicando texto + tipo + fecha (AND).
    private void aplicarFiltro() {
        alertasMostradas.clear();
        String q = textoFiltro.toLowerCase(Locale.getDefault()).trim();
        for (Alerta a : alertasRecibidas) {
            if (!q.isEmpty()
                    && (a.getTextoAlerta() == null
                        || !a.getTextoAlerta().toLowerCase(Locale.getDefault()).contains(q))) {
                continue;
            }
            if (tipoFiltro == TIPO_URGENTES && a.isSilent()) {
                continue;
            }
            if (tipoFiltro == TIPO_SILENCIADAS && !a.isSilent()) {
                continue;
            }
            if (fechaFiltro != null && !coincideFecha(a, fechaFiltro)) {
                continue;
            }
            alertasMostradas.add(a);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        // El mensaje de "sin alertas" y el botón de borrar dependen de si se ha
        // recibido alguna alerta (lista maestra), no del resultado del filtro.
        boolean sinAlertas = alertasRecibidas.isEmpty();
        if (textoVacio != null) {
            textoVacio.setVisibility(sinAlertas ? View.VISIBLE : View.GONE);
        }
        if (btnBorrarHistorial != null) {
            btnBorrarHistorial.setVisibility(sinAlertas ? View.GONE : View.VISIBLE);
        }
    }

    // Formato en que el servidor guarda DATA_CREACIO ("yyyy-MM-dd HH:mm:ss").
    private static final SimpleDateFormat FORMATO_SERVIDOR =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private static final SimpleDateFormat FORMATO_DIA =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    // Compara solo el día (ignora la hora). Parsea la fecha guardada como
    // string y la reformatea a "yyyy-MM-dd" para compararla con la elegida.
    private boolean coincideFecha(Alerta a, String fechaIso) {
        String f = a.getFechaCreacion();
        if (f == null) {
            return false;
        }
        try {
            Date d = FORMATO_SERVIDOR.parse(f.trim());
            return FORMATO_DIA.format(d).equals(fechaIso);
        } catch (ParseException e) {
            // Si el formato no coincide, comparamos el prefijo de fecha.
            return f.trim().startsWith(fechaIso);
        }
    }

    // Pide confirmación antes de vaciar el historial almacenado en el dispositivo.
    private void confirmarBorrado() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.borrar_titulo)
                .setMessage(R.string.borrar_mensaje)
                .setPositiveButton(R.string.borrar, (d, w) -> borrarHistorial())
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    // Vacía la lista maestra, persiste la lista vacía y refresca la vista.
    private void borrarHistorial() {
        alertasRecibidas.clear();
        guardarHistorial(this);
        aplicarFiltro();
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
                .setContentTitle(context.getString(R.string.alerta_titulo))
                .setContentText(alerta.getTextoAlerta())
                .setPriority(NotificationCompat.PRIORITY_MAX);

        if (!alerta.isSilent() && enZona) {
            builder.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
            builder.setVibrate(new long[]{0, 500});
        }

        NotificationManagerCompat.from(context).notify(2, builder.build());
    }

    private static final int RC_PERMISOS = 10;

    // Obtiene el ID del dispositivo y pide los permisos necesarios.
    // El servicio se arranca en onRequestPermissionsResult, una vez resueltos
    // los permisos, para no lanzar un foreground service de tipo location sin
    // tener el permiso concedido (provocaría un cierre de la app en Android 14+).
    private void prepararApp() {
        // Solo pedimos los permisos que aún falten. Así, cuando la Activity se
        // recrea (p. ej. al cambiar de idioma) y los permisos ya están concedidos,
        // no se vuelve a mostrar el prompt una y otra vez.
        List<String> pendientes = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            pendientes.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            pendientes.add(Manifest.permission.ACCESS_FINE_LOCATION);
            pendientes.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (pendientes.isEmpty()) {
            // Permisos ya concedidos: arrancamos el servicio directamente.
            iniciarServicio();
        } else {
            requestPermissions(pendientes.toArray(new String[0]), RC_PERMISOS);
        }
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
        pedirExencionBateria();
    }

    // Pide al sistema que excluya la app de la optimización de batería (Doze).
    // Sin esto, con la pantalla apagada el sistema estrangula la red del
    // servicio y las alertas dejan de llegar o llegan con mucho retraso.
    // En fabricantes agresivos (Xiaomi/MIUI) además hay que activar el
    // "Inicio automático" y bloquear la app en recientes manualmente.
    private void pedirExencionBateria() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            // Algunos fabricantes no exponen esta pantalla; el usuario deberá
            // desactivar la optimización manualmente desde Ajustes.
        }
    }
}
