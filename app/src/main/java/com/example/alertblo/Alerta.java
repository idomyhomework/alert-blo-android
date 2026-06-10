package com.example.alertblo;

import android.icu.text.SimpleDateFormat;
import android.icu.util.TimeZone;

import org.json.JSONException;
import org.json.JSONObject;
public class Alerta {
    public static final String PENDIENTE = "PENDIENTE";
    public static final String RECIBIDA  = "RECIBIDA";
    public static final String FALLIDA   = "FALLIDA";

    public int idAlerta;
    public String textoAlerta;
    public String fechaCreacion;
    public int silencio;
    public String estado;

    public Alerta(int id, String texto, String fecha, int silencio, String estado) {
        this.idAlerta = id;
        this.textoAlerta = texto;
        this.fechaCreacion = fecha;
        this.silencio = silencio;
        this.estado = estado;
    }

    public int getIdAlerta() {
        return idAlerta;
    }

    public int getSilencio() {
        return silencio;
    }

    public String getTextoAlerta(){
        return textoAlerta;
    }

    public String getFechaCreacion(){
        return fechaCreacion;
    }

    public boolean isSilent(){
        return this.getSilencio() == 1;
    }

    public String getEstado() { return estado; }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("ID_ALERTA", idAlerta);
        json.put("TEXT_ALERTA", textoAlerta);
        json.put("DATA_CREACIO", fechaCreacion);
        json.put("SILENCIO", silencio);
        json.put("ESTADO", estado);
        return json;
    }

    public static Alerta fromJson(JSONObject json) throws JSONException {
        return new Alerta(
                json.getInt("ID_ALERTA"),
                json.getString("TEXT_ALERTA"),
                json.getString("DATA_CREACIO"),
                json.getInt("SILENCIO"),
                json.optString("ESTADO", RECIBIDA)
        );
    }

    public String getDateFormatted() {
        try {
            // Parsear la hora del servidor (asumiendo que viene en formato "HH:mm")
            SimpleDateFormat sdfUTC = new SimpleDateFormat("HH:mm");
            sdfUTC.setTimeZone(TimeZone.getTimeZone("UTC"));

            // Convertir a hora local del dispositivo
            SimpleDateFormat sdfLocal = new SimpleDateFormat("HH:mm");
            sdfLocal.setTimeZone(TimeZone.getDefault());

            return sdfLocal.format(sdfUTC.parse(fechaCreacion));
        } catch (Exception e) {
            return fechaCreacion;  // Si hay error, devolver la hora original
        }
    }

}
