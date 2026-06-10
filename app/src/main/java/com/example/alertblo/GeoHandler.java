package com.example.alertblo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.util.function.Consumer;


public class GeoHandler {
    static final double ZONA_LAT    = 40.4186;
    static final double ZONA_LNG    =  0.4291;
    static final float  ZONA_RADIO_M = 15_000f;

    @SuppressLint("MissingPermission")
    public static void obtenerUbicacion(Context context, Consumer<Location> callback) {
        if (!tienePermiso(context)) {
            callback.accept(null);
            return;
        }

        FusedLocationProviderClient cliente =
                LocationServices.getFusedLocationProviderClient(context);

        // Intento 1: última ubicación cacheada (instantáneo, sin batería extra)
        cliente.getLastLocation()
                .addOnSuccessListener(loc -> {
                    if (loc != null) {
                        callback.accept(loc);
                    } else {
                        // Intento 2: solicitud fresca si el caché estaba vacío
                        CancellationTokenSource cts = new CancellationTokenSource();
                        CurrentLocationRequest peticion = new CurrentLocationRequest.Builder()
                                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                                .setDurationMillis(2000)
                                .setMaxUpdateAgeMillis(10_000)
                                .build();
                        cliente.getCurrentLocation(peticion, cts.getToken())
                                .addOnSuccessListener(callback::accept)
                                .addOnFailureListener(e -> callback.accept(null));
                    }
                })
                .addOnFailureListener(e -> callback.accept(null));
    }

    public static boolean estaDentroDeZona(Location loc) {
        if (loc == null) {
            return false;
        }
        float[] distancia = new float[1];
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(),
                ZONA_LAT, ZONA_LNG, distancia);
        return distancia[0] <= ZONA_RADIO_M;
    }

    private static boolean tienePermiso(Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

}
