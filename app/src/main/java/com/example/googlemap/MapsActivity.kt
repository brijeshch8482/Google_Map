package com.example.googlemap

//.............................................................................................................................................................
// use your api to get directions...............................................................................................................................
//...............................................................................................................................................................

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request


class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var destinationLatitude: Double = 38.897957
    private var destinationLongitude: Double = -77.036560
    var fusedLocationProviderClient: FusedLocationProviderClient? = null
    var currentLocation : Location? = null
    val REQUEST_CODE = 101



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )


        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        fetchLocation()

    }


    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE)
            return
        }
        val task = fusedLocationProviderClient!!.lastLocation
        task.addOnSuccessListener { location ->
            if (location != null){

                // Fetching API_KEY which we wrapped
                val ai: ApplicationInfo = applicationContext.packageManager
                    .getApplicationInfo(applicationContext.packageName, PackageManager.GET_META_DATA)
                val value = ai.metaData["com.google.android.geo.API_KEY"]
                val apiKey = value.toString()
                // Initializing the Places API with the help of our API_KEY
                if (!Places.isInitialized()) {
                    Places.initialize(applicationContext, apiKey)
                }

                currentLocation = location
                val mapFragment = (supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?)
                mapFragment!!.getMapAsync(this@MainActivity)

                mapFragment.getMapAsync {
                    mMap = it
                    val originLocation = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
                    mMap.addMarker(MarkerOptions().position(originLocation))
                    val destinationLocation = LatLng(destinationLatitude, destinationLongitude)
                    mMap.addMarker(MarkerOptions().position(destinationLocation))
                    val urll = getDirectionURL(originLocation, destinationLocation, apiKey)
                    GetDirection(urll).execute()
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(originLocation, 10F))

                    val navigation =
                        Uri.parse("google.navigation:q=$destinationLatitude,$destinationLongitude")
                    val navigationIntent = Intent(Intent.ACTION_VIEW, navigation)
                    navigationIntent.setPackage("com.google.android.apps.maps")
                    startActivity(navigationIntent)
                }


            }
        }
    }


    override fun onMapReady(p0: GoogleMap) {
        mMap = p0
        val originLocation = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
        mMap.clear()
        mMap.addMarker(MarkerOptions().position(originLocation))
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(originLocation, 10f))
    }

    private fun getDirectionURL(origin:LatLng, dest:LatLng, secret: String) : String{

//        here you use your api for get directions

        return "https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude},${origin.longitude}" +
                "&destination=${dest.latitude},${dest.longitude}" +
                "&sensor=false" +
                "&mode=driving" +
                "&key=$secret"
    }


    @SuppressLint("StaticFieldLeak")
    private inner class GetDirection(val url : String) : AsyncTask<Void, Void, List<List<LatLng>>>(){
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: Void?): List<List<LatLng>> {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val data = response.body!!.string()

            val result =  ArrayList<List<LatLng>>()
            try{
                val respObj = Gson().fromJson(data,MapData::class.java)
                val path =  ArrayList<LatLng>()
                for (i in 0 until respObj.routes[0].legs[0].steps.size){
                    path.addAll(decodePolyline(respObj.routes[0].legs[0].steps[i].polyline.points))
                }
                result.add(path)
            }catch (e:Exception){
                e.printStackTrace()
            }
            return result
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(result: List<List<LatLng>>) {
            val lineoption = PolylineOptions()
            for (i in result.indices){
                lineoption.addAll(result[i])
                lineoption.width(10f)
                lineoption.color(Color.RED)
                lineoption.geodesic(true)
            }
            mMap.addPolyline(lineoption)
        }
    }

    fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            val latLng = LatLng((lat.toDouble() / 1E5),(lng.toDouble() / 1E5))
            poly.add(latLng)
        }
        return poly
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        when (requestCode){
            REQUEST_CODE -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    fetchLocation()
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
