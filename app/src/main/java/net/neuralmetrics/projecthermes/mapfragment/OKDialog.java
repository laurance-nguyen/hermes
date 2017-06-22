package net.neuralmetrics.projecthermes.mapfragment;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.mapbox.mapboxsdk.geometry.LatLng;

/**
 * Created by hoang on 14/06/2017.
 */

public class OKDialog extends DialogFragment {

    private String message, title;

    public void initialise(String mMessage, String mTitle)
    {
        title=mTitle;
        message=mMessage;
    }

    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(message)
                .setTitle(title)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
        return builder.create();
    }
}
