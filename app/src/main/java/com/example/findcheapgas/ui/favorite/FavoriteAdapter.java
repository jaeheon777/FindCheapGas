package com.example.findcheapgas.ui.favorite;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.FavoriteItem;
import com.example.findcheapgas.model.FavoritePrice;
import com.example.findcheapgas.model.FavoriteStation;
import com.example.findcheapgas.ui.SavingColors;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.util.FavoritePriceResolver;
import com.example.findcheapgas.util.FormatUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 즐겨찾기 탭 목록. 최근에 저장한 순서 그대로 보여준다.
 * 가격 칸: 지금 고른 유종 가격. 가져오는 중, 그 유종을 팔지 않음, 찾을 수 없음, 한도 도달이면 가격 대신 안내를 쓴다.
 * 이득 칸: 예상 이득. 조회 위치에서 5km 밖이면 계산하지 않는다는 안내를 아래 줄에 쓴다.
 */
public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(FavoriteItem item);

        void onMenuClick(View anchor, FavoriteItem item);

        void onDeleteClick(FavoriteItem item);
    }

    private final Listener listener;
    private final List<FavoriteItem> items = new ArrayList<>();
    // 거리 앞에 붙는 조회 위치 이름 (예: 내 위치, 강남역)
    private String originLabel = "";
    private long now = System.currentTimeMillis();

    public FavoriteAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<FavoriteItem> newItems, String newOriginLabel) {
        items.clear();
        items.addAll(newItems);
        originLabel = newOriginLabel;
        now = System.currentTimeMillis();
        notifyDataSetChanged();
    }

    @Nullable
    public FavoriteItem getItem(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_favorite, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FavoriteItem item = items.get(position);
        FavoriteStation favorite = item.getFavorite();
        Context context = holder.itemView.getContext();

        holder.name.setText(favorite.getName());
        Double distance = item.getDistance();
        holder.info.setText(distance != null
                ? context.getString(R.string.favorite_info, favorite.getBrandName(), originLabel,
                        FormatUtil.formatDistance(distance))
                : favorite.getBrandName());
        String address = favorite.getAddress();
        holder.address.setText(address != null ? address : context.getString(R.string.favorite_address_unknown));

        bindPrice(holder, item, context);
        bindSaving(holder, item, context);

        FavoritePrice price = item.getPrice();
        boolean notFound = price != null && price.getStatus() == FavoritePrice.Status.NOT_FOUND;
        holder.delete.setVisibility(notFound ? View.VISIBLE : View.GONE);
        holder.delete.setOnClickListener(v -> listener.onDeleteClick(item));
        holder.menu.setContentDescription(context.getString(R.string.favorite_menu_description, favorite.getName()));
        holder.menu.setOnClickListener(v -> listener.onMenuClick(v, item));
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    /** 가격, 또는 가격이 없는 이유. 30분이 지난 캐시 가격이면 "약 2시간 전 가격"을 붙인다. */
    private void bindPrice(ViewHolder holder, FavoriteItem item, Context context) {
        FavoritePrice price = item.getPrice();
        holder.stale.setVisibility(View.GONE);
        boolean available = price != null && price.isAvailable();
        // 가격이 없으면 작은 보조 글자로 쓴다.
        holder.price.setTextSize(TypedValue.COMPLEX_UNIT_PX, available ? holder.priceTextSize : holder.noteTextSize);
        holder.price.setTypeface(null, available ? Typeface.BOLD : Typeface.NORMAL);
        holder.price.setTextColor(available ? holder.priceColor : holder.defaultColor);
        if (price == null) {
            holder.price.setText(R.string.favorite_price_loading);
            return;
        }
        if (!available) {
            holder.price.setText(R.string.detail_price_unknown);
            return;
        }
        holder.price.setText(FormatUtil.formatPrice(price.getPrice()));
        String age = staleAge(context, price);
        if (age != null) {
            holder.stale.setText(context.getString(R.string.favorite_stale, age));
            holder.stale.setVisibility(View.VISIBLE);
        }
    }

    /** 예상 이득 칸과, 가격·이득이 없는 이유를 쓰는 아래 줄 */
    private void bindSaving(ViewHolder holder, FavoriteItem item, Context context) {
        holder.saving.setText(null);
        holder.saving.setVisibility(View.GONE);
        holder.saving.setTextColor(holder.defaultColor);
        String status = null;

        FavoritePrice price = item.getPrice();
        if (price != null && !price.isAvailable()) {
            status = priceProblem(context, price);
        } else if (item.getSavingStatus() == FavoriteItem.SavingStatus.CALCULATED && item.getSaving() != null) {
            StationSaving saving = item.getSaving();
            holder.saving.setText(FormatUtil.formatSaving(saving));
            holder.saving.setTextColor(savingColor(context, saving, holder.defaultColor));
            holder.saving.setVisibility(View.VISIBLE);
        } else if (item.getSavingStatus() == FavoriteItem.SavingStatus.TOO_FAR) {
            status = context.getString(R.string.favorite_too_far, originLabel);
        } else if (item.getSavingStatus() == FavoriteItem.SavingStatus.NO_BASELINE && price != null) {
            status = context.getString(R.string.favorite_no_baseline);
        } else if (item.getSavingStatus() == FavoriteItem.SavingStatus.NO_ORIGIN) {
            status = context.getString(R.string.favorite_no_origin);
        }
        holder.status.setText(status);
        holder.status.setVisibility(status != null ? View.VISIBLE : View.GONE);
    }

    private static String priceProblem(Context context, FavoritePrice price) {
        switch (price.getStatus()) {
            case NOT_SOLD:
                return context.getString(R.string.favorite_not_sold, price.getFuelType().getLabel());
            case NOT_FOUND:
                return context.getString(R.string.favorite_not_found);
            case DAILY_LIMIT:
                return context.getString(R.string.favorite_daily_limit);
            case NETWORK:
                return context.getString(R.string.favorite_network_error);
            default:
                return context.getString(R.string.favorite_opinet_error);
        }
    }

    /** 30분이 지난 캐시면 "40분", "2시간". 아니면 null */
    @Nullable
    private String staleAge(Context context, FavoritePrice price) {
        if (price.getSource() != FavoritePrice.Source.CACHE) {
            return null;
        }
        long age = now - price.getFetchedAt();
        if (age < FavoritePriceResolver.FRESH_CACHE_MS) {
            return null;
        }
        long minutes = age / 60_000;
        if (minutes < 60) {
            return context.getString(R.string.favorite_stale_minutes, (int) minutes);
        }
        return context.getString(R.string.favorite_stale_hours, (int) (minutes / 60));
    }

    /** 이득은 초록, 비슷은 회색, 손해는 빨강, 기준이면 보조 글자색 (지도 탭 목록과 같다) */
    private static int savingColor(Context context, StationSaving saving, int defaultColor) {
        if (saving.isBaseline()) {
            return defaultColor;
        }
        return ContextCompat.getColor(context, SavingColors.textColorOf(saving.getLevel()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView info;
        final TextView address;
        final TextView status;
        final View delete;
        final TextView price;
        final TextView saving;
        final TextView stale;
        final ImageButton menu;
        final int defaultColor;
        final int priceColor;
        final float priceTextSize;
        final float noteTextSize;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.favorite_name);
            info = itemView.findViewById(R.id.favorite_info);
            address = itemView.findViewById(R.id.favorite_address);
            status = itemView.findViewById(R.id.favorite_status);
            delete = itemView.findViewById(R.id.favorite_delete);
            price = itemView.findViewById(R.id.favorite_price);
            saving = itemView.findViewById(R.id.favorite_saving);
            stale = itemView.findViewById(R.id.favorite_stale);
            menu = itemView.findViewById(R.id.favorite_menu);
            defaultColor = info.getCurrentTextColor();
            priceColor = price.getCurrentTextColor();
            priceTextSize = price.getTextSize();
            noteTextSize = info.getTextSize();
        }
    }
}
