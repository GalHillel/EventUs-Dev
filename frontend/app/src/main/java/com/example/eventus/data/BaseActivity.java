package com.example.eventus.data;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eventus.data.model.LoggedInUser;

import java.util.Set;
import java.util.stream.Collectors;

public abstract class BaseActivity extends AppCompatActivity {
    protected ImageButton backButton;
    protected Intent intent;
    protected Bundle args;
    protected LoggedInUser user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() == null) {
            Intent res = new Intent();
            res.putExtra("error", "null intent given");
            setResult(Activity.RESULT_CANCELED, res);
            this.finish();
            return;
        }
        intent = getIntent();

        if (intent.getExtras() == null) {
            Intent res = new Intent();
            res.putExtra("error", "intent missing bundle");
            setResult(Activity.RESULT_CANCELED, res);
            this.finish();
            return;
        }

        args = intent.getExtras();
        Set<String> required = getRequiredArgs();
        Set<String> missingArgs = required.stream().filter(e -> !args.containsKey(e)).collect(Collectors.toSet());
        if (!missingArgs.isEmpty()) {
            Intent res = new Intent();
            res.putExtra("error", "Missing " + missingArgs + " fields");
            setResult(Activity.RESULT_CANCELED, res);
            this.finish();
        }


    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (backButton != null)
            backButtonClick(backButton);
    }

    public abstract void backButtonClick(View view);

    public void success() {
        Bundle arg = new Bundle();
        if (this.user != null) {
            arg.putSerializable("user", user);
        }
        Intent i = new Intent();
        i.putExtras(arg);
        setResult(Activity.RESULT_OK, i);
        finish();
    }

    public abstract Set<String> getRequiredArgs();
}
