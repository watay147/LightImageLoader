package android.watay147.lightimageloader;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.watay147.lightimageloader.utils.LightImageLoader;
import android.widget.ImageView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageView imageView=(ImageView)findViewById(R.id.image);
        LightImageLoader.getInstance().loadImage(imageView,"http://img.hexun.com/2011-06-21/130726386.jpg");
    }
}
