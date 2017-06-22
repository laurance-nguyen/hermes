package net.neuralmetrics.projecthermes.mapfragment;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.mapbox.mapboxsdk.geometry.LatLng;

import net.neuralmetrics.projecthermes.utils.IDelegate;

/**
 * Created by hoang on 14/06/2017.
 */

public class ConfirmNavigationDialog extends DialogFragment {
    public LatLng latLng;
    public IDelegate delegateAffirmative;
    public IDelegate delegateNegative;

    public void setLatLng(LatLng mLatLng)
    {
        latLng=mLatLng;
    }

    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage("Do you want to switch to driving mode now?")
                .setTitle("Begin navigation")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.i("MapFragment","Navigation start!");
                        if(delegateAffirmative!=null) delegateAffirmative.exec();
                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if(delegateNegative!=null) delegateNegative.exec();
            }
        });
        return builder.create();
    }
}
