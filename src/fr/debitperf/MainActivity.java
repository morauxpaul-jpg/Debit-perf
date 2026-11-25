package fr.debitperf;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private DripView view;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // L'écran reste allumé pendant le réglage de la perfusion.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new DripView(this);
        setContentView(view);
        view.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (!view.back()) super.onBackPressed();
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        // Lunette / couronne : transmise même si la vue a perdu le focus.
        if (view.onRotary(ev)) return true;
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    protected void onPause() {
        view.save();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        view.release();
        super.onDestroy();
    }
}
