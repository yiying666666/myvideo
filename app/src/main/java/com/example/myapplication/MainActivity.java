package com.example.myapplication;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.cover.CoverResult;
import com.example.myapplication.cover.CoverSelectionContract;


public class MainActivity extends AppCompatActivity {

    // 仅用于人工验证封面选择功能：选一个视频，再为它选一个封面，然后展示回传的结果。
    private final ActivityResultLauncher<Uri> coverSelectionLauncher =
            registerForActivityResult(CoverSelectionContract.INSTANCE, this::onCoverSelected);

    private final ActivityResultLauncher<PickVisualMediaRequest> pickVideoLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    coverSelectionLauncher.launch(uri);
                }
            });

    private TextView textCoverResult;
    private ImageView imageCoverResultDemo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        textCoverResult = findViewById(R.id.text_cover_result);
        imageCoverResultDemo = findViewById(R.id.image_cover_result_demo);

        Button pickVideoDemoButton = findViewById(R.id.btn_pick_video_demo);
        pickVideoDemoButton.setOnClickListener(v -> pickVideoLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE)
                        .build()));
    }

    private void onCoverSelected(CoverResult result) {
        if (result instanceof CoverResult.VideoFrame) {
            long timestampMs = ((CoverResult.VideoFrame) result).getTimestampUs() / 1000L;
            textCoverResult.setText(getString(R.string.cover_result_frame_format, timestampMs));
            imageCoverResultDemo.setImageDrawable(null);
        } else if (result instanceof CoverResult.StaticImage) {
            textCoverResult.setText(R.string.cover_result_image);
            imageCoverResultDemo.setImageURI(((CoverResult.StaticImage) result).getImageUri());
        } else {
            textCoverResult.setText(R.string.cover_result_none);
            imageCoverResultDemo.setImageDrawable(null);
        }
    }


}

