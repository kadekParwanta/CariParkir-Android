package com.dwipa.cariparkir;

import com.google.android.gms.maps.model.LatLng;
import com.strongloop.android.loopback.Model;

/**
 * Created by Kadek_P on 6/22/2016.
 */
public class Parking extends Model{

    private String name;
    private String type;
    private int available;
    private int total;
    private String rate;
    private LatLng geo;
    private String _id;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public String getRate() {
        return rate;
    }

    public void setRate(String rate) {
        this.rate = rate;
    }

    public LatLng getGeo() {
        return geo;
    }

    public void setGeo(LatLng geo) {
        this.geo = geo;
    }

    public String getId() {
        return _id;
    }

    public void setId(String id) {
        this._id = id;
    }
}
