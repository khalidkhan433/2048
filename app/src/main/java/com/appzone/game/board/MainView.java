package com.appzone.game.board;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.appzone.game.R;
import com.appzone.game.board.model.AnimationCell;
import com.appzone.game.board.model.MainGame;
import com.appzone.game.board.model.Tile;
import com.appzone.game.utils.Utils;

import java.util.ArrayList;

public class MainView extends View {

    //Internal Constants
    public static final int BASE_ANIMATION_TIME = 100000000;
    private static final String TAG = MainView.class.getSimpleName();

    private static final float MERGING_ACCELERATION = (float) -0.5;
    private static final float INITIAL_VELOCITY = (1 - MERGING_ACCELERATION) / 4;
    public final int numCellTypes = 21;
    private final BitmapDrawable[] bitmapCell = new BitmapDrawable[numCellTypes];
    public final MainGame game;
    //Internal variables
    private final Paint paint = new Paint();
    public boolean hasSaveState = false;
    public boolean continueButtonEnabled = false;
    public int startingX;
    public int startingY;
    public int endingX;
    public int endingY;
    //Misc
    public boolean refreshLastTime = true;
    //Timing
    private long lastFPSTime = System.nanoTime();
    //Text
    private float bodyTextSize;
    private float gameOverTextSize;
    //Layout variables
    public int cellSize = 0;
    private float textSize = 0;
    private float cellTextSize = 0;
    private int gridWidth = 0;
    private int textPaddingSize;
    //Assets
    private Drawable backgroundRectangle;
    private Drawable lightUpRectangle;
    private Drawable fadeRectangle;
    private Bitmap background = null;
    private BitmapDrawable loseGameOverlay;
    private BitmapDrawable winGameContinueOverlay;
    private BitmapDrawable winGameFinalOverlay;

    private OnScoreUpdate onScoreUpdate;

    public MainView(Context context) {
        super(context);
        game = new MainGame(context, this);
        init(context, null);
    }

    public MainView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        game = new MainGame(context, this);
        init(context, attrs);
    }

    public MainView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        game = new MainGame(context, this);
        init(context, attrs);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public MainView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        game = new MainGame(context, this);
        init(context, attrs);
    }

    String fontName;

    private void init(Context context, AttributeSet attrs) {

        try {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Game);
            backgroundRectangle = getDrawable(a, R.styleable.Game_item_background);
            lightUpRectangle = getDrawable(a, R.styleable.Game_item_lightup);
            fadeRectangle = getDrawable(a, R.styleable.Game_item_fade);
            fontName = a.getString(R.styleable.Game_font_name);
            a.recycle();
            Typeface font = Typeface.createFromAsset(getResources().getAssets(), fontName);
            paint.setTypeface(font);
            paint.setAntiAlias(true);
        } catch (Exception e) {
            Log.e(TAG, "Error getting assets?", e);
        }
        setOnTouchListener(new InputListener(this));
        game.newGame();
    }

    private Drawable getDrawable(TypedArray a, int ref) {
        Drawable d;
        d = a.getDrawable(ref);
        if (d == null)
            d = new ColorDrawable(a.getColor(ref, 0xFF00FF00));
        return d;
    }



    @Override
    public void onDraw(Canvas canvas) {
        canvas.drawBitmap(background, 0, 0, paint);
        if(getOnScoreUpdate()!=null)
            getOnScoreUpdate().onScoreUpdated();
        drawCells(canvas);
        if (!game.isActive())
            drawEndGameState(canvas);
        if (!game.canContinue()&&getOnScoreUpdate()!=null)
                getOnScoreUpdate().onScoreUpdated();
        if (game.aGrid.isAnimationActive()) {
            invalidate(startingX, startingY, endingX, endingY);
            tick();
        } else if (!game.isActive() && refreshLastTime) {
            invalidate();
            refreshLastTime = false;
        }
    }

    private void drawCells(Canvas canvas) {
        paint.setTextSize(textSize);
        paint.setTextAlign(Paint.Align.CENTER);
        // Outputting the individual cells
        for (int xx = 0; xx < game.numSquaresX; xx++)
        {
            for (int yy = 0; yy < game.numSquaresY; yy++)
            {
                int sX = startingX + gridWidth + (cellSize + gridWidth) * xx;
                int eX = sX + cellSize;
                int sY = startingY + gridWidth + (cellSize + gridWidth) * yy;
                int eY = sY + cellSize;

                Tile currentTile = game.grid.getCellContent(xx, yy);
                if (currentTile != null)
                {
                    //Get and represent the value of the tile
                    int value = currentTile.getValue();
                    int index = log2(value);

                    //Check for any active animations
                    ArrayList<AnimationCell> aArray = game.aGrid.getAnimationCell(xx, yy);
                    boolean animated = false;
                    for (int i = aArray.size() - 1; i >= 0; i--)
                    {
                        AnimationCell aCell = aArray.get(i);
                        //If this animation is not active, skip it
                        if (aCell.getAnimationType() == MainGame.SPAWN_ANIMATION) {
                            animated = true;
                        }
                        if (!aCell.isActive()) {
                            continue;
                        }

                        if (aCell.getAnimationType() == MainGame.SPAWN_ANIMATION) { // Spawning animation
                            double percentDone = aCell.getPercentageDone();
                            float textScaleSize = (float) (percentDone);
                            paint.setTextSize(textSize * textScaleSize);

                            float cellScaleSize = cellSize / 2 * (1 - textScaleSize);
                            bitmapCell[index].setBounds((int) (sX + cellScaleSize), (int) (sY + cellScaleSize), (int) (eX - cellScaleSize), (int) (eY - cellScaleSize));
                            bitmapCell[index].draw(canvas);
                        } else if (aCell.getAnimationType() == MainGame.MERGE_ANIMATION) { // Merging Animation
                            double percentDone = aCell.getPercentageDone();
                            float textScaleSize = (float) (1 + INITIAL_VELOCITY * percentDone
                                    + MERGING_ACCELERATION * percentDone * percentDone / 2);
                            paint.setTextSize(textSize * textScaleSize);

                            float cellScaleSize = cellSize / 2 * (1 - textScaleSize);
                            bitmapCell[index].setBounds((int) (sX + cellScaleSize), (int) (sY + cellScaleSize), (int) (eX - cellScaleSize), (int) (eY - cellScaleSize));
                            bitmapCell[index].draw(canvas);
                        } else if (aCell.getAnimationType() == MainGame.MOVE_ANIMATION) {  // Moving animation
                            double percentDone = aCell.getPercentageDone();
                            int tempIndex = index;
                            if (aArray.size() >= 2) {
                                tempIndex = tempIndex - 1;
                            }
                            int previousX = aCell.extras[0];
                            int previousY = aCell.extras[1];
                            int currentX = currentTile.getX();
                            int currentY = currentTile.getY();
                            int dX = (int) ((currentX - previousX) * (cellSize + gridWidth) * (percentDone - 1) * 1.0);
                            int dY = (int) ((currentY - previousY) * (cellSize + gridWidth) * (percentDone - 1) * 1.0);
                            bitmapCell[tempIndex].setBounds(sX + dX, sY + dY, eX + dX, eY + dY);
                            bitmapCell[tempIndex].draw(canvas);
                        }
                        animated = true;
                    }

                    //No active animations? Just draw the cell
                    if (!animated) {
                        bitmapCell[index].setBounds(sX, sY, eX, eY);
                        bitmapCell[index].draw(canvas);
                    }
                }
            }
        }
    }

    private void drawEndGameState(Canvas canvas) {
        double alphaChange = 1;
        continueButtonEnabled = false;
        for (AnimationCell animation : game.aGrid.globalAnimation) {
            if (animation.getAnimationType() == MainGame.FADE_GLOBAL_ANIMATION) {
                alphaChange = animation.getPercentageDone();
            }
        }
        BitmapDrawable displayOverlay = null;
        if (game.isGameWon())
        {
            if (game.canContinue()) {
                continueButtonEnabled = true;
                displayOverlay = winGameContinueOverlay;
            } else {
                displayOverlay = winGameFinalOverlay;
            }
        } else if (game.isGameLost())
            displayOverlay = loseGameOverlay;


        if (displayOverlay != null) {
            displayOverlay.setBounds(startingX, startingY, endingX, endingY);
            displayOverlay.setAlpha((int) (255 * alphaChange));
            displayOverlay.draw(canvas);
        }
    }


    @Override
    protected void onSizeChanged(int width, int height, int oldW, int oldH) {
        super.onSizeChanged(width, height, oldW, oldH);
        getLayout(width, height);
        createBitmapCells();
        createBackgroundBitmap(width, height);
        createOverlays();
    }

    private void drawCellText(Canvas canvas, int value) {
        int textShiftY = centerText();
//        if (value >= 8) {
//            paint.setColor(getResources().getColor(R.color.text_white));
//        } else
        {
            paint.setColor(getResources().getColor(R.color.text_white));
        }
        canvas.drawText("" + value, cellSize / 2, cellSize / 2 - textShiftY, paint);
    }

    private void drawBackground(Canvas canvas) {
        drawDrawable(canvas, backgroundRectangle, startingX, startingY, endingX, endingY);
    }
    private void drawBackgroundGrid(Canvas canvas) {
        Resources resources = getResources();
        Drawable backgroundCell = getDrawable(R.drawable.cell_rectangle);
        // Outputting the game grid
        for (int xx = 0; xx < game.numSquaresX; xx++) {
            for (int yy = 0; yy < game.numSquaresY; yy++) {
                int sX = startingX + gridWidth + (cellSize + gridWidth) * xx;
                int eX = sX + cellSize;
                int sY = startingY + gridWidth + (cellSize + gridWidth) * yy;
                int eY = sY + cellSize;

                drawDrawable(canvas, backgroundCell, sX, sY, eX, eY);
            }
        }
    }

    private static int log2(int n) {
        if (n <= 0) throw new IllegalArgumentException();
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    @SuppressWarnings("deprecation")
    private Drawable getDrawable(int resId) {
        return getResources().getDrawable(resId);
    }

    private void drawDrawable(Canvas canvas, Drawable draw, int startingX, int startingY, int endingX, int endingY) {
        draw.setBounds(startingX, startingY, endingX, endingY);
        draw.draw(canvas);
    }

    private void createEndGameStates(Canvas canvas, boolean win, boolean showButton) {
        int width = endingX - startingX;
        int length = endingY - startingY;
        int middleX = width / 2;
        int middleY = length / 2;
        if (win) {
            lightUpRectangle.setAlpha(127);
            drawDrawable(canvas, lightUpRectangle, 0, 0, width, length);
            lightUpRectangle.setAlpha(255);
            paint.setColor(getResources().getColor(R.color.text_white));
            paint.setAlpha(255);
            paint.setTextSize(gameOverTextSize);
            paint.setTextAlign(Paint.Align.CENTER);
            int textBottom = middleY - centerText();
            canvas.drawText(getResources().getString(R.string.you_win), middleX, textBottom, paint);
            paint.setTextSize(bodyTextSize);
            String text = showButton ? getResources().getString(R.string.go_on) :
                    getResources().getString(R.string.for_now);
            canvas.drawText(text, middleX, textBottom + textPaddingSize * 2 - centerText() * 2, paint);
        } else {
            fadeRectangle.setAlpha(127);
            drawDrawable(canvas, fadeRectangle, 0, 0, width, length);
            fadeRectangle.setAlpha(255);
            paint.setColor(getResources().getColor(R.color.text_black));
            paint.setAlpha(255);
            paint.setTextSize(gameOverTextSize);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(getResources().getString(R.string.game_over), middleX, middleY - centerText(), paint);
        }
    }

    private void createBackgroundBitmap(int width, int height) {
        background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(background);
        drawBackground(canvas);
        drawBackgroundGrid(canvas);

    }

    private void createBitmapCells() {
        Resources resources = getResources();
        paint.setTextAlign(Paint.Align.CENTER);
        float radii[] = new float[8];
        for (int i = 0; i < 8; i++)
            radii[i] = 10.0f;
        try {
            TypedArray ta = resources.obtainTypedArray(R.array.cell_colors);

            for (int xx = 1; xx < bitmapCell.length; xx++) {
                int value = (int) Math.pow(2, xx);
                paint.setTextSize(cellTextSize);
                float tempTextSize = cellTextSize * cellSize * 0.9f / Math.max(cellSize * 0.9f, paint.measureText(String.valueOf(value)));
                paint.setTextSize(tempTextSize);
                Bitmap bitmap = Bitmap.createBitmap(cellSize, cellSize, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                Drawable d = Utils.drawRoundCornerRectange(this.getContext(), cellSize, cellSize, radii, ta.getColor(xx, 0xff00ff));
                drawDrawable(canvas, d, 0, 0, cellSize, cellSize);
                drawCellText(canvas, value);
                bitmapCell[xx] = new BitmapDrawable(resources, bitmap);
            }
            ta.recycle();
        } catch (Exception e) {
        }
    }

    private void createOverlays() {
        Resources resources = getResources();
        //Initialize overlays
        Bitmap bitmap = Bitmap.createBitmap(endingX - startingX, endingY - startingY, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        createEndGameStates(canvas, true, true);
        winGameContinueOverlay = new BitmapDrawable(resources, bitmap);

        bitmap = Bitmap.createBitmap(endingX - startingX, endingY - startingY, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        createEndGameStates(canvas, true, false);
        winGameFinalOverlay = new BitmapDrawable(resources, bitmap);

        bitmap = Bitmap.createBitmap(endingX - startingX, endingY - startingY, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        createEndGameStates(canvas, false, false);
        loseGameOverlay = new BitmapDrawable(resources, bitmap);
    }

    private void tick() {
        long currentTime = System.nanoTime();
        game.aGrid.tickAll(currentTime - lastFPSTime);
        lastFPSTime = currentTime;
    }

    public void resyncTime() {
        lastFPSTime = System.nanoTime();
    }

    private void getLayout(int width, int height) {
        cellSize = Math.min(width / (game.numSquaresX +1), height / (game.numSquaresY +1));
        gridWidth = cellSize / (game.numSquaresX +1);

        int screenMiddleX = width / 2;
        int screenMiddleY = height / 2;

        //Grid Dimensions
        double halfNumSquaresX = game.numSquaresX / 2d;
        double halfNumSquaresY = game.numSquaresY / 2d;
        startingX = (int) (screenMiddleX - (cellSize + gridWidth) * halfNumSquaresX - gridWidth / 2);
        endingX = (int) (screenMiddleX + (cellSize + gridWidth) * halfNumSquaresX + gridWidth / 2);
        startingY = (int) (screenMiddleY - (cellSize + gridWidth) * halfNumSquaresY - gridWidth / 2);
        endingY = (int) (screenMiddleY + (cellSize + gridWidth) * halfNumSquaresY + gridWidth / 2);

        float widthWithPadding = endingX - startingX;

        // Text Dimensions
        paint.setTextSize(cellSize);
        textSize = cellSize * cellSize / Math.max(cellSize, paint.measureText("0000"));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(1000);
        gameOverTextSize = Math.min(
                Math.min(
                        1000f * ((widthWithPadding - gridWidth * 2) / (paint.measureText(getResources().getString(R.string.game_over)))),
                        textSize * 2
                ),
                1000f * ((widthWithPadding - gridWidth * 2) / (paint.measureText(getResources().getString(R.string.you_win))))
        );

        paint.setTextSize(cellSize);
        cellTextSize = textSize;
        bodyTextSize = (int) (textSize);
        textPaddingSize = (int) (textSize / 3);
        resyncTime();
    }

    private int centerText() {
        return (int) ((paint.descent() + paint.ascent()) / 2);
    }
    public OnScoreUpdate getOnScoreUpdate() {
        return onScoreUpdate;
    }

    public void setOnScoreUpdate(OnScoreUpdate onScoreUpdate) {
        this.onScoreUpdate = onScoreUpdate;
    }
    public interface OnScoreUpdate {
        void onScoreUpdated();
        void onEndLessMode();

    }
}