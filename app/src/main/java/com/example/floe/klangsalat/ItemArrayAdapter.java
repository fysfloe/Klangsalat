package com.example.floe.klangsalat;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ItemArrayAdapter extends ArrayAdapter<String[]> {

    private List<String[]> poiList = new ArrayList<String[]>();

    protected static final String TAG = "ItemArrayAdapter";


    static class ItemViewHolder {
        TextView poi;
        TextView lat;
        TextView lng;
    }

    public ItemArrayAdapter(Context context, int resource) {
        super(context, resource);
    }

    public void add(String[] object) {
        poiList.add(object);
        super.add(object);
    }

    @Override
    public int getCount() {
        return this.poiList.size();
    }

    @Override
    public String[] getItem(int position) {
        return this.poiList.get(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        ItemViewHolder viewHolder;
        if(row == null){
            LayoutInflater inflater = (LayoutInflater) this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            row = inflater.inflate(R.layout.single_list_item, parent, false);
            viewHolder = new ItemViewHolder();
            viewHolder.poi = (TextView) row.findViewById(R.id.poi);
            viewHolder.lat = (TextView) row.findViewById(R.id.lat);
            viewHolder.lng = (TextView) row.findViewById(R.id.lng);
            row.setTag(viewHolder);
        } else {
            viewHolder = (ItemViewHolder) row.getTag();
        }

        String[] poi = getItem(position);

        viewHolder.poi.setText(poi[0]);
        viewHolder.lat.setText(poi[1]);
        viewHolder.lng.setText(poi[2]);
        return row;
    }
}