package com.rapidresq.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.rapidresq.app.ui.navigation.RapidResQNavGraph
import com.rapidresq.app.ui.theme.RapidResQTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
            
        Coil.setImageLoader(imageLoader)

        setContent {
            RapidResQTheme {
                RapidResQNavGraph()
            }
        }
    }
}
