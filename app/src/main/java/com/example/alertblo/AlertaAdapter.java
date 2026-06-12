package com.example.alertblo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

public class AlertaAdapter extends ArrayAdapter<Alerta> {

    public AlertaAdapter(Context context, List<Alerta> alertas) {
        super(context, 0, alertas);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_alerta, parent, false);
        }

        Alerta alerta = getItem(position);

        TextView textoPrincipal = convertView.findViewById(R.id.textoPrincipal);
        TextView textoSubitem   = convertView.findViewById(R.id.textoSubitem);
        TextView textoEstado    = convertView.findViewById(R.id.textoEstado);

        if (alerta != null) {
            textoPrincipal.setText(alerta.getTextoAlerta());
            textoSubitem.setText(alerta.getFechaCreacion());

            // El ciudadano solo recibe alertas: siempre se muestran como "Recibida".
            textoEstado.setText(R.string.estado_recibida);
            textoEstado.setBackgroundColor(0xFF81C784);
        }

        return convertView;
    }
}