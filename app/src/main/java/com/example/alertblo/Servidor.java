package com.example.alertblo;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.*;

public class Servidor {

    // Consulta si hay una alerta nueva para este dispositivo.
    // Devuelve objeto Alerta o null si no hay ninguna.
    public static Alerta getAlerta(String idDispositivo) {
        try {
            String enc = URLEncoder.encode(idDispositivo, "UTF-8");
            HttpURLConnection conn = abrir(MainActivity.IP_SERVIDOR + "/get_alerta_json.php?id=" + enc, "GET");
            String resp = leer(conn);
            conn.disconnect();

            if (resp != null && !resp.isEmpty()) {
                try {
                    JSONObject json = new JSONObject(resp);
                    return Alerta.fromJson(json);
                } catch (JSONException e) {
                    return null;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // Abre la conexión con timeout de 5 s
    private static HttpURLConnection abrir(String url, String metodo) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(metodo);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        return conn;
    }

    // Lee la primera línea de la respuesta
    private static String leer(HttpURLConnection conn) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String resp = br.readLine();
        br.close();
        return resp;
    }
}
