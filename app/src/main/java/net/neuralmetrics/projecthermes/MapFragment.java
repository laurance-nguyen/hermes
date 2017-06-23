package net.neuralmetrics.projecthermes;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PointF;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationSource;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.services.android.telemetry.location.LocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEngineListener;
import com.mapbox.services.android.telemetry.permissions.PermissionsListener;
import com.mapbox.services.android.telemetry.permissions.PermissionsManager;

import net.neuralmetrics.projecthermes.mapfragment.ConfirmNavigationDialog;
import net.neuralmetrics.projecthermes.mapfragment.OKDialog;
import net.neuralmetrics.projecthermes.navigationservice.ServiceConstants;
import net.neuralmetrics.projecthermes.utils.DisplayUnitConverter;
import net.neuralmetrics.projecthermes.utils.IDelegate;

import java.util.List;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.INPUT_METHOD_SERVICE;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MapFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MapFragment extends Fragment implements PermissionsListener {
    private static final int PLACE_AUTOCOMPLETE_REQUEST_CODE = 11195;
    private static final String TAG = "MapFragment";
    private MapView mapView;
    private MapboxMap map;
    private LocationEngine locationEngine;
    private PermissionsManager permissionsManager;
    private FloatingActionButton locateMeFab;
    private Button pickLocationButton;
    private ConstraintLayout mapFragmentParentLayout;
    private EditText searchBox;
    private LatLng locationReference;

    private boolean searchLocationActivityLaunched = false; // Prevent launching the search activity multiple times
    private final static String LOG_TAG = "MapFragment";

    public MapFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment MapFragment.
     */
    public static MapFragment newInstance() {
        MapFragment fragment = new MapFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle("Maps");
        // Activate Mapbox layout UI
        Mapbox.getInstance(this.getContext(), getActivity().getResources().getString(R.string.mapbox_access_token));
        // New location engine
        locationEngine = LocationSource.getLocationEngine(getActivity());
        locationEngine.activate();
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mapFragmentParentLayout=(ConstraintLayout) getView().findViewById(R.id.map_fragment_parent_layout);
        mapView = (MapView) getView().findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                map = mapboxMap;
                toggleGps(true);
            }
        });

        // Locate Me Fab listener connect
        locateMeFab = (FloatingActionButton) getView().findViewById(R.id.locate_me_fab);
        locateMeFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (map != null) {
                    // toggleGps(!map.isMyLocationEnabled());
                    toggleGps(true);
                }
            }
        });

        // Pick location listener connect
        pickLocationButton=(Button) getView().findViewById(R.id.pick_location_button);
        pickLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manualPickLocation();
            }
        });

        // Setup the location searchbox autocomplete widget
        searchBox = (EditText) getView().findViewById(R.id.search_box);
        searchBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(searchLocationActivityLaunched) return;
                searchLocationActivityLaunched = true;
                try {
                    Intent intent =
                            new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_FULLSCREEN)
                                    .build(getActivity());
                    startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE);
                } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
                    // TODO: Handle the error.
                    Log.w(TAG, "No Google Play Services are found!");
                    OKDialog playServicesErrorDialog = new OKDialog();
                    playServicesErrorDialog.initialise("We have trouble with Google Play Services. Please consider update Google Play Services to the latest version.", "Play Services Error");
                    playServicesErrorDialog.show(getActivity().getSupportFragmentManager(),"PLAY_SERVICES_DIALOG");
                }
            }
        });
    }

    // Handle the Google Place API callback from the autocomplete (search for location box)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode==PLACE_AUTOCOMPLETE_REQUEST_CODE)
        {
            if (resultCode==RESULT_OK)
            {
                Place place = PlaceAutocomplete.getPlace(getActivity(), data);
                searchBox.setText(place.getName());
                final com.google.android.gms.maps.model.LatLng placeCoordinates = place.getLatLng();
                createMarkerAndPanMap(placeCoordinates.latitude,placeCoordinates.longitude);
                //TODO: Show the select location button and go
                final Button selectLocationButton = createSelectLocationButton(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        locationPicked(new LatLng(placeCoordinates.latitude,placeCoordinates.longitude));
                    }
                });
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(getActivity(), data);
                Log.i(TAG, status.getStatusMessage());
            }
        }
        searchLocationActivityLaunched=false;
    }

    private void hideOnScreenKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(INPUT_METHOD_SERVICE);
            if (getActivity().getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    // GPS location Functions for Maps

    // Check for position permission and then call enableLocation to move the map to user's current location
    private void toggleGps(boolean enableGps) {
        if (enableGps) {
            // Check if user has granted location permission
            permissionsManager = new PermissionsManager(this);
            if (!PermissionsManager.areLocationPermissionsGranted(this.getActivity())) {
                permissionsManager.requestLocationPermissions(this.getActivity());
            } else {
                enableLocation(true);
            }
        } else {
            enableLocation(false);
        }
    }

    // Get GPS location and move the map to user's current location
    private void enableLocation(boolean enabled) {
        if (enabled) {
            // If we have the last location of the user, we can move the camera to that position.
            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            Location lastLocation = locationEngine.getLastLocation();
            if (lastLocation != null) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastLocation), 16));
                locationReference=new LatLng(lastLocation);
            }

            LocationEngineListener locationEngineListener = new LocationEngineListener() {
                @Override
                public void onConnected() {
                    // No action needed here.
                }

                @Override
                public void onLocationChanged(Location location) {
                    if (location != null) {
                        // Move the map camera to where the user location is and then remove the
                        // listener so the camera isn't constantly updating when the user location
                        // changes. When the user disables and then enables the location again, this
                        // listener is registered again and will adjust the camera once again.
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location), 16));
                        locationReference=new LatLng(location);
                        locationEngine.removeLocationEngineListener(this);
                    }
                }
            };
            locationEngine.addLocationEngineListener(locationEngineListener);
            // locateMeFab.setImageResource(R.drawable.ic_location_disabled_24dp);
        } else {
            locateMeFab.setImageResource(R.drawable.ic_my_location_black_24dp);
        }
        // Enable or disable the location layer on the map
        map.setMyLocationEnabled(enabled);
    }

    // For the autocompletebox: when user taps on a location, display a marker and move the map to that position
    private void createMarkerAndPanMap(double latitude, double longitude) {
        // Build marker
        map.clear();
        map.addMarker(new MarkerOptions()
                .position(new LatLng(latitude, longitude)));

        // Animate camera to geocoder result location
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(new LatLng(latitude, longitude))
                .zoom(15)
                .build();
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 2000, null);
    }

    // Some Android permissions dialogs handling
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(getActivity(), "This app needs location permissions in order to show its functionality.",
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            enableLocation(true);
        } else {
            Toast.makeText(getActivity(), "You didn't grant location permissions.",
                    Toast.LENGTH_LONG).show();
            getActivity().finish();
        }
    }

    // Position selection modes

    // Display the manual location picking screen
    private void manualPickLocation() {
        // Create drop pin using custom image
        // Create and display the "Select location" button on screen
        // Tapping on the pick location button again will toggle off the locating picking mode
        final ImageView dropPinView = new ImageView(getActivity());
        pickLocationButton = (Button) getView().findViewById(R.id.pick_location_button);
        dropPinView.setImageResource(R.drawable.ic_my_location_black_24dp);
        final Button selectLocationButton = createSelectLocationButton(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manualLocationPicked(dropPinView, (Button) v, pickLocationButton);
            }
        });
        pickLocationButton.setOnClickListener(null);
        pickLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cleanManualPickLocation(dropPinView, selectLocationButton, (Button) v);
            }
        });
        // Statically Set drop pin in center of screen
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        float density = getResources().getDisplayMetrics().density;
        params.bottomMargin = (int) (12 * density);
        dropPinView.setLayoutParams(params);
        mapView.addView(dropPinView);
    }

    // Create the "Select Location" button to allow user to switch to driving mode!
    private Button createSelectLocationButton(View.OnClickListener listener)
    {
        // Remove all the "Pick Location" button, if presents
        if(mapFragmentParentLayout.findViewById(R.id.select_location_button)!=null)
        {
            View v = mapFragmentParentLayout.findViewById(R.id.select_location_button);
            ((ViewGroup)v.getParent()).removeView(v);
        }
        final Button selectLocationButton = new Button(getActivity(), null);
        selectLocationButton.setText("CHOOSE LOCATION");
        selectLocationButton.setId(R.id.select_location_button);
        selectLocationButton.setBackgroundResource(R.drawable.button_background_ripple);
        selectLocationButton.setTextColor(Color.WHITE);
        mapFragmentParentLayout.addView(selectLocationButton, mapFragmentParentLayout.getChildCount());

        ConstraintSet selectLocationButtonConstraints = new ConstraintSet();
        selectLocationButtonConstraints.clone(mapFragmentParentLayout);

        selectLocationButtonConstraints.constrainWidth(selectLocationButton.getId(), ConstraintSet.WRAP_CONTENT);
        selectLocationButton.setPadding(DisplayUnitConverter.convertDpToPx(16f, getActivity()), 0, DisplayUnitConverter.convertDpToPx(16f, getActivity()), 0);
        selectLocationButtonConstraints.constrainHeight(selectLocationButton.getId(), ConstraintSet.WRAP_CONTENT);

        selectLocationButtonConstraints.connect(selectLocationButton.getId(), ConstraintSet.LEFT, R.id.cardView, ConstraintSet.LEFT, 8);
        selectLocationButtonConstraints.connect(selectLocationButton.getId(), ConstraintSet.BOTTOM, R.id.mapView, ConstraintSet.BOTTOM, 44);
        selectLocationButtonConstraints.connect(selectLocationButton.getId(), ConstraintSet.RIGHT, R.id.cardView, ConstraintSet.RIGHT, 8);

        selectLocationButtonConstraints.applyTo(mapFragmentParentLayout);

        selectLocationButton.setOnClickListener(listener);
        return selectLocationButton;
    }

    // Clear the location pick mode (clear the pin/select location button, reset the listener for the pick location button)
    private void cleanManualPickLocation(ImageView dropPinView, Button selectLocationButton, Button pickLocationButton)
    {
        ((ViewGroup) dropPinView.getParent()).removeView(dropPinView);
        selectLocationButton.setOnClickListener(null);
        ((ViewGroup) selectLocationButton.getParent()).removeView(selectLocationButton);
        pickLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manualPickLocation();
            }
        });
        cleanSearchPickLocation();
    }

    // Clear the location pick mode if user search and then canceled the navigation dialog
    private void cleanSearchPickLocation()
    {
        if(mapFragmentParentLayout.findViewById(R.id.select_location_button)!=null)
        {
            View v = mapFragmentParentLayout.findViewById(R.id.select_location_button);
            ((ViewGroup)v.getParent()).removeView(v);
        }
        map.clear();
    }

    // Acquire coordinates and prompt a dialog to start driving
    private void manualLocationPicked(ImageView dropPinView, Button selectLocationButton, Button pickLocationButton)
    {
        cleanManualPickLocation(dropPinView, selectLocationButton, pickLocationButton);
        LatLng position = map.getProjection().fromScreenLocation(new PointF(dropPinView.getLeft() + (dropPinView.getWidth() / 2), dropPinView.getBottom()));
        locationPicked(position);
    }

    // Prompt confirm dialog to switch to driving mode
    private void locationPicked(final LatLng position)
    {
        ConfirmNavigationDialog confirmNavigationDialog = new ConfirmNavigationDialog();
        confirmNavigationDialog.setLatLng(position);
        confirmNavigationDialog.delegateAffirmative = new IDelegate() {
            @Override
            public void exec() {
                cleanSearchPickLocation();
                ((HomeActivity) getActivity()).requestFragmentDrive(position.getLatitude(), position.getLongitude());
            }
        };
        confirmNavigationDialog.delegateNegative=new IDelegate() {
            @Override
            public void exec() {
                cleanSearchPickLocation();
            }
        };
        confirmNavigationDialog.show(getChildFragmentManager(), "CONFIRM_NAVIGATION_DIALOG");
    }

    // Life cycle methods

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    // Mapbox Life Cycle Events

    @Override
    public void onStart() {
        super.onStart();
        getActivity().setTitle("Maps");
        mapView.onStart();
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
    public void onStop() {
        super.onStop();
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
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    /*public interface OnRequestDrivingListener
    {
        void requestFragmentDrive(double lat, double lon);
    }*/
}
