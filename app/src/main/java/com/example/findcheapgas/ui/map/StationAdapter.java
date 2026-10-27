package com.example.findcheapgas.ui.map;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.util.FormatUtil;

import java.util.ArrayList;
import java.util.List;

/** 아래 시트의 주유소 목록. 순위는 목록 순서(1부터)이고, 가장 싼 곳에는 "최저가"를 붙인다. */
public class StationAdapter extends RecyclerView.Adapter<StationAdapter.ViewHolder> {

    public interface OnStationClickListener {
        void onStationClick(GasStation station);
    }

    private final OnStationClickListener clickListener;
    private final List<GasStation> stations = new ArrayList<>();
    private int cheapestPrice = Integer.MAX_VALUE;
    @Nullable
    private String selectedId;

    public StationAdapter(OnStationClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void submit(List<GasStation> newStations, int newCheapestPrice) {
        stations.clear();
        stations.addAll(newStations);
        cheapestPrice = newCheapestPrice;
        notifyDataSetChanged();
    }

    /** 선택이 바뀐 두 항목만 다시 그린다. */
    public void setSelectedId(@Nullable String id) {
        int oldPosition = positionOf(selectedId);
        selectedId = id;
        int newPosition = positionOf(id);
        if (oldPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(oldPosition);
        }
        if (newPosition != RecyclerView.NO_POSITION && newPosition != oldPosition) {
            notifyItemChanged(newPosition);
        }
    }

    public int positionOf(@Nullable String id) {
        if (id == null) {
            return RecyclerView.NO_POSITION;
        }
        for (int i = 0; i < stations.size(); i++) {
            if (stations.get(i).getId().equals(id)) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_station, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GasStation station = stations.get(position);
        holder.rank.setText(String.valueOf(position + 1));
        holder.name.setText(station.getName());
        holder.info.setText(holder.itemView.getContext().getString(R.string.station_info,
                station.getBrandName(), FormatUtil.formatDistance(station.getDistance())));
        holder.price.setText(FormatUtil.formatPrice(station.getPrice()));
        holder.cheapest.setVisibility(station.getPrice() == cheapestPrice ? View.VISIBLE : View.GONE);
        holder.itemView.setActivated(station.getId().equals(selectedId));
        holder.itemView.setOnClickListener(v -> clickListener.onStationClick(station));
    }

    @Override
    public int getItemCount() {
        return stations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView rank;
        final TextView name;
        final TextView info;
        final TextView price;
        final TextView cheapest;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            rank = itemView.findViewById(R.id.station_rank);
            name = itemView.findViewById(R.id.station_name);
            info = itemView.findViewById(R.id.station_info);
            price = itemView.findViewById(R.id.station_price);
            cheapest = itemView.findViewById(R.id.station_cheapest);
        }
    }
}
