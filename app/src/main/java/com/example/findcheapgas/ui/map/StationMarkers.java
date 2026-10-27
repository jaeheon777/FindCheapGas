package com.example.findcheapgas.ui.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.util.FormatUtil;
import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.label.CompetitionType;
import com.kakao.vectormap.label.Label;
import com.kakao.vectormap.label.LabelLayer;
import com.kakao.vectormap.label.LabelLayerOptions;
import com.kakao.vectormap.label.LabelManager;
import com.kakao.vectormap.label.LabelOptions;
import com.kakao.vectormap.label.LabelStyle;
import com.kakao.vectormap.label.LabelStyles;
import com.kakao.vectormap.label.OrderingType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 지도 위 주유소 가격 마커 (예: "1,659원" 말풍선).
 * 가장 싼 주유소는 초록색, 선택한 주유소는 파란 테두리로 그린다.
 * Label의 tag에 주유소 ID를 넣어 두고, 눌렀을 때 그 ID로 목록과 연결한다.
 */
class StationMarkers {

    private static final String LAYER_ID = "stations";
    // 겹치는 마커는 싼 곳, 선택한 곳이 위에 오도록 한다.
    private static final long RANK_NORMAL = 1_000;
    private static final long RANK_CHEAPEST = 2_000;
    private static final long RANK_SELECTED = 3_000;

    private final Context context;
    private final LabelManager labelManager;
    private final LabelLayer layer;

    // 같은 가격·모양의 스타일은 다시 만들지 않는다.
    private final Map<String, LabelStyles> styleCache = new HashMap<>();
    private final Map<String, Label> labelsById = new HashMap<>();
    private final Map<String, GasStation> stationsById = new HashMap<>();
    private int cheapestPrice = Integer.MAX_VALUE;
    @Nullable
    private String selectedId;

    /** 지도에서 LabelManager를 얻지 못하면 null을 돌려준다. */
    @Nullable
    static StationMarkers create(Context context, KakaoMap map) {
        LabelManager labelManager = map.getLabelManager();
        if (labelManager == null) {
            return null;
        }
        // 기본 레이어는 겹치는 라벨을 숨기므로, 모든 가격이 보이도록 경쟁 없는 레이어를 따로 만든다.
        // 내 위치 점(기본 레이어)보다 아래에 그린다.
        LabelLayer layer = labelManager.addLayer(LabelLayerOptions.from(LAYER_ID)
                .setCompetitionType(CompetitionType.None)
                .setOrderingType(OrderingType.Rank)
                .setZOrder(LabelManager.DEFAULT_Z_ORDER - 1));
        if (layer == null) {
            return null;
        }
        return new StationMarkers(context, labelManager, layer);
    }

    private StationMarkers(Context context, LabelManager labelManager, LabelLayer layer) {
        this.context = context;
        this.labelManager = labelManager;
        this.layer = layer;
    }

    /** 이전 마커를 모두 지우고 새로 그린다. */
    void show(List<GasStation> stations, int cheapest, @Nullable String selected) {
        layer.removeAll();
        labelsById.clear();
        stationsById.clear();
        cheapestPrice = cheapest;
        selectedId = selected;

        for (GasStation station : stations) {
            boolean isSelected = station.getId().equals(selectedId);
            Label label = layer.addLabel(LabelOptions.from(LatLng.from(station.getLatitude(), station.getLongitude()))
                    .setStyles(stylesFor(station, isSelected))
                    .setRank(rankFor(station, isSelected))
                    .setClickable(true)
                    .setTag(station.getId()));
            if (label != null) {
                labelsById.put(station.getId(), label);
                stationsById.put(station.getId(), station);
            }
        }
    }

    void setSelected(@Nullable String id) {
        if (id != null ? id.equals(selectedId) : selectedId == null) {
            return;
        }
        String oldId = selectedId;
        selectedId = id;
        refresh(oldId);
        refresh(id);
    }

    /** 이 레이어의 주유소 마커면 주유소 ID, 아니면 null */
    @Nullable
    String stationIdOf(LabelLayer clickedLayer, Label label) {
        if (clickedLayer != layer || !(label.getTag() instanceof String)) {
            return null;
        }
        return (String) label.getTag();
    }

    private void refresh(@Nullable String id) {
        if (id == null) {
            return;
        }
        Label label = labelsById.get(id);
        GasStation station = stationsById.get(id);
        if (label == null || station == null) {
            return;
        }
        boolean isSelected = id.equals(selectedId);
        label.changeStyles(stylesFor(station, isSelected));
        label.changeRank(rankFor(station, isSelected));
    }

    private long rankFor(GasStation station, boolean selected) {
        if (selected) {
            return RANK_SELECTED;
        }
        return station.getPrice() == cheapestPrice ? RANK_CHEAPEST : RANK_NORMAL;
    }

    private LabelStyles stylesFor(GasStation station, boolean selected) {
        String text = FormatUtil.formatPrice(station.getPrice());
        boolean cheapest = station.getPrice() == cheapestPrice;
        String key = text + "|" + cheapest + "|" + selected;
        LabelStyles styles = styleCache.get(key);
        if (styles == null) {
            styles = labelManager.addLabelStyles(LabelStyles.from(
                    LabelStyle.from(createBitmap(text, cheapest, selected))
                            .setAnchorPoint(0.5f, 1f)     // 말풍선 꼬리 끝이 주유소 위치에 오도록
                            .setApplyDpScale(false)));    // 비트맵을 이미 dp 기준 크기로 그렸다.
            styleCache.put(key, styles);
        }
        return styles;
    }

    /** 아래쪽에 꼬리가 달린 둥근 말풍선에 가격을 쓴다. */
    private Bitmap createBitmap(String text, boolean cheapest, boolean selected) {
        float density = context.getResources().getDisplayMetrics().density;
        float paddingH = 9 * density;
        float paddingV = 5 * density;
        float tailHeight = 6 * density;
        float tailHalfWidth = 5 * density;
        float border = (selected ? 2.5f : 1f) * density;

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(13 * density);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(color(cheapest ? R.color.marker_cheapest_text : R.color.marker_text));
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float textHeight = metrics.descent - metrics.ascent;

        float bodyWidth = textPaint.measureText(text) + paddingH * 2;
        float bodyHeight = textHeight + paddingV * 2;
        int width = (int) Math.ceil(bodyWidth + border * 2);
        int height = (int) Math.ceil(bodyHeight + tailHeight + border * 2);

        // 테두리가 잘리지 않도록 border/2만큼 안쪽에 그린다.
        float inset = border / 2;
        RectF body = new RectF(inset, inset, width - inset, inset + bodyHeight + border);
        float centerX = width / 2f;
        Path shape = new Path();
        float radius = bodyHeight / 2;
        shape.addRoundRect(body, radius, radius, Path.Direction.CW);
        Path tail = new Path();
        tail.moveTo(centerX - tailHalfWidth, body.bottom - border);
        tail.lineTo(centerX, height - inset);
        tail.lineTo(centerX + tailHalfWidth, body.bottom - border);
        tail.close();
        shape.op(tail, Path.Op.UNION);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color(cheapest ? R.color.marker_cheapest_background : R.color.marker_background));
        canvas.drawPath(shape, fill);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(border);
        stroke.setColor(color(selected ? R.color.marker_selected_border
                : cheapest ? R.color.marker_cheapest_background : R.color.marker_border));
        canvas.drawPath(shape, stroke);

        float baseline = body.top + border / 2 + paddingV - metrics.ascent;
        canvas.drawText(text, centerX - textPaint.measureText(text) / 2, baseline, textPaint);
        return bitmap;
    }

    private int color(int resId) {
        return ContextCompat.getColor(context, resId);
    }
}
