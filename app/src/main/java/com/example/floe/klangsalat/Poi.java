package com.example.floe.klangsalat;

import android.location.Location;
import android.support.annotation.NonNull;

public class Poi implements Comparable<Poi> {
    private Location location;
    private float id;
    private float distance;
    private float angle;

    public Poi(int id, float distance, float angle, Location location) {
        this.id = id;
        this.distance = distance;
        this.angle = angle;
        this.location = location;
    }

    public float getId() {
        return this.id;
    }

    public float getDistance() {
        return this.distance;
    }

    public float getAngle() {
        return this.angle;
    }

    public Location getLocation() {
        return this.location;
    }

    public double getLat() {
        return this.location.getLatitude();
    }

    public double getLng() {
        return this.location.getLongitude();
    }

    public void setAngle(float angle) {
        this.angle = angle;
    }

    public int compareTo(@NonNull Poi poi) {
        float sub = this.distance - poi.getDistance();
        if(sub < 0f) return -1;
        else if(sub == 0f) return 0;
        else return 1;
    }
}
