package net.neuralmetrics.projecthermes;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.constants.MyLocationTracking;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.services.Constants;
import com.mapbox.services.android.navigation.v5.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.NavigationConstants;
import com.mapbox.services.android.navigation.v5.NavigationService;
import com.mapbox.services.android.navigation.v5.RouteProgress;
import com.mapbox.services.android.navigation.v5.listeners.AlertLevelChangeListener;
import com.mapbox.services.android.navigation.v5.listeners.NavigationEventListener;
import com.mapbox.services.android.navigation.v5.listeners.OffRouteListener;
import com.mapbox.services.android.navigation.v5.listeners.ProgressChangeListener;
import com.mapbox.services.android.telemetry.location.LocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEnginePriority;
import com.mapbox.services.android.telemetry.permissions.PermissionsManager;
import com.mapbox.services.api.directions.v5.models.DirectionsResponse;
import com.mapbox.services.api.directions.v5.models.DirectionsRoute;
import com.mapbox.services.api.directions.v5.models.StepManeuver;
import com.mapbox.services.api.utils.turf.TurfConstants;
import com.mapbox.services.api.utils.turf.TurfMeasurement;
import com.mapbox.services.commons.geojson.LineString;
import com.mapbox.services.commons.models.Position;

import net.neuralmetrics.projecthermes.navigationservice.ServiceConstants;
import net.neuralmetrics.projecthermes.utils.ResourceUtils;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * to handle interaction events.
 * Use the {@link DriveFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class DriveFragment extends Fragment implements OnMapReadyCallback, NavigationEventListener, ProgressChangeListener, AlertLevelChangeListener, OffRouteListener {

    private static final String LOG_TAG = "DriveFragment";

    private MapView mapView;
    private LocationEngine locationEngine;
    private MapboxNavigation navigation;
    private MapboxMap mapboxMap;
    private Polyline routeLine;
    private Marker destinationMarker;
    private DirectionsRoute route;
    private Position destination;
    private TextView txtCurrentRoute;
    private TextView txtInstruction;
    private ImageView imgCue;

    public static double INVALID_LAT_LNG = -99999;

    // private OnFragmentInteractionListener mListener;

    public DriveFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment DriveFragment.
     */
    public static DriveFragment newInstance(double latitude, double longitude) {
        DriveFragment fragment = new DriveFragment();
        Bundle args = new Bundle();
        args.putDouble("latitude",latitude);
        args.putDouble("longitude",longitude);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this.getContext(), getActivity().getResources().getString(R.string.mapbox_access_token));
        getActivity().setTitle("Navigation");
        if (getArguments() != null) {
            double lat = getArguments().getDouble("latitude");
            double lon = getArguments().getDouble("longitude");
            this.destination = Position.fromCoordinates(lon,lat);
            if(lat==INVALID_LAT_LNG||lon==INVALID_LAT_LNG)
            {
                // TODO: not eligible to initiate the Drive Process
                Log.e(LOG_TAG, "Should not be eligible to start a driving process");
            }
        }
        navigation = new MapboxNavigation(getActivity(),getResources().getString(R.string.mapbox_access_token));
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_drive, container, false);
    }

    /*// TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }*/

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mapView=(MapView) getView().findViewById(R.id.map_box);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // Add the listeners to the map
        navigation.addNavigationEventListener(this);
        navigation.addProgressChangeListener(this);
        navigation.addAlertLevelChangeListener(this);

        txtCurrentRoute = (TextView) getView().findViewById(R.id.txtRoadTravelling);
        txtInstruction = (TextView) getView().findViewById(R.id.txtDirections);
        imgCue = (ImageView) getView().findViewById(R.id.imgVisualCue);
    }

    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;

        // Mock location engine
        locationEngine = new MockLocationEngine();

        // Attach the mock location engine to the MapboxMap
        // If not set, it will use the current location anyway
        this.mapboxMap.setLocationSource(locationEngine);

        // Location engine configuration
        locationEngine.setInterval(0);
        locationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
        locationEngine.setFastestInterval(1000);
        locationEngine.activate();

        // Enable location tracking
        if (PermissionsManager.areLocationPermissionsGranted(getActivity())) {
            mapboxMap.setMyLocationEnabled(true);
            mapboxMap.getTrackingSettings().setMyLocationTrackingMode(MyLocationTracking.TRACKING_FOLLOW);
            mapboxMap.getTrackingSettings().setDismissAllTrackingOnGesture(false);
        }

        // Calculate route
        calculateRoute();
    }

    // Route methods

    private void drawRouteLine(DirectionsRoute route) {
        List<Position> positions = LineString.fromPolyline(route.getGeometry(), Constants.PRECISION_6).getCoordinates();
        List<LatLng> latLngs = new ArrayList<>();
        for (Position position : positions) {
            latLngs.add(new LatLng(position.getLatitude(), position.getLongitude()));
        }

        // Remove old route if currently being shown on map.
        if (routeLine != null) {
            mapboxMap.removePolyline(routeLine);
        }

        routeLine = mapboxMap.addPolyline(new PolylineOptions()
                .addAll(latLngs)
                .color(Color.parseColor("#56b881"))
                .width(5f));
    }

    private void calculateRoute() {
        final AlertDialog loadingDialog = showLoadingDialog();
        Location userLocation = mapboxMap.getMyLocation();
        if (userLocation == null) {
            Timber.d("calculateRoute: User location is null, therefore, origin can't be set.");
            return;
        }

        Position origin = (Position.fromCoordinates(userLocation.getLongitude(), userLocation.getLatitude()));
        if (TurfMeasurement.distance(origin, destination, TurfConstants.UNIT_METERS) < 50) {
            mapboxMap.removeMarker(destinationMarker);
            // startRouteButton.setVisibility(View.GONE);
            return;
        }

        navigation.getRoute(origin, destination, new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                DirectionsRoute route = response.body().getRoutes().get(0);
                DriveFragment.this.route = route;
                drawRouteLine(route);
                closeLoadingDialog(loadingDialog);

                // Route is available, start fake navigation
                ((MockLocationEngine) locationEngine).setRoute(route);
                navigation.setLocationEngine(locationEngine);
                navigation.startNavigation(route);

                // TODO: Start the service
                Log.i(LOG_TAG, "Attempting to start navigation service");
                Intent startIntent = new Intent(getActivity().getApplicationContext(), net.neuralmetrics.projecthermes.navigationservice.NavigationService.class);
                startIntent.setAction(ServiceConstants.START_FOREGROUND);
                getActivity().getApplication().startService(startIntent);
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
                Timber.e("onFailure: navigation.getRoute()", throwable);
                closeLoadingDialog(loadingDialog);
            }
        });
    }

    // UI related functions

    private AlertDialog showLoadingDialog()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_loading, null);
        builder.setView(v);

        return builder.create();
    }

    private void closeLoadingDialog(AlertDialog a)
    {
        a.dismiss();
        a=null;
    }

    // Listeners

    /*
   * Navigation listeners
   */

    @Override
    public void onRunning(boolean running) {
        if (running) {
            Timber.d("onRunning: Started");
        } else {
            Timber.d("onRunning: Stopped");
        }
    }

    @Override
    public void onProgressChange(Location location, RouteProgress routeProgress) {
        Timber.d("onProgressChange: fraction of route traveled: %f", routeProgress.getFractionTraveled());
        // Log.i(LOG_TAG, routeProgress.getCurrentLegProgress().getCurrentStep().getName());
        txtCurrentRoute.setText(routeProgress.getCurrentLegProgress().getCurrentStep().getName());
        // Log.i(LOG_TAG, routeProgress.getCurrentLegProgress().getUpComingStep().getManeuver().getInstruction());
        double timeToStep = routeProgress.getCurrentLegProgress().getCurrentStepProgress().getDurationRemaining();
        StepManeuver m = routeProgress.getCurrentLegProgress().getUpComingStep().getManeuver();
        txtInstruction.setText((timeToStep<500?String.format("%.0f",timeToStep):"500+") + " | " + m.getInstruction().toUpperCase());
        String cueResourceString = "direction_"+m.getType()+"_"+m.getModifier();
        cueResourceString = cueResourceString.replace(" ","_");
        int cueResourceId = ResourceUtils.getResourceId(getActivity(),cueResourceString,"drawable",getActivity().getPackageName());
        imgCue.setImageResource(cueResourceId);
        // Log.i(LOG_TAG, String.valueOf(routeProgress.getCurrentLegProgress().getCurrentStepProgress().getDurationRemaining()));
    }

    @Override
    public void onAlertLevelChange(int alertLevel, RouteProgress routeProgress) {

        switch (alertLevel) {
            /*case NavigationConstants.HIGH_ALERT_LEVEL:
                Toast.makeText(MockNavigationActivity.this, "HIGH", Toast.LENGTH_LONG).show();
                Log.d("MANEUVER_ALERT","HIGH");
                Log.d("MANEUVER_INSTR",routeProgress.getCurrentLegProgress().getUpComingStep().getManeuver().getInstruction());
                break;
            case NavigationConstants.MEDIUM_ALERT_LEVEL:
                Toast.makeText(MockNavigationActivity.this, "MEDIUM", Toast.LENGTH_LONG).show();
                Log.d("MANEUVER_ALERT","MEDIUM");
                break;
            case NavigationConstants.LOW_ALERT_LEVEL:
                Toast.makeText(MockNavigationActivity.this, "LOW", Toast.LENGTH_LONG).show();
                Log.d("MANEUVER_ALERT","LOW");
                break;
            case NavigationConstants.ARRIVE_ALERT_LEVEL:
                Toast.makeText(MockNavigationActivity.this, "ARRIVE", Toast.LENGTH_LONG).show();
                break;
            case NavigationConstants.DEPART_ALERT_LEVEL:
                Toast.makeText(MockNavigationActivity.this, "DEPART", Toast.LENGTH_LONG).show();
                break;
            default:
            case NavigationConstants.NONE_ALERT_LEVEL:
                Toast.makeText(MockNavigationActivity.this, "NONE", Toast.LENGTH_LONG).show();
                break;*/
        }
    }

    @Override
    public void userOffRoute(Location location) {
        /*Position newOrigin = Position.fromCoordinates(location.getLongitude(), location.getLatitude());
        navigation.getRoute(newOrigin, destination, location.getBearing(), new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                DirectionsRoute route = response.body().getRoutes().get(0);
                MockNavigationActivity.this.route = route;

                // Remove old route line from map and draw the new one.
                if (routeLine != null) {
                    mapboxMap.removePolyline(routeLine);
                }
                drawRouteLine(route);
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
                Timber.e("onFailure: navigation.getRoute()", throwable);
            }
        });*/
    }

    // Life cycle methods

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        /*if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }*/
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // mListener = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();
        navigation.onStart();
        mapView.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        navigation.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();

        // Remove all navigation listeners
        navigation.removeAlertLevelChangeListener(this);
        navigation.removeNavigationEventListener(this);
        navigation.removeProgressChangeListener(this);
        navigation.removeOffRouteListener(this);

        // End the navigation session
        navigation.endNavigation();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }*/
}
