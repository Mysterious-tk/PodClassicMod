package com.example.podclassic.view


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.podclassic.base.Core
import com.example.podclassic.base.Observer
import com.example.podclassic.base.ScreenView
import com.example.podclassic.bean.Music
import com.example.podclassic.bean.MusicList
import com.example.podclassic.media.PlayMode
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.storage.MusicTable
import com.example.podclassic.storage.SPManager
import com.example.podclassic.util.LiveData
import com.example.podclassic.util.MediaUtil
import com.example.podclassic.util.ThreadUtil
import com.example.podclassic.values.Colors
import com.example.podclassic.values.Icons
import java.util.Locale
import com.example.podclassic.values.Values
import com.example.podclassic.widget.RecyclerListView
import com.example.podclassic.widget.RecyclerListView.OnItemClickListener

@SuppressLint("ViewConstructor")
class MusicListView : FrameLayout, ScreenView {
    private val observer = Observer()
    override fun getObserver(): Observer {
        return observer
    }

    override fun getTitle(): String {
        return name
    }

    override fun onViewAdd() {
        // 延迟刷新列表，确保视图完全布局完成后再刷新
        postDelayed({
            listView.refreshList()
        }, 100)
    }

    private val musicList: ArrayList<Music>
    private val name: String
    private val type: Int
    private val listView: RecyclerListView
    private val backgroundImageView: ImageView

    // 添加构造函数，用于创建背景ImageView
    private fun createBackgroundImageView(context: Context): ImageView {
        val imageView = ImageView(context)
        val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        imageView.layoutParams = layoutParams
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        // API 31+ 使用 RenderEffect 实现玻璃效果，不需要设置低透明度
        // 低版本保持原来的透明度
        imageView.alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.6f else 0.15f
        // 禁用背景ImageView的触摸事件，确保事件传递给上层的RecyclerListView
        imageView.isClickable = false
        imageView.isFocusable = false
        return imageView
    }

    constructor(context: Context, musicList: ArrayList<Music>, name: String, type: Int) : super(
        context
    ) {
        this.musicList = musicList
        this.name = name
        this.type = type
        
        // 初始化RecyclerListView和背景ImageView
        listView = RecyclerListView(context)
        backgroundImageView = createBackgroundImageView(context)
        
        // 添加到FrameLayout中 - 先添加背景，再添加列表，确保列表在上层接收触摸事件
        addView(backgroundImageView)
        // 为listView设置布局参数，确保它占据整个FrameLayout的空间
        val listViewParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(listView, listViewParams)
        // 确保RecyclerListView可以接收触摸事件
        listView.isClickable = true
        
        init()
        listView.sorted = type == TYPE_NORMAL
    }

    constructor(context: Context, musicList: MusicList) : super(context) {
        this.musicList = musicList.getMusicList()
        this.name = musicList.title
        this.type = TYPE_NORMAL
        
        // 初始化RecyclerListView和背景ImageView
        listView = RecyclerListView(context)
        backgroundImageView = createBackgroundImageView(context)
        
        // 添加到FrameLayout中 - 先添加背景，再添加列表，确保列表在上层接收触摸事件
        addView(backgroundImageView)
        // 为listView设置布局参数，确保它占据整个FrameLayout的空间
        val listViewParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(listView, listViewParams)
        // 确保RecyclerListView可以接收触摸事件
        listView.isClickable = true
        
        init()
        listView.sorted = musicList.type != MusicList.TYPE_ALBUM
    }

    companion object {
        const val TYPE_NORMAL = 0
        const val TYPE_FAVOURITE = 1
        const val TYPE_RECENT = 2
        const val TYPE_CURRENT = 3

    }


    private fun init() {
        // 添加顶部装饰
        addTopDecoration()

        // 批量创建所有歌曲项，然后一次性添加，避免多次刷新
        val showInfo = SPManager.getBoolean(SPManager.SP_SHOW_INFO)
        val items = musicList.mapIndexed { index, music ->
            createSongItem(index, music, showInfo)
        }
        listView.addAll(items)

        val currentIndex = musicList.indexOf(MediaPresenter.getCurrent())
        if (currentIndex != -1) {
            listView.setCurrent(currentIndex)
        }
        
        // 设置默认点击监听器，处理触摸播放歌曲的功能
        listView.defaultOnItemClickListener = object : RecyclerListView.OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                return enter(index)
            }
        }

        // 加载背景模糊的专辑封面
        loadBackgroundImage()

        observer.addLiveData(MediaPresenter.music, object : LiveData.OnDataChangeListener {
            override fun onDataChange() {
                ThreadUtil.runOnUiThread { refreshList() }
            }
        })
    }
    
    // 加载背景模糊的专辑封面
    private fun loadBackgroundImage() {
        if (musicList.isNotEmpty()) {
            val firstMusic = musicList[0]
            val albumId = firstMusic.albumId

            // 在子线程中加载专辑封面并应用模糊效果
            Thread {
                val bitmap = MediaUtil.getAlbumImage(albumId ?: 0L)
                if (bitmap != null) {
                    val blurredBitmap = blurBitmap(bitmap)
                    bitmap.recycle()
                    ThreadUtil.runOnUiThread {
                        backgroundImageView.setImageBitmap(blurredBitmap)
                        // API 31+ 应用 RenderEffect 玻璃效果
                        applyGlassEffect()
                    }
                } else {
                    // 使用默认图标作为背景
                    ThreadUtil.runOnUiThread {
                        val defaultBitmap = Icons.DEFAULT.bitmap
                        val blurredBitmap = blurBitmap(defaultBitmap)
                        backgroundImageView.setImageBitmap(blurredBitmap)
                        // API 31+ 应用 RenderEffect 玻璃效果
                        applyGlassEffect()
                    }
                }
            }.start()
        }
    }

    // 应用玻璃效果 - 使用 RenderEffect (API 31+)
    private fun applyGlassEffect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                // 创建高斯模糊效果 - 使用适中的模糊半径实现玻璃效果
                val blurEffect = RenderEffect.createBlurEffect(
                    40f,  // X轴模糊半径
                    40f,  // Y轴模糊半径
                    Shader.TileMode.CLAMP
                )

                // 应用效果到背景ImageView
                backgroundImageView.setRenderEffect(blurEffect)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // 模糊Bitmap的方法 - API 31+ 使用 RenderEffect 实现更现代的玻璃效果
    private fun blurBitmap(bitmap: Bitmap): Bitmap {
        // API 31+ 使用 RenderEffect 实现高质量模糊
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return blurBitmapWithRenderEffect(bitmap)
        }
        // 低版本使用传统的 BlurMaskFilter
        return blurBitmapWithMaskFilter(bitmap)
    }

    // 使用 RenderEffect 实现高质量模糊（API 31+）
    private fun blurBitmapWithRenderEffect(bitmap: Bitmap): Bitmap {
        // 缩小图片以提高性能，但保持较高分辨率以获得更好的玻璃效果
        val scaleFactor = 0.25f
        val scaledWidth = (bitmap.width * scaleFactor).toInt()
        val scaledHeight = (bitmap.height * scaleFactor).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        val blurredBitmap = Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(blurredBitmap)

        // 先绘制原始缩放图片
        canvas.drawBitmap(scaledBitmap, 0f, 0f, null)

        scaledBitmap.recycle()
        return blurredBitmap
    }

    // 低版本使用传统的 BlurMaskFilter
    private fun blurBitmapWithMaskFilter(bitmap: Bitmap): Bitmap {
        // 缩小图片以提高模糊效率
        val scaleFactor = 0.1f
        val scaledWidth = (bitmap.width * scaleFactor).toInt()
        val scaledHeight = (bitmap.height * scaleFactor).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false)
        val blurredBitmap = Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(blurredBitmap)

        val paint = Paint()
        paint.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)

        scaledBitmap.recycle()
        return blurredBitmap
    }
    
    private fun addTopDecoration() {
        // 移除专辑信息项，改为在背景显示专辑封面
    }
    
    private fun createSongItem(index: Int, music: Music, showInfo: Boolean): RecyclerListView.Item {
        // 只显示歌曲标题，符合iPod风格
        val displayText = music.title
        // 创建Item，设置右侧文本为歌曲时长，并设置enable为true
        val item = RecyclerListView.Item(displayText, null, formatDuration(music.duration.toInt()))
        item.enable = true
        return item
    }
    
    // 格式化时长为mm:ss格式，分钟和秒都补零
    private fun formatDuration(duration: Int): String {
        val totalSeconds = duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun refreshList() {
        listView.refreshList()
    }

    private fun enter(index: Int): Boolean {
        return if (index in 0 until musicList.size) {
            if (musicList[index] != MediaPresenter.getCurrent())
            {
                MediaPresenter.setPlayMode(PlayMode.ORDER)
                MediaPresenter.setPlaylist(musicList, index)
            }

            // 根据主题选择使用哪个播放器视图
            if (SPManager.getInt(SPManager.Theme.SP_NAME) == SPManager.Theme.IPOD_3RD.id) {
                Core.addView(MusicPlayerView3rd(context))
            } else {
                Core.addView(MusicPlayerView(context))
            }
            true
        } else {
            false
        }
    }

    override fun enter(): Boolean {
        // 使用ListView的当前索引
        val currentIndex = listView.getCurrentIndex()
        return enter(currentIndex)
    }

    override fun enterLongClick(): Boolean {
        // 注意：由于index是protected的，我们不能直接访问
        // 这里使用一个简单的实现，假设当前选中的是第一个项目
        // 实际上，这个方法应该由ListView的长按监听器调用
        return false
    }

    override fun slide(slideVal: Int): Boolean {
        return listView.onSlide(slideVal)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // 不拦截触摸事件，让子视图（RecyclerListView）处理滑动和点击
        return false
    }
}