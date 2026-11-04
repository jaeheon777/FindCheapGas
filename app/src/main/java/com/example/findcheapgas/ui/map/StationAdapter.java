package com.example.findcheapgas.ui.map;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.SavingLevel;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.util.FormatUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 아래 시트의 주유소 목록. 순위는 지금 정렬 기준의 순서(1부터)다.
 * 가격 아래에 예상 이득을 쓰고, 예상 이득 1위에는 "이득 1위"(기준 주유소가 1위면 "가까운 곳이 가장 유리"),
 * 표시 가격이 가장 싼 곳에는 "최저가" 배지를 붙인다. 두 배지는 한 곳에 함께 붙을 수 있다.
 * 선호 브랜드는 상호 앞에 ★를, 브랜드 할인이 적용된 곳은 "100원 할인 적용" 배지를 붙인다.
 */
public class StationAdapter extends RecyclerView.Adapter<StationAdapter.ViewHolder> {

    public interface OnStationClickListener {
        void onStationClick(StationSaving saving);
    }

    private final OnStationClickListener clickListener;
    private final List<StationSaving> stations = new ArrayList<>();
    private int cheapestPrice = Integer.MAX_VALUE;
    @Nullable
    private String selectedId;

    public StationAdapter(OnStationClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void submit(List<StationSaving> newStations, int newCheapestPrice) {
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
            if (stations.get(i).getStation().getId().equals(id)) {
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
        StationSaving saving = stations.get(position);
        GasStation station = saving.getStation();
        Context context = holder.itemView.getContext();
        holder.rank.setText(String.valueOf(position + 1));
        holder.name.setText(station.getName());
        // 선호 브랜드는 상호 앞에 ★ (색은 레이아웃의 drawableTint)
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(holder.name,
                saving.isPreferred() ? R.drawable.ic_star_small : 0, 0, 0, 0);
        holder.name.setContentDescription(saving.isPreferred()
                ? context.getString(R.string.station_preferred) + ", " + station.getName()
                : null);
        holder.info.setText(context.getString(R.string.station_info,
                station.getBrandName(), FormatUtil.formatDistance(station.getDistance())));
        holder.price.setText(FormatUtil.formatPrice(station.getPrice()));
        holder.saving.setText(FormatUtil.formatSaving(saving));
        holder.saving.setTextColor(savingTextColor(context, saving, holder.defaultSavingColor));

        boolean cheapest = station.getPrice() == cheapestPrice;
        if (saving.isBest()) {
            holder.best.setText(saving.isBaseline() ? R.string.station_best_baseline : R.string.station_best);
        }
        holder.best.setVisibility(saving.isBest() ? View.VISIBLE : View.GONE);
        holder.cheapest.setVisibility(cheapest ? View.VISIBLE : View.GONE);
        boolean discounted = saving.getDiscountRule().hasDiscount();
        if (discounted) {
            holder.discount.setText(context.getString(R.string.station_discount_applied,
                    FormatUtil.formatPrice(saving.getDiscountRule().getPerLiter())));
        }
        holder.discount.setVisibility(discounted ? View.VISIBLE : View.GONE);
        holder.badges.setVisibility(saving.isBest() || cheapest || discounted ? View.VISIBLE : View.GONE);

        holder.itemView.setActivated(station.getId().equals(selectedId));
        holder.itemView.setOnClickListener(v -> clickListener.onStationClick(saving));
    }

    /** 이득은 초록, 손해는 빨강, 비슷하거나 기준이면 보조 글자색 */
    private static int savingTextColor(Context context, StationSaving saving, int defaultColor) {
        if (saving.isBaseline()) {
            return defaultColor;
        }
        SavingLevel level = saving.getLevel();
        if (level == SavingLevel.GAIN) {
            return ContextCompat.getColor(context, R.color.saving_gain_text);
        }
        if (level == SavingLevel.LOSS) {
            return ContextCompat.getColor(context, R.color.saving_loss_text);
        }
        return defaultColor;
    }

    @Override
    public int getItemCount() {
        return stations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView rank;
        final TextView name;
        final TextView info;
        final View badges;
        final TextView best;
        final TextView cheapest;
        final TextView discount;
        final TextView price;
        final TextView saving;
        // 보조 글자색 (station_info와 같은 색)
        final int defaultSavingColor;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            rank = itemView.findViewById(R.id.station_rank);
            name = itemView.findViewById(R.id.station_name);
            info = itemView.findViewById(R.id.station_info);
            badges = itemView.findViewById(R.id.station_badges);
            best = itemView.findViewById(R.id.station_best);
            cheapest = itemView.findViewById(R.id.station_cheapest);
            discount = itemView.findViewById(R.id.station_discount);
            price = itemView.findViewById(R.id.station_price);
            saving = itemView.findViewById(R.id.station_saving);
            defaultSavingColor = info.getCurrentTextColor();
        }
    }
}
