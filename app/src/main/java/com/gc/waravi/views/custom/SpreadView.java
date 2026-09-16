package com.gc.waravi.views.custom;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.gc.waravi.R;

import java.util.ArrayList;

/**
 *　波紋アニメーションでコールアイコンの背景
 */
public class SpreadView extends View {

    private final Paint centerPaint;
    private final Paint spreadPaint;
    private int radius = 30;
    private int maxRadius = 90;
    private int distance = 5;
    private int delayMilliseconds = 30;
    private final ArrayList<Integer> alphas = new ArrayList<>();
    private final ArrayList<Integer> spreadRadius = new ArrayList<>();

    public SpreadView(Context context) {
        this(context,null,0);
    }

    public SpreadView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs,0);
    }

    public SpreadView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SpreadView, defStyleAttr, 0);
        radius = a.getInt(R.styleable.SpreadView_spread_radius, radius);
        maxRadius = a.getInt(R.styleable.SpreadView_spread_max_radius, maxRadius);
        delayMilliseconds = a.getInt(R.styleable.SpreadView_spread_delay_milliseconds, delayMilliseconds);
        int centerColor = a.getColor(R.styleable.SpreadView_spread_center_color, ContextCompat.getColor(context, R.color.colorAccent));
        int spreadColor = a.getColor(R.styleable.SpreadView_spread_spread_color, ContextCompat.getColor(context, R.color.colorAccent));
        distance = a.getInt(R.styleable.SpreadView_spread_distance, distance);
        a.recycle();
        centerPaint = new Paint();
        centerPaint.setColor(centerColor);
        centerPaint.setAntiAlias(true);
        // Initially opaque with diffusion distance of 0
        alphas.add(255);
        spreadRadius.add(0);
        spreadPaint = new Paint();
        spreadPaint.setAntiAlias(true);
        spreadPaint.setAlpha(255);
        spreadPaint.setColor(spreadColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int centerX = getWidth()/2;
        int centerY = getHeight()/2;
        for (int i = 0; i < spreadRadius.size(); i++) {
            int alpha = alphas.get(i);
            spreadPaint.setAlpha(alpha);
            int width = spreadRadius.get(i);
            // Drawing a diffused circle
            canvas.drawCircle(centerX, centerY, radius + width, spreadPaint);
            // Every time the radius of diffusion circle increases, the circular transparency decreases.
            if (alpha > 0 && width < 300) {
                alpha = Math.max(alpha - distance, 0);
                alphas.set(i, alpha);
                spreadRadius.set(i, width + distance);
            }
        }
        // When the radius of the outermost diffusion circle reaches the maximum radius, a new diffusion circle is added.
        if (spreadRadius.get(spreadRadius.size() - 1) > maxRadius) {
            spreadRadius.add(0);
            alphas.add(255);
        }
        // Over eight diffusion circles, delete the first drawn circle, that is, the outermost circle
        if (spreadRadius.size() >= 6) {
            alphas.remove(0);
            spreadRadius.remove(0);
        }
        // The circle in the middle
        canvas.drawCircle(centerX, centerY, radius, centerPaint);
        // Delayed updating to achieve diffuse visual impairment
        postInvalidateDelayed(delayMilliseconds);
    }
}
