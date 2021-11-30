package com.multitv.ott.videocompressoranduploadsdk

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobile.config.AWSConfiguration
import com.amazonaws.mobileconnectors.s3.transferutility.*
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.activity_video_sdk_compress.*
import kotlinx.android.synthetic.main.video_upload_content.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.log10
import kotlin.math.pow


/**
 * Created by AbedElaziz Shehadeh on 26 Jan, 2020
 * elaziz.shehadeh@gmail.com
 */
class VideoCompressActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_SELECT_VIDEO = 0
        const val REQUEST_CAPTURE_VIDEO = 1
    }

    private lateinit var playableVideoPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TransferNetworkLossHandler.getInstance(this)
        setContentView(R.layout.activity_video_sdk_compress)

        setReadStoragePermission()

        pickVideo.setOnClickListener {
            pickVideo()
        }

        recordVideo.setOnClickListener {
            dispatchTakeVideoIntent()
        }

        cancel.setOnClickListener {
            VideoCompressor.cancel()
        }

        videoLayout.setOnClickListener { VideoPerviewPlayerActivity.start(this, playableVideoPath) }
    }

    //Pick a video file from device
    private fun pickVideo() {
        val intent = Intent()
        intent.apply {
            type = "video/*"
            action = Intent.ACTION_PICK
        }
        startActivityForResult(Intent.createChooser(intent, "Select video"), REQUEST_SELECT_VIDEO)
    }

    private fun dispatchTakeVideoIntent() {
        Intent(MediaStore.ACTION_VIDEO_CAPTURE).also { takeVideoIntent ->
            takeVideoIntent.resolveActivity(packageManager)?.also {
                startActivityForResult(takeVideoIntent, REQUEST_CAPTURE_VIDEO)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        mainContents.visibility = View.GONE
        timeTaken.text = ""
        newSize.text = ""

        if (resultCode == Activity.RESULT_OK)
            if (requestCode == REQUEST_SELECT_VIDEO || requestCode == REQUEST_CAPTURE_VIDEO) {
                handleResult(data)
            }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleResult(data: Intent?) {
        if (data != null && data.data != null) {
            val uri = data.data

            var originalFileSize = File(getMediaPath(applicationContext, uri!!)).length()
            val digitGroups = (log10(originalFileSize.toDouble()) / log10(1024.0)).toInt()
            val totalSize = originalFileSize / 1024.0.pow(digitGroups.toDouble())


            // Duration
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(this, uri)
            val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val millSecond = durationStr!!.toInt()
            val minutes = millSecond / 1000 / 60

            Log.e("File Duration:::", "Less :::" + minutes)


            var streamableFile: File? = null
            uri?.let {
                runOnUiThread {
                    mainContents.visibility = View.VISIBLE
                    Glide.with(applicationContext).load(uri).into(videoImage)
                }

                GlobalScope.launch {
                    // run in background as it can take a long time if the video is big,
                    // this implementation is not the best way to do it,
                    // todo(abed): improve threading
                    val job = async { getMediaPath(applicationContext, uri) }
                    val path = job.await()

                    val desFile = saveVideoFile(path)
                    streamableFile = saveVideoFile(path)

                    playableVideoPath = if (streamableFile != null) streamableFile!!.path
                    else path


                    if (totalSize < 20 || minutes < 2) {
                        processVideo(uri, desFile!!, streamableFile!!, path)
                        Log.e("File length:::", "Less :::" + totalSize)
                    } else {
                        Log.e("File length:::", "gratter:::" + totalSize)
                    }

                }
            }


            // uploadVideoOnAws(uri.path!!)


        }
    }


    private fun uploadVideoOnAws(filePath: String) {
        val credentials = BasicAWSCredentials(
                this.getString(R.string.s3_access_key),
                this.getString(R.string.s3_secret)
        )


        val name = filePath.substring(filePath.lastIndexOf("/") + 1)
        val removeSpaceFileName = name.replace(" ", "_")
        val fileName = removeSpaceFileName.replace("-", "_")

        //https://d1ik1ve0yltxwm.cloudfront.net /multitv/video/960/fileName
        val videoUrl = "https://d1ik1ve0yltxwm.cloudfront.net/multitv/video/960/$fileName"

        Log.e("Video Url :::", videoUrl)


        val s3Client = AmazonS3Client(credentials)
        var folderName = "multitv/video/960/$fileName"
        // backet name ott960

        val transferUtility = TransferUtility.builder()
                .context(this)
                .awsConfiguration(AWSConfiguration(this))
                .s3Client(s3Client)
                .build()
        val imageFile = File(filePath)


        if (imageFile.isAbsolute)
            Log.e("Image path ===", "" + filePath)
        else
            Log.e("Image path ===", "" + "Null")

        var uploadObserver: TransferObserver? = null

        uploadObserver = transferUtility.upload(
                "ott960", // The S3 bucket to upload to
                folderName, imageFile, CannedAccessControlList.PublicReadWrite
        )

        //var fileUrl = "https://d3tn0h9cityxqm.cloudfront.net/urbanstar/images/" + removeSpaceFileName

        uploadObserver?.setTransferListener(object : TransferListener {

            override fun onStateChanged(id: Int, state: TransferState) {
                if (TransferState.COMPLETED == state) {
                    Log.e("file uploaded======", "backetname:::::" + uploadObserver.bucket)
                    Log.e("file uploaded======", "path:::::" + uploadObserver.absoluteFilePath)
                    Log.e("file uploaded======", "key:::::" + uploadObserver.key)
                    Log.e("file uploaded======", "bytes:::::" + uploadObserver.bytesTotal)

                    Log.e("file uploaded======", "completed")
                    Toast.makeText(this@VideoCompressActivity, "Video Upload Completed!", Toast.LENGTH_SHORT).show()

                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                val percentDonef = bytesCurrent.toFloat() / bytesTotal.toFloat() * 100
                val percentDone = percentDonef.toInt()

                Log.e(
                        "YourActivity", "ID:" + id + " bytesCurrent: " + bytesCurrent
                        + " bytesTotal: " + bytesTotal + " " + percentDone + "%"
                )


            }

            override fun onError(id: Int, ex: Exception) {
                Log.e("video  uploaded======", "error===" + ex.message)
                Toast.makeText(this@VideoCompressActivity, "Video Upload Failed!", Toast.LENGTH_SHORT).show()
            }

        })

        if (TransferState.COMPLETED == uploadObserver?.state) {

            Log.e("file uploaded======", "backetname:::::" + uploadObserver.bucket)
            Log.e("file uploaded======", "path:::::" + uploadObserver.absoluteFilePath)
            Log.e("file uploaded======", "key:::::" + uploadObserver.key)
            Log.e("file uploaded======", "bytes:::::" + uploadObserver.bytesTotal)

            Log.e("file uploaded======", "completed")
            Toast.makeText(this@VideoCompressActivity, "Video Upload Successfully.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun setReadStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED
        ) {

            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
            ) {
                ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        1
                )
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun processVideo(uri: Uri?, desFile: File, streamableFile: File, path: String) {


        desFile?.let {
            var time = 0L
            VideoCompressor.start(
                    context = applicationContext,
                    srcUri = uri,
                    // srcPath = path,
                    destPath = desFile.path,
                    streamableFile = streamableFile?.path,
                    listener = object : CompressionListener {
                        override fun onProgress(percent: Float) {
                            //Update UI
                            if (percent <= 100 && percent.toInt() % 5 == 0)
                                runOnUiThread {
                                    progress.text = "${percent.toLong()}%"
                                    progressBar.progress = percent.toInt()
                                }
                        }

                        override fun onStart() {
                            time = System.currentTimeMillis()
                            progress.visibility = View.VISIBLE
                            progressBar.visibility = View.VISIBLE
                            originalSize.text =
                                    "Original size: ${getFileSize(File(path).length())}"
                            progress.text = ""
                            progressBar.progress = 0
                        }

                        override fun onSuccess() {
                            val newSizeValue =
                                    if (streamableFile != null) streamableFile!!.length()
                                    else desFile.length()

                            newSize.text =
                                    "Size after compression: ${getFileSize(newSizeValue)}"

                            time = System.currentTimeMillis() - time
                            timeTaken.text =
                                    "Duration: ${DateUtils.formatElapsedTime(time / 1000)}"

                            Looper.myLooper()?.let {
                                Handler(it).postDelayed({
                                    progress.visibility = View.GONE
                                    progressBar.visibility = View.GONE
                                }, 50)
                            }

                            var pathOfVideo = ""
                            if (streamableFile != null)
                                pathOfVideo = streamableFile.path
                            else
                                pathOfVideo = desFile.path

                            uploadVideoOnAws(pathOfVideo)
                        }

                        override fun onFailure(failureMessage: String) {
                            progress.text = failureMessage
                            Log.wtf("failureMessage", failureMessage)
                        }

                        override fun onCancelled() {
                            Log.wtf("TAG", "compression has been cancelled")
                            // make UI changes, cleanup, etc
                        }
                    },
                    configureWith = Configuration(
                            quality = VideoQuality.HIGH,
                            frameRate = 24,
                            isMinBitrateCheckEnabled = false,
                    )
            )
        }

    }

    @Suppress("DEPRECATION")
    private fun saveVideoFile(filePath: String?): File? {
        filePath?.let {
            val videoFile = File(filePath)
            val videoFileName = "${System.currentTimeMillis()}_${videoFile.name}"
            val folderName = Environment.DIRECTORY_MOVIES
            if (Build.VERSION.SDK_INT >= 30) {

                val values = ContentValues().apply {

                    put(
                            MediaStore.Images.Media.DISPLAY_NAME,
                            videoFileName
                    )
                    put(MediaStore.Images.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Images.Media.RELATIVE_PATH, folderName)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val collection =
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                val fileUri = applicationContext.contentResolver.insert(collection, values)

                fileUri?.let {
                    application.contentResolver.openFileDescriptor(fileUri, "rw")
                            .use { descriptor ->
                                descriptor?.let {
                                    FileOutputStream(descriptor.fileDescriptor).use { out ->
                                        FileInputStream(videoFile).use { inputStream ->
                                            val buf = ByteArray(4096)
                                            while (true) {
                                                val sz = inputStream.read(buf)
                                                if (sz <= 0) break
                                                out.write(buf, 0, sz)
                                            }
                                        }
                                    }
                                }
                            }

                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    applicationContext.contentResolver.update(fileUri, values, null, null)

                    return File(getMediaPath(applicationContext, fileUri))
                }
            } else {
                val downloadsPath =
                        applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val desFile = File(downloadsPath, videoFileName)

                if (desFile.exists())
                    desFile.delete()

                try {
                    desFile.createNewFile()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                return desFile
            }
        }
        return null
    }
}
